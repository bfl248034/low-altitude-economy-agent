package com.lowaltitude.agent.service.query;

import com.lowaltitude.agent.entity.DataDimensionConfig;
import com.lowaltitude.agent.entity.DataSourceConfig;
import com.lowaltitude.agent.entity.DataTableConfig;
import com.lowaltitude.agent.entity.Indicator;
import com.lowaltitude.agent.repository.DataDimensionConfigRepository;
import com.lowaltitude.agent.repository.DataSourceConfigRepository;
import com.lowaltitude.agent.repository.DataTableConfigRepository;
import com.lowaltitude.agent.repository.IndicatorRepository;
import com.lowaltitude.agent.service.DynamicQueryService;
import com.lowaltitude.agent.service.dimension.LlmDimensionParser;
import com.lowaltitude.agent.service.retrieval.IndicatorMatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 数据查询编排服务 - 整合指标匹配、维度解析、SQL生成、执行
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataQueryOrchestrator {
    
    private final IndicatorMatchingService indicatorMatchingService;
    private final LlmDimensionParser llmDimensionParser;
    private final SqlGenerationService sqlGenerationService;
    private final DynamicQueryService dynamicQueryService;
    private final IndicatorRepository indicatorRepository;
    private final DataTableConfigRepository tableConfigRepository;
    private final DataSourceConfigRepository dataSourceConfigRepository;
    private final DataDimensionConfigRepository dimensionConfigRepository;
    
    /**
     * 查询结果
     */
    public record QueryResult(
            boolean success,
            String message,
            List<IndicatorMatchingService.MatchedIndicator> indicators,
            LlmDimensionParser.ParsedDimensions dimensions,
            SqlGenerationService.GeneratedSql generatedSql,
            String queryData,
            String formattedResult
    ) {}
    
    /**
     * 执行完整查询流程
     */
    public QueryResult executeQuery(String userQuery) {
        log.info("Executing query: {}", userQuery);
        
        try {
            // 1. 指标匹配（向量+BM25+同义词+LLM精排）
            List<IndicatorMatchingService.MatchedIndicator> indicators = 
                    indicatorMatchingService.matchIndicators(userQuery, 5);
            
            if (indicators.isEmpty()) {
                return new QueryResult(false, "未找到匹配的指标", null, null, null, null, null);
            }
            
            log.info("Matched {} indicators", indicators.size());
            
            // 2. 获取指标详情和表配置（并行）
            String primaryTableId = indicators.get(0).tableId();
            
            CompletableFuture<Optional<Indicator>> indicatorFuture = CompletableFuture.supplyAsync(() ->
                    indicatorRepository.findByIndicatorId(indicators.get(0).indicatorId()));
            
            CompletableFuture<Optional<DataTableConfig>> tableFuture = CompletableFuture.supplyAsync(() ->
                    tableConfigRepository.findById(primaryTableId));
            
            CompletableFuture<List<DataDimensionConfig>> dimsFuture = CompletableFuture.supplyAsync(() ->
                    dimensionConfigRepository.findByTableId(primaryTableId));
            
            // 等待并行查询完成
            CompletableFuture.allOf(indicatorFuture, tableFuture, dimsFuture).join();
            
            Indicator indicator = indicatorFuture.get().orElseThrow(() -> 
                    new RuntimeException("指标不存在: " + indicators.get(0).indicatorId()));
            
            DataTableConfig tableConfig = tableFuture.get().orElseThrow(() -> 
                    new RuntimeException("表配置不存在: " + primaryTableId));
            
            List<DataDimensionConfig> tableDimensions = dimsFuture.get();
            
            // 3. 维度解析（LLM直接抽取）
            LlmDimensionParser.ParsedDimensions dimensions = 
                    llmDimensionParser.parseDimensions(userQuery, indicator, tableDimensions);
            
            log.info("Parsed dimensions: region={}, time={} to {}, analysisType={}",
                    dimensions.region().regionName(),
                    dimensions.timeRange().start(),
                    dimensions.timeRange().end(),
                    dimensions.analysisType());
            
            // 4. 获取数据源配置
            DataSourceConfig dataSource = dataSourceConfigRepository
                    .findBySourceId(tableConfig.getSourceId())
                    .orElseThrow(() -> new RuntimeException("数据源不存在: " + tableConfig.getSourceId()));
            
            // 5. 生成SQL
            SqlGenerationService.GeneratedSql generatedSql = sqlGenerationService.generateSql(
                    indicators, dimensions, tableConfig, dataSource);
            
            log.info("Generated SQL for datasource: {}", dataSource.getSourceId());
            
            // 6. 执行查询
            String queryResult = dynamicQueryService.executeQueryAsString(dataSource, generatedSql.sql());
            
            // 7. 格式化结果（简化版，实际可调用ResultRenderer）
            String formattedResult = formatResult(indicators, dimensions, queryResult);
            
            return new QueryResult(
                    true,
                    "查询成功",
                    indicators,
                    dimensions,
                    generatedSql,
                    queryResult,
                    formattedResult
            );
            
        } catch (Exception e) {
            log.error("Query execution failed", e);
            return new QueryResult(false, "查询失败: " + e.getMessage(), null, null, null, null, null);
        }
    }
    
    /**
     * 格式化查询结果
     */
    private String formatResult(List<IndicatorMatchingService.MatchedIndicator> indicators,
                                 LlmDimensionParser.ParsedDimensions dimensions,
                                 String rawData) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== 查询结果 ===\n\n");
        sb.append("指标: ").append(indicators.stream()
                .map(IndicatorMatchingService.MatchedIndicator::indicatorName)
                .collect(Collectors.joining(", "))).append("\n");
        sb.append("地区: ").append(dimensions.region().regionName()).append("\n");
        sb.append("时间: ").append(dimensions.timeRange().start())
          .append(" 至 ").append(dimensions.timeRange().end()).append("\n");
        sb.append("分析类型: ").append(dimensions.analysisType()).append("\n\n");
        sb.append("数据:\n").append(rawData);
        
        return sb.toString();
    }
}
