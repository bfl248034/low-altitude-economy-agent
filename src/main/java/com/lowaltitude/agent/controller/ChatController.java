package com.lowaltitude.agent.controller;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.lowaltitude.agent.stream.StreamContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.Optional;

/**
 * 聊天控制器
 * 提供 REST API 接口供前端调用
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
                System.out.println(state.toString());
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
     * 流式聊天接口（SSE）- 新版
     * 支持输出工具调用、智能体调用流程
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        log.info("Received stream chat request: {}", request.getMessage());
        
        Sinks.Many<StreamEvent> sink = Sinks.many().unicast().onBackpressureBuffer();
        
        // 设置当前线程的流式上下文
        StreamContext.setSink(sink);
        
        // 发送开始思考事件
        sink.tryEmitNext(StreamEvent.thinking("正在分析您的请求..."));
        
        try {
            supervisorAgent.stream(request.getMessage())
                    .subscribe(
                            output -> {
                                processStreamOutput(output, sink);
                            },
                            error -> {
                                log.error("Stream error", error);
                                sink.tryEmitNext(StreamEvent.error(error.getMessage()));
                                sink.tryEmitNext(StreamEvent.done());
                                StreamContext.clear();
                            },
                            () -> {
                                sink.tryEmitNext(StreamEvent.done());
                                StreamContext.clear();
                            }
                    );
        } catch (Exception e) {
            log.error("Stream setup error", e);
            sink.tryEmitNext(StreamEvent.error(e.getMessage()));
            sink.tryEmitNext(StreamEvent.done());
            StreamContext.clear();
        }
        
        return sink.asFlux().map(StreamEvent::toSseString);
    }
    
    /**
     * 处理流式输出
     */
    private void processStreamOutput(Object output, Sinks.Many<StreamEvent> sink) {
        if (output == null) return;
        
        // 处理 AssistantMessage
        if (output instanceof AssistantMessage msg) {
            String text = msg.getText();
            if (text != null && !text.isEmpty()) {
                sink.tryEmitNext(StreamEvent.content(text));
            }
            return;
        }
        
        // 处理 ToolMessage (工具调用结果)
        if (output.getClass().getSimpleName().equals("ToolMessage")) {
            try {
                String toolName = (String) output.getClass().getMethod("getToolName").invoke(output);
                sink.tryEmitNext(StreamEvent.toolResult(toolName, "执行完成", 0));
            } catch (Exception e) {
                // ignore
            }
            return;
        }
        
        // 处理其他类型的消息，尝试提取 Agent 调用信息
        String str = output.toString();
        
        // 检测 Agent 调用 - 根据 Spring AI Alibaba 的输出格式
        if (str.contains("agent") || str.contains("Agent")) {
            // 尝试提取 Agent 名称
            if (str.contains("chat_agent")) {
                sink.tryEmitNext(StreamEvent.agentCall("chat_agent", "处理闲聊问候"));
            } else if (str.contains("data_query_agent")) {
                sink.tryEmitNext(StreamEvent.agentCall("data_query_agent", "执行数据查询"));
            } else if (str.contains("article_agent")) {
                sink.tryEmitNext(StreamEvent.agentCall("article_agent", "检索文章资讯"));
            } else if (str.contains("policy_agent")) {
                sink.tryEmitNext(StreamEvent.agentCall("policy_agent", "查询政策法规"));
            }
        }
        
        // 如果是纯文本内容，直接输出
        if (!str.contains("{") && !str.isEmpty()) {
            sink.tryEmitNext(StreamEvent.content(str));
        }
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

    // ==================== 私有辅助方法 ====================

    /**
     * 从 State 中提取响应文本
     */
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

    /**
     * 从流式输出中提取内容
     */
    private String extractContent(Object output) {
        if (output == null) {
            return "";
        }
        
        // 如果是 AssistantMessage，提取文本
        if (output instanceof AssistantMessage msg) {
            return msg.getText();
        }
        
        return output.toString();
    }
}
