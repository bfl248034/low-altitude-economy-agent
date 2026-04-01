package com.lowaltitude.agent.config.hook;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.lowaltitude.agent.tool.DataQueryTool;

import lombok.extern.slf4j.Slf4j;
@Slf4j
@Configuration
public class HookConfig {
	/**
	 * 模型限制调用5次
	 * @return
	 */
	@Bean
	public ModelCallLimitHook modelCallLimitHook() {
		return ModelCallLimitHook.builder().runLimit(5).build();
	}
	/**
	 * 消息压缩 HOOK   
	 * @param chatModel
	 * @return
	 */
	@Bean
	public SummarizationHook summarizationHook(ChatModel chatModel) {
		return SummarizationHook.builder()
				.model(chatModel)
				.maxTokensBeforeSummary(4000)
				.messagesToKeep(15)
				.build();
	}
	
	/**
	 * 工具执行限制 10次
	 * @return
	 */
	@Bean
	public ToolCallLimitHook toolCallLimitHook() {
		return ToolCallLimitHook.builder()
				.runLimit(10)
				.build();
	}
	
	/**
	 * 工政策数据查询RAG
	 * @return
	 */
	@Bean
	public PolicySearchRagHook policySearchRagHook() {
		return new PolicySearchRagHook();
	}
	
	/**
	 * 数据查询RAG
	 * @return
	 */
	@Bean
	public DataSearchRagHook dataSearchRagHook(DataQueryTool dataQueryTool) {
		return new DataSearchRagHook(dataQueryTool);
	}
	
	
	
	
	
}
