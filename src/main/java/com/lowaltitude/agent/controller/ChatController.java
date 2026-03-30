package com.lowaltitude.agent.controller;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
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
     * 流式聊天接口（SSE）
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        log.info("Received stream chat request: {}", request.getMessage());
        
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
        
        try {
            supervisorAgent.stream(request.getMessage())
                    .subscribe(
                            output -> {
                                String content = extractContent(output);
                                if (content != null && !content.isEmpty()) {
                                    sink.tryEmitNext("data: " + content + "\n\n");
                                }
                            },
                            error -> {
                                log.error("Stream error", error);
                                sink.tryEmitNext("data: [ERROR] " + error.getMessage() + "\n\n");
                                sink.tryEmitComplete();
                            },
                            () -> {
                                sink.tryEmitNext("data: [DONE]\n\n");
                                sink.tryEmitComplete();
                            }
                    );
        } catch (Exception e) {
            log.error("Stream setup error", e);
            sink.tryEmitNext("data: [ERROR] " + e.getMessage() + "\n\n");
            sink.tryEmitComplete();
        }
        
        return sink.asFlux();
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
     * 简化处理：尝试获取文本表示
     */
    private String extractContent(Object output) {
        if (output == null) {
            return "";
        }
        
        // 尝试获取文本内容
        String str = output.toString();
        
        // 如果是 AssistantMessage，提取文本
        if (output instanceof AssistantMessage msg) {
            return msg.getText();
        }
        
        return str;
    }
}
