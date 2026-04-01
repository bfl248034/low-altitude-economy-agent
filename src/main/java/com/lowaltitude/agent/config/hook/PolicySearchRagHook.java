package com.lowaltitude.agent.config.hook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.lowaltitude.agent.tool.PolicySearchTools;
/**
 * 政策rag hook
 */
@HookPositions({HookPosition.BEFORE_AGENT})
public class PolicySearchRagHook extends AgentHook{

	

	  @Override
	  public String getName() {
	      return "policySearchRagHook";
	  }

	  @Override
	  public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
	      // 从状态中提取用户问题
	      Optional<Object> messagesOpt = state.value("messages");
	      if (messagesOpt.isEmpty()) {
	          return CompletableFuture.completedFuture(Map.of());
	      }
	      System.out.println(state.toString());
	      @SuppressWarnings("unchecked")
	      List<org.springframework.ai.chat.messages.Message> messages = 
	          (List<org.springframework.ai.chat.messages.Message>) messagesOpt.get();
	      
	      // 提取最后一个用户消息作为查询
	      String userQuery = messages.stream()
	          .filter(msg -> msg instanceof org.springframework.ai.chat.messages.UserMessage)
	          .map(msg -> ((org.springframework.ai.chat.messages.UserMessage) msg).getText())
	          .reduce((first, second) -> second) // 获取最后一个
	          .orElse("");

	      if (userQuery.isEmpty()) {
	          return CompletableFuture.completedFuture(Map.of());
	      }

	      try {
			String content = PolicySearchTools.doHttpPost(userQuery, 10);

			  String enhancedSystemPrompt = String.format("""
			          你是一个有用的助手。基于以下上下文回答问题。
			          如果上下文中没有相关信息，请再判断是否需要调用工具完成问题回答。
			          
			          上下文：
			          %s
			          """, 
			          content);
			  
			  // 如果查询被增强，更新消息列表
//	      if (!enhancedSystemPrompt.equals(userQuery)) {
			      List<Message> enhancedMessages = new ArrayList<>();
			      enhancedMessages.add(new SystemMessage(enhancedSystemPrompt));
			      // 保留系统消息和其他消息，只替换用户消息
			      enhancedMessages.addAll(messages);
			      // 将增强后的查询存储到 metadata 中，供后续使用
			      config.metadata().ifPresent(meta -> {
			          meta.put("policy_rag_content", content);
			      });
			      
			      // 返回更新后的消息列表
			      return CompletableFuture.completedFuture(Map.of("messages", enhancedMessages));
		} catch (Exception e) {
			return CompletableFuture.completedFuture(Map.of());
		}
	      // Step 3: 将检索到的上下文存储到状态中，供后续 ModelInterceptor 使用
	      // 存储到 state 中，ModelInterceptor 可以通过 request.getContext() 访问
	      
	  }
	
}
