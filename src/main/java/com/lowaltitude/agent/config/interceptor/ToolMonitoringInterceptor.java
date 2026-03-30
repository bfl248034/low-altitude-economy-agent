package com.lowaltitude.agent.config.interceptor;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.lowaltitude.agent.controller.StreamEvent;
import com.lowaltitude.agent.stream.StreamContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ToolMonitoringInterceptor extends ToolInterceptor {

    @Override
    public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
        String toolName = request.getToolName();
        String params = request.getArguments() != null ? request.getArguments().toString() : "";
        long startTime = System.currentTimeMillis();

        // 打印到控制台
        System.out.println("执行工具: " + toolName);
        
        // 发送到 SSE 流
        if (StreamContext.isActive()) {
            StreamContext.emit(StreamEvent.toolCall(toolName, params));
        }

        try {
            ToolCallResponse response = handler.call(request);
            long duration = System.currentTimeMillis() - startTime;
            
            System.out.println("工具执行结果: {} " + response.getResult());
            System.out.println("工具 " + toolName + " 执行成功 (耗时: " + duration + "ms)");

            // 发送工具完成事件
            if (StreamContext.isActive()) {
                StreamContext.emit(StreamEvent.toolResult(toolName, 
                    response.getResult() != null ? response.getResult().toString() : "", duration));
            }

            return response;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            System.err.println("工具 " + toolName + " 执行失败 (耗时: " + duration + "ms): " + e.getMessage());

            // 发送工具错误事件
            if (StreamContext.isActive()) {
                StreamContext.emit(StreamEvent.toolResult(toolName, "错误: " + e.getMessage(), duration));
            }

            return ToolCallResponse.of(
                request.getToolCallId(),
                request.getToolName(),
                "工具执行失败: " + e.getMessage()
            );
        }
    }

    @Override
    public String getName() {
        return "ToolMonitoringInterceptor";
    }
}
