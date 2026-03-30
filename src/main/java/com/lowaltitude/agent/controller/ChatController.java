package com.lowaltitude.agent.controller;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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
     * 流式聊天接口（SSE）- 使用 SseEmitter
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        log.info("Received stream chat request: {}", request.getMessage());
        
        SseEmitter emitter = new SseEmitter(300000L);
        
        CompletableFuture.runAsync(() -> {
            try {
                // 发送开始事件
                sendEvent(emitter, "thinking", "正在分析您的请求...");
                
                supervisorAgent.stream(request.getMessage())
                    .subscribe(
                        output -> {
                            processStreamOutput(output, emitter);
                        },
                        error -> {
                            log.error("Stream error", error);
                            sendEvent(emitter, "error", error.getMessage());
                            sendEvent(emitter, "done", "");
                            emitter.complete();
                        },
                        () -> {
                            sendEvent(emitter, "done", "");
                            emitter.complete();
                        }
                    );
            } catch (Exception e) {
                log.error("Stream setup error", e);
                sendEvent(emitter, "error", e.getMessage());
                sendEvent(emitter, "done", "");
                emitter.complete();
            }
        });
        
        return emitter;
    }
    
    private void sendEvent(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event()
                .name(eventName)
                .data(data));
        } catch (IOException e) {
            log.error("Failed to send SSE event", e);
        }
    }
    
    private void processStreamOutput(Object output, SseEmitter emitter) {
        if (output == null) return;
        
        // 处理 AssistantMessage - 这是主要的 AI 回复内容
        if (output instanceof AssistantMessage msg) {
            String text = msg.getText();
            if (text != null && !text.isEmpty()) {
                sendEvent(emitter, "content", text);
            }
            return;
        }
        
        // 处理 ToolMessage (工具调用)
        String className = output.getClass().getSimpleName();
        if (className.contains("ToolMessage") || className.contains("ToolResponse")) {
            try {
                String toolName = (String) output.getClass().getMethod("getToolName").invoke(output);
                sendEvent(emitter, "tool", toolName + "|执行完成");
            } catch (Exception e) {
                log.debug("Not a tool message");
            }
            return;
        }
        
        // 检测 Agent 调用
        String str = output.toString();
        if (str.contains("chat_agent")) {
            sendEvent(emitter, "agent", "chat_agent|处理闲聊问候");
        } else if (str.contains("data_query_agent")) {
            sendEvent(emitter, "agent", "data_query_agent|执行数据查询");
        } else if (str.contains("policy_agent")) {
            sendEvent(emitter, "agent", "policy_agent|查询政策法规");
        }
        
        // 尝试提取其他有意义的文本内容
        if (!str.startsWith("{") && str.length() > 5 && !str.contains("OverAllState")) {
            sendEvent(emitter, "content", str);
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
