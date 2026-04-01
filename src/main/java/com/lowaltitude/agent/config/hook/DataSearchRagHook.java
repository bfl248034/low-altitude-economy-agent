package com.lowaltitude.agent.config.hook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.lowaltitude.agent.tool.DataQueryTool;
import com.lowaltitude.agent.tool.PolicySearchTools;

import cn.hutool.json.JSONUtil;

/**
 * 数据 rag hook
 */
@HookPositions({HookPosition.BEFORE_AGENT})
public class DataSearchRagHook extends AgentHook{
	
	private DataQueryTool dataQueryTool;
	
	public DataSearchRagHook(DataQueryTool dataQueryTool) {
		this.dataQueryTool = dataQueryTool;
	}
	
	  @Override
	  public String getName() {
	      return "dataSearchRagHook";
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
	    	  Map<String, Object> indicatorMap = dataQueryTool.matchIndicators(userQuery, 3);
			
	    	  List<Map<String,Object>> indicators = (List<Map<String, Object>>) indicatorMap.get("indicators");
	    	  
	    	  Map<String, Object> sqlMap = dataQueryTool.parseAndBuildSql(userQuery, indicators);
	    	  
	    	  // 指标信息
	            List<Map<String, Object>> indicatorList = (List<Map<String, Object>>) sqlMap.get("indicatorList");
	            List<Map<String, Object>> allDimensions = (List<Map<String, Object>>) sqlMap.get("allDimensions");
	    	  
	    	  List<Map<String,String>> sqlList = (List<Map<String, String>>) sqlMap.get("sqlTasks");
			  
	    	  Map<String, Object> resMap = dataQueryTool.executeMultiQuery(sqlList);
	    	  
	    	  List<Map<String, Object>> allResults = (List<Map<String, Object>>) resMap.get("data");
	    	  
	    	  String enhancedSystemPrompt = String.format("""
			          你是一个有用的助手。基于以下上下文回答问题。
			          如果上下文中没有相关信息，请再判断是否需要调用工具完成问题回答。
			          
			          ## 上下文
			           ### 指标信息：
			          	%s
			          
			           ### 维度信息：
			            %s
			            
			           ### 结果数据：
			          	%s
			          """, 
			          JSONUtil.toJsonStr(indicatorList),
			          JSONUtil.toJsonStr(allDimensions),
			          JSONUtil.toJsonStr(allResults));
			  
			  // 如果查询被增强，更新消息列表
//	      if (!enhancedSystemPrompt.equals(userQuery)) {
			      List<Message> enhancedMessages = new ArrayList<>();
			      enhancedMessages.add(new SystemMessage(enhancedSystemPrompt));
			      // 保留系统消息和其他消息，只替换用户消息
			      enhancedMessages.addAll(messages);
			      // 将增强后的查询存储到 metadata 中，供后续使用
			      config.metadata().ifPresent(meta -> {
			          meta.put("indicatorList", indicatorList);
			          meta.put("allDimensions", allDimensions);
			          meta.put("allResults", allResults);
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
