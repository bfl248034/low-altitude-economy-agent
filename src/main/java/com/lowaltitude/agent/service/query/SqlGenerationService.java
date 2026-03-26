package com.lowaltitude.agent.service.query;

import com.lowaltitude.agent.entity.DataSourceConfig;
import com.lowaltitude.agent.entity.DataTableConfig;
import com.lowaltitude.agent.service.dimension.LlmDimensionParser;
import com.lowaltitude.agent.service.retrieval.IndicatorMatchingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SQL生成服务 - 基于指标和维度生成查询SQL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SqlGenerationService {
    
    /**
     * SQL生成结果
     */
    public record GeneratedSql(
            String sql,
            String tableId,
            String sourceId,
            String sourceType,
            List<String> indicatorIds,
            String explanation
    ) {}
    
    /**
     * 生成SQL - 支持多指标、多维度值
     */
    public GeneratedSql generateSql(
            List<IndicatorMatchingService.MatchedIndicator> indicators,
            LlmDimensionParser.ParsedDimensions dimensions,
            DataTableConfig tableConfig,
            DataSourceConfig dataSource) {
        
        log.info("Generating SQL for {} indicators on table {}", 
                indicators.size(), tableConfig.getTableId());
        
        StringBuilder sql = new StringBuilder();
        
        // 1. SELECT 子句
        sql.append("SELECT ");
        
        // 时间字段
        sql.append(tableConfig.getTimeColumn()).append(", ");
        
        // 地区字段
        sql.append(tableConfig.getRegionColumn()).append(", ");
        sql.append(tableConfig.getRegionLevelColumn()).append(", ");
        
        // 指标字段（数据表需要包含indicator_id来区分不同指标）
        if (tableConfig.getIndicatorColumn() != null) {
            sql.append(tableConfig.getIndicatorColumn()).append(", ");
        }
        
        // 其他维度字段
        for (LlmDimensionParser.DimensionValueInfo dim : dimensions.otherDimensions()) {
            sql.append(dim.columnName()).append(", ");
        }
        
        // 数值字段
        sql.append(tableConfig.getValueColumn());
        
        // 2. FROM 子句
        sql.append(" FROM ");
        if (tableConfig.getSchemaName() != null) {
            sql.append(tableConfig.getSchemaName()).append(".");
        }
        sql.append(tableConfig.getTableName());
        
        // 3. WHERE 子句
        List<String> conditions = new ArrayList<>();
        
        // 指标条件（多指标用IN）
        if (indicators.size() == 1) {
            conditions.add(tableConfig.getIndicatorColumn() + " = '" + 
                    indicators.get(0).indicatorId() + "'");
        } else {
            String indicatorList = indicators.stream()
                    .map(i -> "'" + i.indicatorId() + "'")
                    .collect(Collectors.joining(", "));
            conditions.add(tableConfig.getIndicatorColumn() + " IN (" + indicatorList + ")");
        }
        
        // 时间范围
        LlmDimensionParser.TimeRange timeRange = dimensions.timeRange();
        conditions.add(buildTimeCondition(timeRange, tableConfig.getTimeColumn(), dataSource.getSourceType()));
        
        // 地区条件
        LlmDimensionParser.RegionInfo region = dimensions.region();
        if (region.regionLevel() != null) {
            // 地区级别过滤
            if (region.regionLevel() == 1) {
                // 全国级，不过滤具体地区
            } else {
                if (region.regionCode() != null && !"0".equals(region.regionCode())) {
                    conditions.add(tableConfig.getRegionColumn() + " = '" + region.regionCode() + "'");
                }else {
                	conditions.add(tableConfig.getRegionLevelColumn() + " = " + region.regionLevel());
                }
            }
        }
        
        // 其他维度条件
        for (LlmDimensionParser.DimensionValueInfo dim : dimensions.otherDimensions()) {
            if (!dim.useDefault() || dim.isCrossSection()) {
                String condition = buildDimensionCondition(dim);
                if (condition != null) {
                    conditions.add(condition);
                }
            }
        }
        
        // 组装WHERE
        sql.append(" WHERE ");
        sql.append(String.join(" AND ", conditions));
        
        // 4. ORDER BY
        sql.append(" ORDER BY ").append(tableConfig.getTimeColumn()).append(" DESC");
        
        String finalSql = sql.toString();
        log.info("Generated SQL: {}", finalSql);
        
        return new GeneratedSql(
                finalSql,
                tableConfig.getTableId(),
                dataSource.getSourceId(),
                dataSource.getSourceType(),
                indicators.stream().map(IndicatorMatchingService.MatchedIndicator::indicatorId).toList(),
                generateExplanation(indicators, dimensions)
        );
    }
    
    /**
     * 构建时间条件
     */
    private String buildTimeCondition(LlmDimensionParser.TimeRange timeRange, 
                                       String timeColumn, String sourceType) {
        // 根据数据源类型适配时间格式
        String start = timeRange.start();
        String end = timeRange.end();
        
        // 如果是yyyy-MM-dd格式，转换为yyyyMM（按月表）
        if (start.length() == 10) {
            start = start.replace("-", "").substring(0, 6);
        }
        if (end.length() == 10) {
            end = end.replace("-", "").substring(0, 6);
        }
        
        return String.format("%s BETWEEN '%s' AND '%s'", timeColumn, start, end);
    }
    
    /**
     * 构建维度条件
     */
    private String buildDimensionCondition(LlmDimensionParser.DimensionValueInfo dim) {
        if (dim.values() == null || dim.values().isEmpty()) {
            return null;
        }
        
        // 截面分析：排除默认值
        if (dim.isCrossSection()) {
            String defaultValue = dim.values().get(0); // 假设第一个是默认值
            return dim.columnName() + " != '" + defaultValue + "'";
        }
        
        // 多值用IN
        if (dim.values().size() == 1) {
            return dim.columnName() + " = '" + dim.values().get(0) + "'";
        } else {
            String values = dim.values().stream()
                    .map(v -> "'" + v + "'")
                    .collect(Collectors.joining(", "));
            return dim.columnName() + " IN (" + values + ")";
        }
    }
    
    /**
     * 生成SQL说明
     */
    private String generateExplanation(List<IndicatorMatchingService.MatchedIndicator> indicators,
                                        LlmDimensionParser.ParsedDimensions dimensions) {
        StringBuilder sb = new StringBuilder();
        sb.append("查询指标: ").append(indicators.stream()
                .map(IndicatorMatchingService.MatchedIndicator::indicatorName)
                .collect(Collectors.joining(", ")));
        sb.append("\n时间范围: ").append(dimensions.timeRange().start())
          .append(" 至 ").append(dimensions.timeRange().end());
        sb.append("\n地区: ").append(dimensions.region().regionName());
        sb.append("\n分析类型: ").append(dimensions.analysisType());
        return sb.toString();
    }
}
