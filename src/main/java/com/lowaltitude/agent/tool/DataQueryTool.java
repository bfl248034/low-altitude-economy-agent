package com.lowaltitude.agent.tool;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.lowaltitude.agent.entity.DataSourceConfig;
import com.lowaltitude.agent.entity.DataTableConfig;
import com.lowaltitude.agent.entity.Indicator;
import com.lowaltitude.agent.entity.LatestTimeConfig;
import com.lowaltitude.agent.service.DynamicQueryService;
import com.lowaltitude.agent.service.MetadataService;
import com.lowaltitude.agent.service.MetadataService.DimensionConfigWithValues;
import com.lowaltitude.agent.service.retrieval.InMemoryVectorSearchService;
import com.lowaltitude.agent.service.retrieval.InMemoryVectorSearchService.IndicatorVector;
import com.lowaltitude.agent.service.retrieval.SynonymService;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据查询工具 - 3个粗粒度工具
 * 1. matchIndicators: 指标匹配（关键词提取→同义词扩展→向量检索→LLM精排）
 * 2. parseAndBuildSql: 维度解析+SQL生成（获取所有指标维度→LLM解析→生成SQL）
 * 3. executeMultiQuery: 多源并行查询执行
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
    
    private final ExecutorService queryExecutor = Executors.newFixedThreadPool(10);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ==================== Tool 1: 指标匹配 ====================

    @Tool(name = "matchIndicators", description = "指标匹配：从用户查询中识别并匹配相关指标，支持多指标。流程：关键词提取→同义词扩展→向量检索→LLM精排")
    public Map<String, Object> matchIndicators(
            @ToolParam(description = "用户原始查询，如'北京近6个月本科招聘薪资'") String query,
            @ToolParam(description = "返回候选数量，默认10") int topK) {
        
        log.info("Matching indicators for query: {}", query);
        
        try {
            List<String> keywords = extractKeywordsInternal(query);
            List<String> expandedKeywords = expandSynonymsInternal(keywords);
            List<Map<String, Object>> candidates = vectorSearchInternal(expandedKeywords, topK);
            
            if (candidates.isEmpty()) {
                return Map.of(
                    "success", false,
                    "error", "未找到匹配的指标",
                    "suggestion", "请尝试使用更通用的词汇，如'招聘'、'企业'、'专利'等"
                );
            }
            
            Map<String, Object> rerankResult = llmRerankInternal(query, candidates);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> matchedIndicators = (List<Map<String, Object>>) rerankResult.get("indicators");
            
            return Map.of(
                "success", true,
                "indicators", matchedIndicators,
                "isMultiMetric", rerankResult.get("isMultiMetric"),
                "reasoning", rerankResult.get("reasoning"),
                "keywords", expandedKeywords
            );
            
        } catch (Exception e) {
            log.error("Indicator matching failed", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // ==================== Tool 2: 维度解析+SQL生成（合并） ====================

    @Tool(name = "parseAndBuildSql", description = "维度解析+SQL生成：获取所有指标的维度合集→LLM解析维度→根据最大时间推算时间→生成SQL。支持多指标、多地区")
    public Map<String, Object> parseAndBuildSql(
            @ToolParam(description = "用户原始查询") String query,
            @ToolParam(description = "匹配到的指标列表（包含indicatorId, tableId, sourceId等）") List<Map<String, Object>> indicators) {
        
        log.info("Parsing dimensions and building SQL for {} indicators", indicators.size());
        
        try {
            // 1. 收集所有指标的信息
            List<String> indicatorIds = new ArrayList<>();
            Map<String, Map<String, Object>> indicatorMetaMap = new HashMap<>();
            Map<String, List<Map<String, Object>>> tableDimensionsMap = new HashMap<>();
            Map<String, Map<String, Object>> tableSchemaMap = new HashMap<>();
            Set<String> tableIds = new HashSet<>();
            
            // 找出所有指标中的最大时间
            LocalDate maxLatestDate = null;
            String maxFrequency = "M"; // 默认月度
            
            for (Map<String, Object> ind : indicators) {
                String indicatorId = (String) ind.get("indicatorId");
                String tableId = (String) ind.get("tableId");
                indicatorIds.add(indicatorId);
                tableIds.add(tableId);
                
                // 获取指标元数据
                Map<String, Object> meta = getIndicatorMetaInternal(indicatorId);
                indicatorMetaMap.put(indicatorId, meta);
                
                // 获取最新时间，找出最大值
                Map<String, Object> latestTime = getLatestTimeInternal(indicatorId);
                String latestDateStr = (String) latestTime.get("latestDate");
                String frequency = (String) latestTime.getOrDefault("frequency", "M");
                
                if (latestDateStr != null && !latestDateStr.isEmpty()) {
                    try {
                        LocalDate latestDate = parseDate(latestDateStr);
                        if (maxLatestDate == null || latestDate.isAfter(maxLatestDate)) {
                            maxLatestDate = latestDate;
                            maxFrequency = frequency;
                        }
                    } catch (Exception e) {
                        log.warn("Failed to parse latest date: {}", latestDateStr);
                    }
                }
                
                // 获取该指标所在表的维度配置（去重）
                if (!tableDimensionsMap.containsKey(tableId)) {
                    List<Map<String, Object>> dims = getDimensionConfigsInternal(tableId, true);
                    tableDimensionsMap.put(tableId, dims);
                }
                
                // 获取表结构
                if (!tableSchemaMap.containsKey(tableId)) {
                    Map<String, Object> schema = getTableSchemaInternal(tableId);
                    tableSchemaMap.put(tableId, schema);
                }
            }
            
            // 如果无法解析最大时间，使用默认值
            if (maxLatestDate == null) {
                maxLatestDate = LocalDate.of(2024, 6, 30);
            }
            
            // 2. 合并所有维度（去重）
            Map<String, Map<String, Object>> mergedDimensions = new LinkedHashMap<>();
            for (List<Map<String, Object>> dims : tableDimensionsMap.values()) {
                for (Map<String, Object> dim : dims) {
                    String dimId = (String) dim.get("dimensionId");
                    if (!mergedDimensions.containsKey(dimId)) {
                        mergedDimensions.put(dimId, dim);
                    }
                }
            }
            List<Map<String, Object>> allDimensions = new ArrayList<>(mergedDimensions.values());
            
            // 3. LLM解析维度（传入最大时间用于计算）
            Map<String, Object> parseResult = llmParseDimensionsWithMaxTime(
                query, 
                toJson(indicators), 
                toJson(allDimensions),
                maxLatestDate.format(DATE_FORMATTER),
                maxFrequency
            );
            log.info("LLM 维度信息 {}",parseResult.toString());
            // 4. 解析LLM返回的时间范围
            TimeRange timeRange = extractTimeRange(parseResult, maxLatestDate, maxFrequency);
            
            // 5. 解析地区
            List<String> regionCodes = extractRegionCodes(parseResult);
            Integer regionLevel = extractRegionLevel(parseResult);
            
            // 6. 解析维度条件
            String dimensionConditions = extractDimensionConditions(parseResult);
            
            // 7. 按表分组生成SQL（不同表的指标需要分开查询）
            List<Map<String, Object>> sqlTasks = new ArrayList<>();
            
            // 按tableId分组指标
            Map<String, List<String>> tableIndicatorMap = new HashMap<>();
            for (Map<String, Object> ind : indicators) {
                String tableId = (String) ind.get("tableId");
                String indicatorId = (String) ind.get("indicatorId");
                tableIndicatorMap.computeIfAbsent(tableId, k -> new ArrayList<>()).add(indicatorId);
            }
            
            // 为每个表生成SQL
            for (Map.Entry<String, List<String>> entry : tableIndicatorMap.entrySet()) {
                String tableId = entry.getKey();
                List<String> tableIndicatorIds = entry.getValue();
                
                DataTableConfig table = metadataService.getDataTable(tableId).orElse(null);
                if (table == null) continue;
                
                String sql = generateSql(
                    table, 
                    tableIndicatorIds, 
                    timeRange.start.format(DATE_FORMATTER), 
                    timeRange.end.format(DATE_FORMATTER),
                    regionCodes,
                    regionLevel,
                    dimensionConditions
                );
                log.info("生成 sourceId {}",table.getSourceId());
                log.info("生成 sql {}",sql);
                sqlTasks.add(Map.of(
                    "tableId", tableId,
                    "sourceId", table.getSourceId(),
                    "indicatorIds", tableIndicatorIds,
                    "sql", sql
                ));
            }
            Map<String,String> timeMap = Map.of("start", timeRange.start.format(DATE_FORMATTER), "end", timeRange.end.format(DATE_FORMATTER));
            Map<String,Object> resultMap = new HashMap<String, Object>();
     
            resultMap.put("success", true);
    		resultMap.put("indicators", indicators);
			resultMap.put("indicatorIds", indicatorIds);
            resultMap.put("allDimensions", allDimensions);
            resultMap.put("maxLatestDate", maxLatestDate.format(DATE_FORMATTER));
            resultMap.put("frequency", maxFrequency);
            resultMap.put( "parsedDimensions", parseResult);
            resultMap.put("timeRange", timeMap);
                
            resultMap.put( "regionCodes", regionCodes);
            resultMap.put( "regionLevel", regionLevel);
        	resultMap.put( "dimensionConditions", dimensionConditions);
        	resultMap.put( "sqlTasks", sqlTasks);
            return resultMap;
            
        } catch (Exception e) {
            log.error("Parse and build SQL failed", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // ==================== Tool 3: 多源并行查询执行 ====================

    @Tool(name = "executeMultiQuery", description = "多源并行查询执行：支持多指标跨不同数据源并行查询，自动合并结果。每个查询包含sourceId和sql")
    public Map<String, Object> executeMultiQuery(
            @ToolParam(description = "查询任务列表，每项包含sourceId和sql，如[{\"sourceId\":\"ds1\",\"sql\":\"SELECT...\"}]") List<Map<String, String>> queryTasks) {
        
        log.info("Executing {} parallel queries", queryTasks.size());
        
        try {
        	
            List<Future<QueryResult>> futures = new ArrayList<>();
            
            for (Map<String, String> task : queryTasks) {
                String sourceId = task.get("sourceId");
                String sql = task.get("sql");
                log.info("执行 sourceId {}",sourceId);
                log.info("执行 sql {}",sql);
                Future<QueryResult> future = queryExecutor.submit(() -> {
                    try {
                        DataSourceConfig config = metadataService.getDataSource(sourceId)
                                .orElseThrow(() -> new RuntimeException("数据源不存在: " + sourceId));
                        
                        List<Map<String, Object>> results = dynamicQueryService.executeQuery(config, sql);
                        List<Map<String, Object>> translatedResults = results.stream()
                            .map(this::translateResultCodes)
                            .collect(Collectors.toList());
                        
                        return new QueryResult(sourceId, config.getSourceName(), translatedResults, null);
                    } catch (Exception e) {
                        log.error("Query failed for source {}: {}", sourceId, e.getMessage());
                        return new QueryResult(sourceId, null, null, e.getMessage());
                    }
                });
                
                futures.add(future);
            }
            
            List<Map<String, Object>> allResults = new ArrayList<>();
            List<Map<String, Object>> errors = new ArrayList<>();
            int totalRowCount = 0;
            
            for (Future<QueryResult> future : futures) {
                try {
                    QueryResult result = future.get(30, TimeUnit.SECONDS);
                    if (result.error() != null) {
                        errors.add(Map.of("sourceId", result.sourceId(), "error", result.error()));
                    } else {
                        for (Map<String, Object> row : result.data()) {
                            row.put("_dataSource", result.sourceName());
                            row.put("_sourceId", result.sourceId());
                            allResults.add(row);
                        }
                        totalRowCount += result.data().size();
                    }
                } catch (Exception e) {
                    errors.add(Map.of("error", "查询执行超时或失败: " + e.getMessage()));
                }
            }
            
            allResults.sort((a, b) -> {
                String timeA = String.valueOf(a.getOrDefault("time_id", ""));
                String timeB = String.valueOf(b.getOrDefault("time_id", ""));
                return timeB.compareTo(timeA);
            });
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", errors.isEmpty() || !allResults.isEmpty());
            result.put("data", allResults);
            result.put("rowCount", totalRowCount);
            result.put("queryCount", queryTasks.size());
            result.put("successCount", queryTasks.size() - errors.size());
            
            if (!errors.isEmpty()) {
                result.put("errors", errors);
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("Multi-query execution failed", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }

    // ==================== 时间计算相关 ====================

    private record TimeRange(LocalDate start, LocalDate end) {}

    private LocalDate parseDate(String dateStr) {
        // 支持多种格式：yyyy-MM-dd, yyyyMMdd, yyyy-MM, yyyyMM
        dateStr = dateStr.trim();
        if (dateStr.contains("-")) {
            if (dateStr.length() <= 7) { // yyyy-MM
                return LocalDate.parse(dateStr + "-01", DATE_FORMATTER);
            }
            return LocalDate.parse(dateStr.substring(0, 10), DATE_FORMATTER);
        } else {
            if (dateStr.length() == 6) { // yyyyMM
                return LocalDate.parse(dateStr + "01", DateTimeFormatter.ofPattern("yyyyMMdd"));
            } else if (dateStr.length() == 8) { // yyyyMMdd
                return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
            }
        }
        return LocalDate.parse(dateStr.substring(0, Math.min(dateStr.length(), 10)), DATE_FORMATTER);
    }

    private TimeRange extractTimeRange(Map<String, Object> parseResult, LocalDate maxLatestDate, String frequency) {
        try {
            // 尝试从LLM解析结果中提取时间范围
            Object timeRangeObj = parseResult.get("timeRange");
            if (timeRangeObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> timeRange = (Map<String, Object>) timeRangeObj;
                String start = (String) timeRange.get("start");
                String end = (String) timeRange.get("end");
                if (start != null && end != null) {
                    return new TimeRange(parseDate(start), parseDate(end));
                }
            }
            
            // 从rawResponse中解析
            String rawResponse = (String) parseResult.getOrDefault("rawResponse", "");
            
            // 尝试匹配时间范围
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "(\\d{4}-\\d{2}-\\d{2}).*?(\\d{4}-\\d{2}-\\d{2})");
            java.util.regex.Matcher matcher = pattern.matcher(rawResponse);
            if (matcher.find()) {
                return new TimeRange(
                    LocalDate.parse(matcher.group(1), DATE_FORMATTER),
                    LocalDate.parse(matcher.group(2), DATE_FORMATTER)
                );
            }
        } catch (Exception e) {
            log.warn("Failed to extract time range from parse result, using default calculation");
        }
        
        // 默认计算：根据频率和最大时间往前推
        LocalDate end = maxLatestDate;
        LocalDate start;
        
        switch (frequency.toUpperCase()) {
            case "D": // 日度，默认近30天
                start = end.minusDays(30);
                break;
            case "M": // 月度，默认近6个月
                start = end.minusMonths(6).with(TemporalAdjusters.lastDayOfMonth());
                break;
            case "Q": // 季度，默认近4个季度
                start = end.minusMonths(12).with(TemporalAdjusters.lastDayOfMonth());
                break;
            case "Y": // 年度，默认近3年
                start = end.minusYears(3).with(TemporalAdjusters.lastDayOfYear());
                break;
            default: // 默认月度近6个月
                start = end.minusMonths(6).with(TemporalAdjusters.lastDayOfMonth());
        }
        
        return new TimeRange(start, end);
    }

    // ==================== 解析LLM结果 ====================

    @SuppressWarnings("unchecked")
    private List<String> extractRegionCodes(Map<String, Object> parseResult) {
        List<String> codes = new ArrayList<>();
        try {
            Object regionObj = parseResult.get("region");
            if (regionObj instanceof Map) {
                Map<String, Object> region = (Map<String, Object>) regionObj;
                Object codesObj = region.get("codes");
                if (codesObj instanceof List) {
                    codes = ((List<Object>) codesObj).stream()
                        .map(Object::toString)
                        .collect(Collectors.toList());
                }
            }
            
            // 如果没有解析到，尝试从rawResponse中匹配
            if (codes.isEmpty()) {
                String rawResponse = (String) parseResult.getOrDefault("rawResponse", "");
                // 匹配地区编码格式
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(11|12|13|14|15|21|22|23|31|32|33|34|35|36|37|41|42|43|44|45|46|50|51|52|53|54|61|62|63|64|65)\\d{4}");
                java.util.regex.Matcher matcher = pattern.matcher(rawResponse);
                while (matcher.find()) {
                    codes.add(matcher.group());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract region codes");
        }
        return codes;
    }

    @SuppressWarnings("unchecked")
    private Integer extractRegionLevel(Map<String, Object> parseResult) {
        try {
            Object regionObj = parseResult.get("region");
            if (regionObj instanceof Map) {
                Map<String, Object> region = (Map<String, Object>) regionObj;
                Object levelObj = region.get("level");
                if (levelObj != null) {
                    return Integer.valueOf(levelObj.toString());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract region level");
        }
        return null;
    }

    private String extractDimensionConditions(Map<String, Object> parseResult) {
        try {
            Object dimensionsObj = parseResult.get("dimensions");
            if (dimensionsObj != null) {
                return dimensionsObj.toString();
            }
        } catch (Exception e) {
            log.warn("Failed to extract dimension conditions");
        }
        return "{}";
    }

    // ==================== 内部方法 ====================

    private record QueryResult(String sourceId, String sourceName, List<Map<String, Object>> data, String error) {}

    private List<String> extractKeywordsInternal(String query) {
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

    private List<String> expandSynonymsInternal(List<String> keywords) {
        Set<String> expanded = new LinkedHashSet<>();
        for (String keyword : keywords) {
            expanded.add(keyword);
            expanded.addAll(synonymService.expandIndicatorKeywords(keyword));
        }
        return new ArrayList<>(expanded);
    }

    private List<Map<String, Object>> vectorSearchInternal(List<String> keywords, int topK) {
        Set<IndicatorVector> candidates = new HashSet<>();
        for (String keyword : keywords) {
            candidates.addAll(vectorSearchService.hybridSearch(keyword, topK));
        }
        return candidates.stream()
                .map(this::convertVectorToMap)
                .limit(topK)
                .collect(Collectors.toList());
    }

    private Map<String, Object> llmRerankInternal(String query, List<Map<String, Object>> candidates) {
        if (candidates.isEmpty()) {
            return Map.of("indicators", List.of(), "isMultiMetric", false, "reasoning", "无候选指标");
        }

        String prompt = buildRerankPrompt(query, candidates);
        
        try {
            AssistantMessage response = chatModel.call(new Prompt(prompt)).getResult().getOutput();
            String content = response.getText();
            return parseRerankResponse(content, candidates);
        } catch (Exception e) {
            log.error("LLM rerank failed", e);
            return fallbackToTopCandidate(candidates);
        }
    }

    private Map<String, Object> llmParseDimensionsWithMaxTime(
            String query, 
            String indicatorsJson, 
            String dimensionsJson,
            String maxLatestDate,
            String frequency) {
        
        String prompt = buildParsePromptWithMaxTime(query, indicatorsJson, dimensionsJson, maxLatestDate, frequency);
        
        try {
            AssistantMessage response = chatModel.call(new Prompt(prompt)).getResult().getOutput();
            String content = response.getText();
            String json = content.replace("```json", "").replace("```", "");
            return JSONUtil.toBean(json, Map.class);
//            return Map.of("rawResponse", content, "status", "success");
        } catch (Exception e) {
            log.error("LLM parse failed", e);
            return Map.of("error", e.getMessage(), "status", "failed");
        }
    }

    private Map<String, Object> getIndicatorMetaInternal(String indicatorId) {
        return metadataService.getIndicatorById(indicatorId)
                .map(this::convertIndicatorToMap)
                .orElse(Map.of("error", "指标不存在: " + indicatorId));
    }

    private Map<String, Object> getLatestTimeInternal(String indicatorId) {
        return metadataService.getLatestTime(indicatorId)
                .map(this::convertTimeToMap)
                .orElseGet(() -> Map.of(
                    "indicatorId", indicatorId,
                    "frequency", "M",
                    "latestTimeId", "2024-06-30",
                    "latestDate", "2024-06-30"
                ));
    }

    private List<Map<String, Object>> getDimensionConfigsInternal(String tableId, boolean excludeTimeRegion) {
        return metadataService.getTableDimensionConfigs(tableId, excludeTimeRegion)
                .stream()
                .map(this::convertDimensionConfigToMap)
                .collect(Collectors.toList());
    }

    private Map<String, Object> getTableSchemaInternal(String tableId) {
        return metadataService.getDataTable(tableId)
                .map(this::convertTableToMap)
                .orElse(Map.of("error", "表不存在: " + tableId));
    }

    private Map<String, Object> getDataSourceInternal(String sourceId) {
        return metadataService.getDataSource(sourceId)
                .map(this::convertSourceToMap)
                .orElse(Map.of("error", "数据源不存在: " + sourceId));
    }

    private String translateCodesInternal(String dimensionId, String valueCode) {
        return metadataService.getDimensionValueName(dimensionId, valueCode);
    }

    private Map<String, Object> translateResultCodes(Map<String, Object> row) {
        Map<String, Object> translated = new HashMap<>(row);
        
        if (row.containsKey("region_id")) {
            String regionName = translateCodesInternal("region", (String) row.get("region_id"));
            translated.put("region_name", regionName);
        }
        
        return translated;
    }

    private String generateSql(DataTableConfig table, List<String> indicatorIds,
                                String timeStart, String timeEnd, List<String> regionCodes, 
                                Integer regionLevel, String dimensionConditions) {
        StringBuilder sql = new StringBuilder();
        
        Map<String, Object> dimConditions = parseDimensionConditions(dimensionConditions);
        log.info("生成sql，维度信息 {} ",dimensionConditions);
        List<Map<String, Object>> dimensionConfigs = getDimensionConfigsInternal(table.getTableId(), true);
        
        sql.append("SELECT ");
        sql.append(table.getTimeColumn()).append(", ");
        sql.append(table.getRegionColumn()).append(", ");
        sql.append(table.getRegionLevelColumn()).append(", ");
        if (table.getIndicatorColumn() == null) {
        	table.setIndicatorColumn("indicator_id");
        }
        sql.append(table.getIndicatorColumn()).append(", ");
        for (Map<String, Object> dimConfig : dimensionConfigs) {
            String columnName = (String) dimConfig.get("columnName");
            if (columnName != null && !columnName.isEmpty()) {
                sql.append(columnName).append(", ");
            }
        }
        
        sql.append(table.getValueColumn());
        
        sql.append(" FROM ");
        if (table.getSchemaName() != null && !table.getSchemaName().isEmpty()) {
            sql.append(table.getSchemaName()).append(".");
        }
        sql.append(table.getTableName());
        
        List<String> conditions = new ArrayList<>();
        
        if (indicatorIds != null && !indicatorIds.isEmpty()) {
            if (indicatorIds.size() == 1) {
                conditions.add(table.getIndicatorColumn() + " = '" + indicatorIds.get(0) + "'");
            } else {
                String inClause = indicatorIds.stream().map(id -> "'" + id + "'").collect(Collectors.joining(", "));
                conditions.add(table.getIndicatorColumn() + " IN (" + inClause + ")");
            }
        }
        
        if (timeStart != null && timeEnd != null) {
            conditions.add(table.getTimeColumn() + " BETWEEN '" + timeStart + "' AND '" + timeEnd + "'");
        }
        
        
        if (regionCodes != null && !regionCodes.isEmpty()) {
            List<String> validCodes = regionCodes.stream()
                    .filter(code -> !"0".equals(code) && !code.isEmpty())
                    .collect(Collectors.toList());
            
            if (validCodes.size() == 1) {
                conditions.add(table.getRegionColumn() + " = '" + validCodes.get(0) + "'");
            } else if (validCodes.size() > 1) {
                String inClause = validCodes.stream().map(code -> "'" + code + "'").collect(Collectors.joining(", "));
                conditions.add(table.getRegionColumn() + " IN (" + inClause + ")");
            }
        }else {
        	if (regionLevel != null ) {
                conditions.add(table.getRegionLevelColumn() + " = " + regionLevel);
            }
        }
        
        for (Map.Entry<String, Object> entry : dimConditions.entrySet()) {
            String columnName = entry.getKey();
            Object obj = entry.getValue();
            List<String> values = new ArrayList<String>();
            if(obj instanceof String) {
            	values.add(obj.toString());
            }else if(obj instanceof List){
            	values = (List<String>) obj;
            }
            if (values != null && !values.isEmpty()) {
                String defaultValue = null;
                boolean flag = false;
                for (Map<String, Object> dimConfig : dimensionConfigs) {
                    if (columnName.equals(dimConfig.get("columnName"))) {
                        defaultValue = (String) dimConfig.get("defaultValue");
                        flag = true;
                        break;
                    }
                }
                if(!flag) {
                	continue;
                }
                if (values.size() == 1) {
                    String value = values.get(0);
                    if (defaultValue == null || !defaultValue.equals(value)) {
                        conditions.add(columnName + " = '" + value + "'");
                    }
                } else {
//                    List<String> validValues = values.stream()
//                            .filter(v -> defaultValue == null || !defaultValue.equals(v))
//                            .collect(Collectors.toList());
                    
//                    if (!validValues.isEmpty()) {
                        String inClause = values.stream()
                                .map(v -> "'" + v + "'")
                                .collect(Collectors.joining(", "));
                        conditions.add(columnName + " IN (" + inClause + ")");
//                    }
                }
            }
        }
        
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ");
            sql.append(String.join(" AND ", conditions));
        }
        
        sql.append(" ORDER BY ").append(table.getTimeColumn()).append(" DESC");
        sql.append(" LIMIT 100 ");
        return sql.toString();
    }

    private Map<String, Object> parseDimensionConditions(String dimensionConditions) {
        Map<String, List<String>> result = new HashMap<>();
        
        if (dimensionConditions == null || dimensionConditions.isEmpty() || dimensionConditions.equals("{}")) {
            return null;
        }
        try {
	        Map<String,Object> map = JSONUtil.toBean(dimensionConditions, Map.class);
	        return map;
        } catch (Exception e) {
            log.warn("Failed to parse dimension conditions: {}", dimensionConditions, e);
        }
            String json = dimensionConditions.trim();
            if (json.startsWith("{")) json = json.substring(1);
            if (json.endsWith("}")) json = json.substring(0, json.length() - 1);
            
            String[] pairs = json.split(",");
            for (String pair : pairs) {
                String[] kv = pair.split(":");
                if (kv.length == 2) {
                    String key = kv[0].trim().replace("\"", "").replace("'", "");
                    String valueStr = kv[1].trim();
                    
                    List<String> values = new ArrayList<>();
                    if (valueStr.startsWith("[") && valueStr.endsWith("]")) {
                        valueStr = valueStr.substring(1, valueStr.length() - 1);
                        String[] valueArr = valueStr.split(",");
                        for (String v : valueArr) {
                            values.add(v.trim().replace("\"", "").replace("'", ""));
                        }
                    } else {
                        values.add(valueStr.replace("\"", "").replace("'", ""));
                    }
                    
                    result.put(key, values);
                }
            }
       
        
        return null;
    }

    // ==================== 辅助方法 ====================

    private boolean isRegionWord(String word) {
        Set<String> regions = Set.of("北京", "上海", "广州", "深圳", "杭州", "全国", "各省", "各地", "城市", "地区");
        return regions.contains(word);
    }

    private boolean isTimeWord(String word) {
        return Pattern.matches(".*(近|最近|最近\\d+|\\d+个月|\\d+年|年|月|日|趋势).*", word);
    }

    private String toJson(Object obj) {
        return obj.toString();
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

    private Map<String, Object> convertDimensionConfigToMap(DimensionConfigWithValues config) {
        Map<String, Object> map = new HashMap<>();
        
        map.put("dimensionId", config.getDimensionId());
        map.put("dimensionName", config.getDimensionName());
        map.put("columnName", config.getColumnName());
        map.put("defaultValue", config.getDefaultValue());
        map.put("defaultValueName", config.getDefaultValueName());
        
        List<Map<String, Object>> values = config.values().stream()
                .map(v -> {
                    Map<String, Object> valueMap = new HashMap<>();
                    valueMap.put("valueCode", v.getValueCode());
                    valueMap.put("valueName", v.getValueName());
                    valueMap.put("synonyms", v.getSynonyms());
                    boolean isDefault = config.getDefaultValue() != null && config.getDefaultValue().equals(v.getValueCode());
                    valueMap.put("isDefault", isDefault);
                    return valueMap;
                })
                .collect(Collectors.toList());
        map.put("values", values);
        
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

    private String buildParsePromptWithMaxTime(
            String query, 
            String indicatorsJson, 
            String dimensionsJson,
            String maxLatestDate,
            String frequency) {
        
        return """
                作为数据查询分析助手，从用户查询中提取维度条件。
                
                ## 用户查询
                %s
                
                ## 匹配到的指标列表
                %s
                
                ## 所有维度集合（去重后）
                %s
                
                ## 时间基准信息（重要）
                - 最大最新时间：%s
                - 频率：%s (D=日度, M=月度, Q=季度, Y=年度)
                
                ## 时间计算规则（基于最大最新时间推算）
                - "近3个月" → 开始时间 = 最大时间往前推3个月的最后一天，结束时间 = 最大时间
                  例如：最大时间="2024-06-30"，频率=M → start="2024-03-31", end="2024-06-30"
                - "近6个月" → 开始时间 = 最大时间往前推6个月的最后一天
                - "近1年" → 开始时间 = 最大时间往前推1年的最后一天
                - "今年" → 开始时间 = 当年1月1日，结束时间 = 最大时间
                - "去年" → 开始时间 = 去年1月1日，结束时间 = 去年12月31日
                - 默认（未指定时间）→ 开始时间=最大时间，结束时间 = 最大时间
                
                ## 地区解析规则
                - 默认（未指定地区），默认全国 → codes:["100000"]
                - 北京和上海 → codes:["110000","310000"]
                - 北京 → codes:["110000"]
                - 上海和深圳 → codes:["310000","440300"]
                - 各省/各省份/分地区 → level:1
                - 各市 → level:2
                - 全国 →  codes:["100000"]
                
                ## 维度匹配规则
                - 从提供的维度集合中匹配
                - 默认（未指定该维度），返回该维度的默认值 -> edu_level:["默认值"]
                - 支持多值，如 "本科和硕士" → edu_level:["3","4"]
                - 交叉分析：如 "不同学历对比" → 包含所有非默认学历值
                
                ## 输出JSON格式
                {
                  "region": {
                    "codes": ["110000", "310000"],
                    "level": 1,
                    "name": "北京和上海"
                  },
                  "timeRange": {
                    "start": "2024-03-31",
                    "end": "2024-06-30"
                  },
                  "dimensions": {
                    "edu_level": ["3"]
                  },
                  "analysisType": "TREND",
                  "reasoning": "解析说明"
                }
                """.formatted(query, indicatorsJson, dimensionsJson, maxLatestDate, frequency);
    }
}
