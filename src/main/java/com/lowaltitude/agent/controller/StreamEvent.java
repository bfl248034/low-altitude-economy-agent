package com.lowaltitude.agent.controller;

import lombok.Data;

/**
 * SSE 流式事件类型
 */
@Data
public class StreamEvent {
    
    /**
     * 事件类型
     * - thinking: 思考/规划开始
     * - agent_call: 智能体调用
     * - tool_call: 工具调用
     * - tool_result: 工具执行结果
     * - content: AI 生成的内容
     * - done: 完成
     * - error: 错误
     */
    private String type;
    
    /**
     * 事件内容
     */
    private String content;
    
    /**
     * 额外数据（JSON格式）
     */
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
        event.setContent("🤖 调用智能体: " + agentName);
        event.setData("{\"agent\":\"" + agentName + "\",\"purpose\":\"" + purpose + "\"}");
        return event;
    }
    
    public static StreamEvent toolCall(String toolName, String params) {
        StreamEvent event = new StreamEvent();
        event.setType("tool_call");
        event.setContent("🔧 执行工具: " + toolName);
        event.setData("{\"tool\":\"" + toolName + "\",\"params\":\"" + params + "\"}");
        return event;
    }
    
    public static StreamEvent toolResult(String toolName, String result, long duration) {
        StreamEvent event = new StreamEvent();
        event.setType("tool_result");
        event.setContent("✅ 工具完成: " + toolName + " (" + duration + "ms)");
        event.setData("{\"tool\":\"" + toolName + "\",\"duration\":" + duration + "}");
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
        event.setContent("[DONE]");
        return event;
    }
    
    public static StreamEvent error(String message) {
        StreamEvent event = new StreamEvent();
        event.setType("error");
        event.setContent(message);
        return event;
    }
    
    public String toSseString() {
        return "data: " + type + "|" + escape(content) + (data != null ? "|" + data : "") + "\n\n";
    }
    
    private String escape(String s) {
        if (s == null) return "";
        return s.replace("|", "\\|").replace("\n", "\\n");
    }
}
