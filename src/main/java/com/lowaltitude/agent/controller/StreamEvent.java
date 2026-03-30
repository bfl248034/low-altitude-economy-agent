package com.lowaltitude.agent.controller;

import lombok.Data;

/**
 * SSE 流式事件类型
 */
@Data
public class StreamEvent {
    
    private String type;
    private String content;
    private String data;
    
    public static StreamEvent thinking(String content) {
        StreamEvent event = new StreamEvent();
        event.setType("thinking");
        event.setContent(content);
        return event;
    }
    
    public static StreamEvent agentCall(String agentName, String purpose) {
        StreamEvent event = new StreamEvent();
        event.setType("agent_call");
        event.setContent(agentName);
        event.setData(purpose);
        return event;
    }
    
    public static StreamEvent toolCall(String toolName, String params) {
        StreamEvent event = new StreamEvent();
        event.setType("tool_call");
        event.setContent(toolName);
        event.setData(params);
        return event;
    }
    
    public static StreamEvent toolResult(String toolName, String result, long duration) {
        StreamEvent event = new StreamEvent();
        event.setType("tool_result");
        event.setContent(toolName + "|" + duration);
        return event;
    }
    
    public static StreamEvent content(String text) {
        StreamEvent event = new StreamEvent();
        event.setType("content");
        event.setContent(text);
        return event;
    }
    
    public static StreamEvent done() {
        StreamEvent event = new StreamEvent();
        event.setType("done");
        return event;
    }
    
    public static StreamEvent error(String message) {
        StreamEvent event = new StreamEvent();
        event.setType("error");
        event.setContent(message);
        return event;
    }
    
    /**
     * 转为 SSE 格式: event|content|data
     * 使用 Base64 编码避免特殊字符问题
     */
    public String toSseString() {
        StringBuilder sb = new StringBuilder();
        sb.append("data: ").append(encode(type));
        sb.append("|").append(encode(content != null ? content : ""));
        sb.append("|").append(encode(data != null ? data : ""));
        sb.append("\n\n");
        return sb.toString();
    }
    
    private String encode(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        // 使用 Base64 编码避免特殊字符问题
        return java.util.Base64.getEncoder().encodeToString(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
