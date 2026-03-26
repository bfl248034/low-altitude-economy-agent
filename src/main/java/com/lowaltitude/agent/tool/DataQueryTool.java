package com.lowaltitude.agent.tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.lowaltitude.agent.entity.DataSourceConfig;
import com.lowaltitude.agent.entity.DataTableConfig;
import com.lowaltitude.agent.entity.DimensionValue;
import com.lowaltitude.agent.entity.Indicator;
import com.lowaltitude.agent.entity.LatestTimeConfig;
import com.lowaltitude.agent.service.DynamicQueryService;
import com.lowaltitude.agent.service.MetadataService;
import com.lowaltitude.agent.service.retrieval.InMemoryVectorSearchService;
import com.lowaltitude.agent.service.retrieval.InMemoryVectorSearchService.IndicatorVector;
import com.lowaltitude.agent.service.retrieval.SynonymService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据查询工具 - 一站式数据查询能力
 * 包含：文本处理、向量检索、元数据查询、SQL执行
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataQueryTool {

    private final SynonymService synonymService;
    private final InMemoryVectorSearchService vectorSearchService;
    private final MetadataService metadataService;
    private final DynamicQueryService dynamicQueryService;
    private final ChatModel chatModel;

    // ==================== 文本处理 ====================

    /**
     * 从用户查询中提取潜在指标关键词
     */
    @Tool(name = "extractKeywords", description = "从用户查询中提取潜在指标关键词，过滤地区和时间词")
    public List<String> extractKeywords(
            @ToolParam(description = "用户原始查询") String query) {
        
        log.info("Extracting keywords from: {}", query);
        Set<String> keywords = new LinkedHashSet<>();
        
        String[] parts = query.split("[和与、，,的\\s]+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.length() >= 2 && !isRegionWord(trimmed) && !isTimeWord(trimmed)) {
                keywords.add(trimmed);
            }
        }
        
        if (keywords.isEmpty()) {
            keywords.add(query);
        }
        
        return new ArrayList<>(keywords);
    }

    /**
     * 扩展关键词的同义词
     */
    @Tool(name = "expandSynonyms", description = "扩展关键词的同义词列表，增加召回率")
    public List<String> expandSynonyms(
            @ToolParam(description = "关键词列表") List<String> keywords) {
        
        Set<String> expanded = new LinkedHashSet<>();
        for (String keyword : keywords) {
            expanded.add(keyword);
            expanded.addAll(synonymService.expandIndicatorKeywords(keyword));
        }
        return new ArrayList<>(expanded);
    }

    /**
     * 计算BM25分数
     */
    @Tool(name = "bm25Score", description = "计算查询与指标的BM25文本匹配分数")
    public double bm25Score(
            @ToolParam(description = "用户查询") String query,
            @ToolParam(description = "指标名称") String indicatorName,
            @ToolParam(description = "指标标签") String tags,
            @ToolParam(description = "指标描述") String remark) {
        
        String queryLower = query.toLowerCase();
        String[] queryTerms = queryLower.split("\\s+");
        String text = (indicatorName + " " + (tags != null ? tags : "") + " " + 
                      (remark != null ? remark : "")).toLowerCase();
        
        double score = 0.0;
        for (String term : queryTerms) {
            if (indicatorName.toLowerCase().contains(term)) score += 2.0;
            if (tags != null && tags.toLowerCase().contains(term)) score += 1.5;
            if (remark != null && remark.toLowerCase().contains(term)) score += 1.0;
        }
        return score / (1 + 0.01 * text.length());
    }

    // ==================== 向量检索 ====================

    /**
     * 向量检索候选指标
     */
    @Tool(name = "vectorSearch", description = "使用向量+BM25混合检索候选指标")
    public List<Map<String, Object>> vectorSearch(
            @ToolParam(description = "扩展后的关键词列表") List<String> keywords,
            @ToolParam(description = "返回候选数量") int topK) {
        
        Set<IndicatorVector> candidates = new HashSet<>();
        for (String keyword : keywords) {
            candidates.addAll(vectorSearchService.hybridSearch(keyword, topK));
        }
        
        return candidates.stream()
                .map(this::convertVectorToMap)
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * LLM精排候选指标
     */
    @Tool(name = "llmRerank", description = "使用LLM对候选指标精排，确定最终匹配（支持多指标）")
    public Map<String, Object> llmRerank(
            @ToolParam(description = "用户原始查询") String query,
            @ToolParam(description = "候选指标列表") List<Map<String, Object>> candidates) {
        
        if (candidates.isEmpty()) {
            return Map.of("indicators", List.of(), "isMultiMetric", false, "reasoning", "无候选指标");
        }

        String prompt = buildRerankPrompt(query, candidates);
        
        try {
            String response = chatModel.call(new Prompt(prompt))
                    .getResult().getOutput().getText();
            return parseRerankResponse(response, candidates);
        } catch (Exception e) {
            log.error("LLM rerank failed", e);
            return fallbackToTopCandidate(candidates);
        }
    }

    /**
     * LLM解析维度
     */
    @Tool(name = "llmParseDimensions", description = "使用LLM从用户查询中解析维度条件（时间、地区、其他维度）")
    public Map<String, Object> llmParseDimensions(
            @ToolParam(description = "用户原始查询") String query,
            @ToolParam(description = "指标信息JSON") String indicatorJson,
            @ToolParam(description = "维度值列表JSON") String dimensionsJson) {
        
        String prompt = buildParsePrompt(query, indicatorJson, dimensionsJson);
        
        try {
            String response = chatModel.call(new Prompt(prompt))
                    .getResult().getOutput().getText();
            return Map.of("rawResponse", response, "status", "success");
        } catch (Exception e) {
            log.error("LLM parse failed", e);
            return Map.of("error", e.getMessage(), "status", "failed");
        }
    }

    // ==================== 元数据查询 ====================

    @Tool(name = "getIndicatorMeta", description = "根据指标ID获取完整元数据")
    public Map<String, Object> getIndicatorMeta(@ToolParam(description = "指标ID") String indicatorId) {
        return metadataService.getIndicatorById(indicatorId)
                .map(this::convertIndicatorToMap)
                .orElse(Map.of("error", "指标不存在: " + indicatorId));
    }

    @Tool(name = "getLatestTime", description = "获取指标的最新数据时间配置")
    public Map<String, Object> getLatestTime(@ToolParam(description = "指标ID") String indicatorId) {
        return metadataService.getLatestTime(indicatorId)
                .map(this::convertTimeToMap)
                .orElseGet(() -> Map.of(
                    "indicatorId", indicatorId,
                    "frequency", "M",
                    "latestTimeId", "2024-06-30",
                    "latestDate", "2024-06-30"
                ));
    }

    @Tool(name = "getDimensionValues", description = "获取数据表的维度值列表（可排除时间和地区）")
    public List<Map<String, Object>> getDimensionValues(
            @ToolParam(description = "表ID") String tableId,
            @ToolParam(description = "是否排除时间和地区") boolean excludeTimeRegion) {
        
    	List<Map<String, Object>> mapList =  metadataService.getDimensionValuesByTable(tableId, excludeTimeRegion)
                .stream()
                .map(this::convertDimensionToMap)
                .collect(Collectors.toList());
    	
    	return mapList;
    }

    @Tool(name = "getTableSchema", description = "获取数据表的完整结构信息")
    public Map<String, Object> getTableSchema(@ToolParam(description = "表ID") String tableId) {
        return metadataService.getDataTable(tableId)
                .map(this::convertTableToMap)
                .orElse(Map.of("error", "表不存在: " + tableId));
    }

    @Tool(name = "getDataSource", description = "获取数据源配置信息")
    public Map<String, Object> getDataSource(@ToolParam(description = "数据源ID") String sourceId) {
        return metadataService.getDataSource(sourceId)
                .map(this::convertSourceToMap)
                .orElse(Map.of("error", "数据源不存在: " + sourceId));
    }

    @Tool(name = "translateCodes", description = "将维度编码翻译为中文名称")
    public String translateCodes(
            @ToolParam(description = "维度ID，如region、edu_level") String dimensionId,
            @ToolParam(description = "编码值，如110000、3") String valueCode) {
        return metadataService.getDimensionValueName(dimensionId, valueCode);
    }

    // ==================== SQL执行 ====================

    @Tool(name = "executeSql", description = "在指定数据源执行SQL查询并返回结果")
    public Map<String, Object> executeSql(
            @ToolParam(description = "数据源ID") String sourceId,
            @ToolParam(description = "SQL语句") String sql) {
        
        try {
            DataSourceConfig config = metadataService.getDataSource(sourceId)
                    .orElseThrow(() -> new RuntimeException("数据源不存在: " + sourceId));
            
            List<Map<String, Object>> results = dynamicQueryService.executeQuery(config, sql);
            return Map.of("success", true, "data", results, "rowCount", results.size());
        } catch (Exception e) {
            log.error("Query execution failed", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private boolean isRegionWord(String word) {
        Set<String> regions = Set.of("北京", "上海", "广州", "深圳", "杭州", "全国", "各省", "各地", "城市", "地区");
        return regions.contains(word);
    }

    private boolean isTimeWord(String word) {
        return Pattern.matches(".*(近|最近|最近\\d+|\\d+个月|\\d+年|年|月|日|趋势).*", word);
    }

    private Map<String, Object> convertVectorToMap(IndicatorVector v) {
        Map<String, Object> map = new HashMap<>();
        map.put("indicatorId", v.indicatorId());
        map.put("indicatorName", v.indicatorName());
        map.put("unit", v.unit());
        map.put("frequency", v.frequency());
        map.put("tableId", v.tableId());
        map.put("tags", v.tags());
        map.put("remark", v.remark());
        return map;
    }

    private Map<String, Object> convertIndicatorToMap(Indicator ind) {
        Map<String, Object> map = new HashMap<>();
        map.put("indicatorId", ind.getIndicatorId());
        map.put("indicatorName", ind.getIndicatorName());
        map.put("unit", ind.getUnit());
        map.put("frequency", ind.getFrequency());
        map.put("tableId", ind.getTableId());
        map.put("tags", ind.getTags());
        map.put("remark", ind.getRemark());
        return map;
    }

    private Map<String, Object> convertTimeToMap(LatestTimeConfig cfg) {
        Map<String, Object> map = new HashMap<>();
        map.put("indicatorId", cfg.getIndicatorId());
        map.put("frequency", cfg.getFrequency());
        map.put("latestTimeId", cfg.getLatestTimeId());
        map.put("latestDate", cfg.getLatestDate());
        return map;
    }

    private Map<String, Object> convertDimensionToMap(DimensionValue v) {
        Map<String, Object> map = new HashMap<>();
        map.put("dimensionId", v.getDimensionId());
        map.put("dimensionName", v.getDimensionName());
        map.put("valueCode", v.getValueCode());
        map.put("valueName", v.getValueName());
        map.put("synonyms", v.getSynonyms());
//        map.put("isDefault", v.getIsDefault());
        return map;
    }

    private Map<String, Object> convertTableToMap(DataTableConfig t) {
        Map<String, Object> map = new HashMap<>();
        map.put("tableId", t.getTableId());
        map.put("tableName", t.getTableName());
        map.put("sourceId", t.getSourceId());
        map.put("timeColumn", t.getTimeColumn());
        map.put("regionColumn", t.getRegionColumn());
        map.put("regionLevelColumn", t.getRegionLevelColumn());
        map.put("valueColumn", t.getValueColumn());
        map.put("indicatorColumn", t.getIndicatorColumn());
        return map;
    }

    private Map<String, Object> convertSourceToMap(DataSourceConfig s) {
        Map<String, Object> map = new HashMap<>();
        map.put("sourceId", s.getSourceId());
        map.put("sourceName", s.getSourceName());
        map.put("sourceType", s.getSourceType());
        map.put("host", s.getHost());
        map.put("port", s.getPort());
        map.put("databaseName", s.getDatabaseName());
        return map;
    }

    private String buildRerankPrompt(String query, List<Map<String, Object>> candidates) {
        String candidatesStr = candidates.stream()
                .map(c -> String.format("- %s: %s (单位:%s, 频率:%s, 标签:%s, 描述:%s)",
                        c.get("indicatorId"), c.get("indicatorName"), c.get("unit"),
                        c.get("frequency"), c.get("tags") != null ? c.get("tags") : "无",
                        c.get("remark") != null ? c.get("remark") : "无"))
                .collect(Collectors.joining("\n"));

        return """
                分析用户查询，从候选指标中选择最匹配的指标。
                
                用户查询: %s
                候选指标:\n%s
                
                任务:
                1. 判断用户查询中包含几个指标需求
                2. 从候选中选择最匹配的指标（可多选）
                3. 给出匹配理由
                
                输出严格JSON格式:
                {
                  "indicators": [{"indicatorId": "...", "matchScore": 0.95, "matchReason": "..."}],
                  "isMultiMetric": false,
                  "reasoning": "整体分析"
                }
                """.formatted(query, candidatesStr);
    }

    private Map<String, Object> parseRerankResponse(String response, List<Map<String, Object>> candidates) {
        List<Map<String, Object>> matched = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            String id = (String) candidate.get("indicatorId");
            if (response.contains(id)) {
                Map<String, Object> m = new HashMap<>();
                m.putAll(candidate);
                m.put("matchScore", 0.95);
                m.put("matchReason", "LLM确认匹配");
                matched.add(m);
            }
        }
        if (matched.isEmpty() && !candidates.isEmpty()) {
            Map<String, Object> first = new HashMap<>(candidates.get(0));
            first.put("matchScore", 0.7);
            first.put("matchReason", "向量匹配（LLM未明确选择）");
            matched.add(first);
        }
        return Map.of("indicators", matched, "isMultiMetric", matched.size() > 1, "reasoning", "LLM精排完成");
    }

    private Map<String, Object> fallbackToTopCandidate(List<Map<String, Object>> candidates) {
        Map<String, Object> first = new HashMap<>(candidates.get(0));
        first.put("matchScore", 0.8);
        first.put("matchReason", "向量匹配（LLM精排失败）");
        return Map.of("indicators", List.of(first), "isMultiMetric", false, "reasoning", "使用向量匹配最高分");
    }

    private String buildParsePrompt(String query, String indicatorJson, String dimensionsJson) {
        return """
                作为数据查询分析助手，从用户查询中提取维度条件。
                
                ## 用户查询
                %s
                
                ## 指标信息
                %s
                
                ## 维度值列表（不含时间和地区）
                %s
                
                ## 任务
                1. 地区解析：自然语言→编码和级别（全国→100000/1, 北京→110000/2, 各省→level=2）
                2. 时间计算：基于频率和最新时间ID计算范围
                3. 维度匹配：从提供的列表中匹配，支持多值
                4. 分析类型：SINGLE/TREND/RANKING/COMPARISON/CROSS_SECTION
                
                输出JSON格式: {region, timeRange, dimensions, analysisType, reasoning}
                """.formatted(query, indicatorJson, dimensionsJson);
    }
}
