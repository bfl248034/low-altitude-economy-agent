package com.lowaltitude.agent.tool;


import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.lowaltitude.agent.service.DynamicQueryService;
import com.lowaltitude.agent.service.MetadataService;
import com.lowaltitude.agent.service.retrieval.InMemoryVectorSearchService;
import com.lowaltitude.agent.service.retrieval.SynonymService;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpStatus;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Slf4j
@Component
@RequiredArgsConstructor
public class PolicySearchTools {
	
	private static String url = "http://192.168.1.210:6300/rag/search";
	
	@Tool(name = "searchPolicy", description = "搜索政策内容")
	public static String doHttpPost( 
			@ToolParam(description = "用户原始查询，如'小微企业申请装修补贴的具体政策有哪些？'")String searchTxt,
			@ToolParam(description = "返回候选数量，默认10")Integer topK) {
		String jsonBody = "{ \"question\": \""+searchTxt+"\", \"top_k\": "+topK+" }";
		log.info("算法请求体: {}", jsonBody);
		HttpResponse res = HttpRequest.post(url)
				.header("Content-Type", "application/json; charset=utf-8")
				.body(jsonBody)
				.timeout(10000)
				.setReadTimeout(10000)
				.execute();
		StringBuffer docContent = new StringBuffer();
		if (res.getStatus() == HttpStatus.HTTP_OK) {
			
			String responseBody = res.body();
			JSONObject obj = JSONUtil.parseObj(responseBody);
			log.info(obj.keySet().toString());
			String resultJsonArr = obj.get("融合结果").toString();
			JSONArray  jsonArr = JSONUtil.parseArray(resultJsonArr);
			for(int i=0;i<jsonArr.size();i++) {
				String docJson = jsonArr.get(i).toString();
				JSONObject docObj = JSONUtil.parseObj(docJson);
				String content = docObj.get("content").toString();
				JSONObject detailJson = JSONUtil.parseObj(docObj.get("metadata").toString());
				
				String title = detailJson.get("title").toString();
//				log.info("标题：{}  内容：{}",title,content);
				docContent.append("政策标题："+title);
				docContent.append("\\n");
				docContent.append("政策内容片段："+content);
				docContent.append("\\n");
			}
//			log.info("算法响应体: {}", obj.get("融合结果").toString());
			return docContent.toString();
		} else {
			String errorMsg = "HTTP " + res.getStatus() + ": " + res.body();
			log.error("算法调用失败: {}", errorMsg);
			throw new RuntimeException(errorMsg);
		}
	}
	public static void main(String[] args) {
//		doHttpPost();
	}

}
