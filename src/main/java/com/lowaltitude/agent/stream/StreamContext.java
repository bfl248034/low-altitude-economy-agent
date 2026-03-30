package com.lowaltitude.agent.stream;

import com.lowaltitude.agent.controller.StreamEvent;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;

/**
 * 流式输出上下文管理器
 * 用于在拦截器和控制器之间共享 SSE Sink
 */
@Slf4j
public class StreamContext {
    
    private static final ThreadLocal<Sinks.Many<StreamEvent>> SINK_HOLDER = new ThreadLocal<>();
    
    /**
     * 设置当前线程的 SSE Sink
     */
    public static void setSink(Sinks.Many<StreamEvent> sink) {
        SINK_HOLDER.set(sink);
    }
    
    /**
     * 获取当前线程的 SSE Sink
     */
    public static Sinks.Many<StreamEvent> getSink() {
        return SINK_HOLDER.get();
    }
    
    /**
     * 清除当前线程的 SSE Sink
     */
    public static void clear() {
        SINK_HOLDER.remove();
    }
    
    /**
     * 发送事件到 SSE
     */
    public static void emit(StreamEvent event) {
        Sinks.Many<StreamEvent> sink = getSink();
        if (sink != null) {
            sink.tryEmitNext(event);
        }
    }
    
    /**
     * 检查是否有活动的流式连接
     */
    public static boolean isActive() {
        return getSink() != null;
    }
}
