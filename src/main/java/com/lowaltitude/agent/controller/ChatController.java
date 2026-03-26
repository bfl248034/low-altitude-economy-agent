package com.lowaltitude.agent.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ChatController {

    private final ReactAgent supervisorAgent;

    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        log.info("Received chat request: {}", request.getMessage());
        
        try {
            Optional<OverAllState> result = supervisorAgent.invoke(request.getMessage());
            
            if (result.isPresent()) {
                OverAllState state = result.get();
                String responseText = state.value("messages")
                        .map(messages -> {
                            if (messages instanceof java.util.List) {
                                java.util.List<?> list = (java.util.List<?>) messages;
                                if (!list.isEmpty()) {
                                    Object last = list.get(list.size() - 1);
                                    if (last instanceof AssistantMessage) {
                                        return ((AssistantMessage) last).getText();
                                    }
                                }
                            }
                            return "无响应";
                        })
                        .orElse("无响应");
                
                return ChatResponse.success(responseText);
            } else {
                return ChatResponse.error("处理失败，无返回结果");
            }
        } catch (Exception e) {
            log.error("Chat processing error", e);
            return ChatResponse.error("处理异常: " + e.getMessage());
        }
    }

//    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<String> chatStream(@RequestBody ChatRequest request) {
//        log.info("Received stream chat request: {}", request.getMessage());
//        
//        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();
//        
//        supervisorAgent.stream(request.getMessage())
//                .subscribe(
//                        output -> {
//                            String content = extractContent(output);
//                            if (content != null && !content.isEmpty()) {
//                                sink.tryEmitNext("data: " + content + "\n\n");
//                            }
//                        },
//                        error -> {
//                            log.error("Stream error", error);
//                            sink.tryEmitNext("data: [ERROR] " + error.getMessage() + "\n\n");
//                            sink.tryEmitComplete();
//                        },
//                        () -> {
//                            sink.tryEmitNext("data: [DONE]\n\n");
//                            sink.tryEmitComplete();
//                        }
//                );
//        
//        return sink.asFlux();
//    }

//    private String extractContent(Object output) {
//        if (output instanceof com.alibaba.cloud.ai.graph.NodeOutput) {
//            com.alibaba.cloud.ai.graph.NodeOutput nodeOutput = (com.alibaba.cloud.ai.graph.NodeOutput) output;
//            Object message = nodeOutput.value();
//            if (message instanceof AssistantMessage) {
//                return ((AssistantMessage) message).getText();
//            }
//        }
//        return output.toString();
//    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "low-altitude-economy-agent");
    }
}
