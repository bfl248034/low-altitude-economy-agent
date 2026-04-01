package com.lowaltitude.agent.config.interceptor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;

import lombok.extern.slf4j.Slf4j;
@Slf4j
@Configuration
public class InterceptorConfig {
	/**
	 * 工具错误时   重试1次
	 * @return
	 */
	@Bean
	public ToolRetryInterceptor toolRetryInterceptor() {
		return ToolRetryInterceptor.builder()
	      .maxRetries(1)
	      .onFailure(ToolRetryInterceptor.OnFailureBehavior.RETURN_MESSAGE)
	      .build();
	}
}
