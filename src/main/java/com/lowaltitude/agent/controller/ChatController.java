package com.lowaltitude.agent.controller;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * 聊天控制器 - 支持流式输出
 * 正确处理 Spring AI Alibaba 的 StreamingOutput 类型
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final ReactAgent supervisorAgent;

    /**
     * 普通聊天接口（非流式）
     */
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        log.info("Received chat request: {}", request.getMessage());
        
        try {
            Optional<OverAllState> result = supervisorAgent.invoke(request.getMessage());
            
            if (result.isPresent()) {
                OverAllState state = result.get();
                String responseText = extractResponseFromState(state);
                return ChatResponse.success(responseText);
            } else {
                return ChatResponse.error("处理失败，无返回结果");
            }
        } catch (Exception e) {
            log.error("Chat processing error", e);
            return ChatResponse.error("处理异常: " + e.getMessage());
        }
    }

    /**
     * 流式聊天接口（SSE）
     * 正确处理 StreamingOutput 类型和 OutputType
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        log.info("Received stream chat request: {}", request.getMessage());
        
        return Flux.create(sink -> {
            CompletableFuture.runAsync(() -> {
                try {
                    // 发送开始事件
                    sink.next(formatSseEvent("thinking", "正在分析您的请求..."));
                    
                    supervisorAgent.stream(request.getMessage())
                        .subscribe(
                            output -> processOutput(output, sink),
                            error -> {
                                log.error("Stream error", error);
                                sink.next(formatSseEvent("error", error.getMessage()));
                                sink.next(formatSseEvent("done", ""));
                                sink.complete();
                            },
                            () -> {
                                sink.next(formatSseEvent("done", ""));
                                sink.complete();
                            }
                        );
                } catch (Exception e) {
                    log.error("Stream setup error", e);
                    sink.next(formatSseEvent("error", e.getMessage()));
                    sink.next(formatSseEvent("done", ""));
                    sink.complete();
                }
            });
        });
    }
    
    /**
     * 处理流式输出 - 根据 OutputType 区分不同类型
     */
    private void processOutput(NodeOutput output, reactor.core.publisher.FluxSink<String> sink) {
        // 检查是否为 StreamingOutput 类型
        if (output instanceof StreamingOutput streamingOutput) {
            OutputType type = streamingOutput.getOutputType();
            
            // 处理模型推理的流式增量内容
            if (type == OutputType.AGENT_MODEL_STREAMING) {
                if (streamingOutput.message() instanceof AssistantMessage msg) {
                    String text = msg.getText();
                    if (text != null && !text.isEmpty()) {
                        sink.next(formatSseEvent("content", text));
                    }
                    
                    // 检查是否有 reasoningContent (Thinking 内容)
                    Object reasoning = msg.getMetadata().get("reasoningContent");
                    if (reasoning != null && !reasoning.toString().isEmpty()) {
                        sink.next(formatSseEvent("thinking_detail", reasoning.toString()));
                    }
                }
            }
            // 模型推理完成
            else if (type == OutputType.AGENT_MODEL_FINISHED) {
                if (streamingOutput.message() instanceof AssistantMessage msg) {
                    // 检查是否有工具调用
                    if (msg.hasToolCalls()) {
                        msg.getToolCalls().forEach(toolCall -> {
                            sink.next(formatSseEvent("tool_call", 
                                toolCall.name() + "|" + toolCall.arguments()));
                        });
                    }
                }
            }
            // 工具调用完成
            else if (type == OutputType.AGENT_TOOL_FINISHED) {
                if (streamingOutput.message() instanceof ToolResponseMessage toolResponse) {
                    toolResponse.getResponses().forEach(response -> {
                        sink.next(formatSseEvent("tool_result", 
                            response.name() + "|" + response.responseData()));
                    });
                }
                sink.next(formatSseEvent("tool", "工具执行完成"));
            }
            // Hook 执行完成
            else if (type == OutputType.AGENT_HOOK_FINISHED) {
                log.debug("Hook finished: {}", output.node());
            }
        }
        // 普通节点输出 - 检测 Agent 调用
        else {
            String nodeName = output.node();
            if (nodeName != null && !nodeName.isEmpty()) {
                // 检测 Agent 名称
                if (nodeName.contains("chat_agent")) {
                    sink.next(formatSseEvent("agent", "chat_agent|处理闲聊问候"));
                } else if (nodeName.contains("data_query_agent")) {
                    sink.next(formatSseEvent("agent", "data_query_agent|执行数据查询"));
                } else if (nodeName.contains("article_agent")) {
                    sink.next(formatSseEvent("agent", "article_agent|检索文章资讯"));
                } else if (nodeName.contains("policy_agent")) {
                    sink.next(formatSseEvent("agent", "policy_agent|查询政策法规"));
                } else if (!nodeName.equals("__END__") && !nodeName.equals("supervisor")) {
                    sink.next(formatSseEvent("step", nodeName));
                }
            }
        }
    }
    
    /**
     * 格式化 SSE 事件
     */
    private String formatSseEvent(String event, String data) {
        return "event: " + event + "\ndata: " + data + "\n\n";
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "service", "low-altitude-economy-agent",
                "version", "1.0.0"
        );
    }

    private String extractResponseFromState(OverAllState state) {
        return state.value("messages")
                .map(messages -> {
                    if (messages instanceof java.util.List) {
                        java.util.List<?> list = (java.util.List<?>) messages;
                        if (!list.isEmpty()) {
                            Object last = list.get(list.size() - 1);
                            if (last instanceof AssistantMessage msg) {
                                return msg.getText();
                            }
                            return last.toString();
                        }
                    }
                    return "无响应";
                })
                .orElse("无响应");
    }
}
