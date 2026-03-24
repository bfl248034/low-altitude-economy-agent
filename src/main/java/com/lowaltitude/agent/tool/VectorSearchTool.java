package com.lowaltitude.agent.tool;

import com.lowaltitude.agent.service.retrieval.InMemoryVectorSearchService;
import com.lowaltitude.agent.service.retrieval.InMemoryVectorSearchService.IndicatorVector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 向量检索和LLM精排工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VectorSearchTool {
    
    private final InMemoryVectorSearchService vectorSearchService;
    private final ChatModel chatModel;
    
    /**
     * 向量检索候选指标
     */
    @Tool(name = "vectorSearch", description = "使用向量+BM25混合检索候选指标")
    public List<Map<String, Object>> vectorSearch(
            @ToolParam(description = "扩展后的关键词列表") List<String> keywords,
            @ToolParam(description = "返回候选数量") int topK) {
        
        log.info("Vector search for keywords: {}, topK: {}", keywords, topK);
        
        Set<IndicatorVector> candidates = new HashSet<>();
        
        // 每个关键词分别检索
        for (String keyword : keywords) {
            List<IndicatorVector> results = vectorSearchService.hybridSearch(keyword, topK);
            candidates.addAll(results);
        }
        
        // 转换为Map列表
        return candidates.stream()
                .map(this::convertToMap)
                .limit(topK)
                .collect(Collectors.toList());
    }
    
    /**
     * LLM精排候选指标
     */
    @Tool(name = "llmRerank", description = "使用LLM对候选指标进行精排，确定最终匹配")
    public Map<String, Object> llmRerank(
            @ToolParam(description = "用户原始查询") String query,
            @ToolParam(description = "候选指标列表") List<Map<String, Object>> candidates) {
        
        log.info("LLM rerank for query: {}, candidates: {}", query, candidates.size());
        
        if (candidates.isEmpty()) {
            return Map.of(
                "indicators", List.of(),
                "isMultiMetric", false,
                "reasoning", "没有候选指标"
            );
        }
        
        String candidatesStr = candidates.stream()
                .map(c -> String.format("- %s: %s (单位:%s, 频率:%s, 标签:%s, 描述:%s)",
                        c.get("indicatorId"),
                        c.get("indicatorName"),
                        c.get("unit"),
                        c.get("frequency"),
                        c.get("tags") != null ? c.get("tags") : "无",
                        c.get("remark") != null ? c.get("remark") : "无"))
                .collect(Collectors.joining("\n"));
        
        String prompt = """
                分析用户查询，从候选指标中选择最匹配的指标。
                
                用户查询: %s
                
                候选指标:
                %s
                
                任务:
                1. 判断用户查询中包含几个指标需求（可能多个）
                2. 从候选中选择最匹配的指标（可多选）
                3. 每个匹配的指标给出匹配理由
                
                输出严格JSON格式:
                {
                  "indicators": [
                    {
                      "indicatorId": "指标ID",
                      "matchScore": 0.95,
                      "matchReason": "匹配理由"
                    }
                  ],
                  "isMultiMetric": false,
                  "reasoning": "整体分析"
                }
                
                如果都不匹配，indicators返回空数组。
                """.formatted(query, candidatesStr);
        
        try {
            String response = chatModel.call(new Prompt(prompt))
                    .getResult().getOutput().getText();
            
            log.info("LLM rerank response: {}", response);
            
            // 简化解析：从响应中提取指标ID
            return parseLlmResponse(response, candidates);
            
        } catch (Exception e) {
            log.error("LLM rerank failed, fallback to top candidate", e);
            // 降级：返回第一个候选
            Map<String, Object> first = candidates.get(0);
            return Map.of(
                "indicators", List.of(Map.of(
                    "indicatorId", first.get("indicatorId"),
                    "matchScore", 0.8,
                    "matchReason", "向量匹配（LLM精排失败）"
                )),
                "isMultiMetric", false,
                "reasoning", "使用向量匹配最高分"
            );
        }
    }
    
    /**
     * LLM解析维度
     */
    @Tool(name = "llmParseDimensions", description = "使用LLM从用户查询中解析维度条件")
    public Map<String, Object> llmParseDimensions(
            @ToolParam(description = "用户原始查询") String query,
            @ToolParam(description = "指标信息JSON") String indicatorJson,
            @ToolParam(description = "维度值列表JSON") String dimensionsJson) {
        
        log.info("LLM parse dimensions for query: {}", query);
        
        String prompt = """
                作为数据查询分析助手，请从用户查询中提取维度条件。
                
                ## 用户查询
                %s
                
                ## 指标信息
                %s
                
                ## 维度值列表（不包含时间和地区）
                %s
                
                ## 任务
                1. **地区解析**：将用户提到的地区解析为编码和级别
                   - 全国 → code: "0", level: 1
                   - 北京 → code: "110000", level: 2
                   - 各省/分省份 → level: 2, 不指定具体code
                   - 其他城市类似
                
                2. **时间计算**：基于频率和最新时间ID计算查询范围
                   - 频率M(月) + 最新时间202406 + "近6个月" → start: "2024-01-01", end: "2024-06-30"
                   - 返回yyyy-MM-dd格式
                
                3. **维度匹配**：从提供的维度值列表中匹配
                   - 支持多值："本科和硕士" → ["3", "4"]
                   - 未提及的使用默认值
                   - 截面分析时isCrossSection=true
                
                4. **分析类型**：
                   - SINGLE: 无特殊关键词
                   - TREND: 趋势、走势
                   - RANKING: 排名、排行
                   - COMPARISON: 对比、比较
                   - CROSS_SECTION: 不同、分、各
                
                ## 输出严格JSON格式
                {
                  "region": {
                    "code": "110000",
                    "name": "北京",
                    "level": 2
                  },
                  "timeRange": {
                    "start": "2024-01-01",
                    "end": "2024-06-30",
                    "originalText": "近6个月"
                  },
                  "dimensions": [
                    {
                      "dimensionId": "edu_level",
                      "values": ["3"],
                      "valueNames": ["本科"],
                      "isDefault": false,
                      "isCrossSection": false
                    }
                  ],
                  "analysisType": "TREND",
                  "reasoning": "解析理由"
                }
                """.formatted(query, indicatorJson, dimensionsJson);
        
        try {
            String response = chatModel.call(new Prompt(prompt))
                    .getResult().getOutput().getText();
            
            log.info("LLM parse dimensions response: {}", response);
            
            // 返回简化结果
            return Map.of(
                "rawResponse", response,
                "status", "success"
            );
            
        } catch (Exception e) {
            log.error("LLM parse dimensions failed", e);
            return Map.of(
                "error", e.getMessage(),
                "status", "failed"
            );
        }
    }
    
    private Map<String, Object> convertToMap(IndicatorVector vector) {
        Map<String, Object> map = new HashMap<>();
        map.put("indicatorId", vector.indicatorId());
        map.put("indicatorName", vector.indicatorName());
        map.put("unit", vector.unit());
        map.put("frequency", vector.frequency());
        map.put("tableId", vector.tableId());
        map.put("tags", vector.tags());
        map.put("remark", vector.remark());
        return map;
    }
    
    private Map<String, Object> parseLlmResponse(String response, List<Map<String, Object>> candidates) {
        // 简化实现：从响应中提取指标ID
        List<Map<String, Object>> matched = new ArrayList<>();
        
        for (Map<String, Object> candidate : candidates) {
            String id = (String) candidate.get("indicatorId");
            if (response.contains(id)) {
                Map<String, Object> m = new HashMap<>();
                m.put("indicatorId", id);
                m.put("indicatorName", candidate.get("indicatorName"));
                m.put("unit", candidate.get("unit"));
                m.put("frequency", candidate.get("frequency"));
                m.put("tableId", candidate.get("tableId"));
                m.put("matchScore", 0.95);
                m.put("matchReason", "LLM确认匹配");
                matched.add(m);
            }
        }
        
        if (matched.isEmpty() && !candidates.isEmpty()) {
            // 如果没有匹配到，返回第一个
            Map<String, Object> first = candidates.get(0);
            Map<String, Object> m = new HashMap<>();
            m.put("indicatorId", first.get("indicatorId"));
            m.put("indicatorName", first.get("indicatorName"));
            m.put("unit", first.get("unit"));
            m.put("frequency", first.get("frequency"));
            m.put("tableId", first.get("tableId"));
            m.put("matchScore", 0.7);
            m.put("matchReason", "向量匹配（LLM未明确选择）");
            matched.add(m);
        }
        
        return Map.of(
            "indicators", matched,
            "isMultiMetric", matched.size() > 1,
            "reasoning", "LLM精排完成"
        );
    }
}
