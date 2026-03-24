package com.lowaltitude.agent.tool;

import com.lowaltitude.agent.entity.DataSourceConfig;
import com.lowaltitude.agent.service.DynamicQueryService;
import com.lowaltitude.agent.service.MetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SQL构建和执行工具
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlExecutionTool {
    
    private final DynamicQueryService dynamicQueryService;
    private final MetadataService metadataService;
    
    /**
     * 构建SQL语句
     */
    @Tool(name = "buildSql", description = "根据指标、维度和表结构构建SQL语句")
    public Map<String, Object> buildSql(
            @ToolParam(description = "指标列表JSON") String indicatorsJson,
            @ToolParam(description = "维度信息JSON") String dimensionsJson,
            @ToolParam(description = "表结构JSON") String schemaJson,
            @ToolParam(description = "数据源类型：h2/mysql/kylin") String sourceType) {
        
        log.info("Building SQL for sourceType: {}", sourceType);
        
        // 简化实现：直接返回提示，实际由Service层处理
        return Map.of(
            "sql", "-- SQL将由DataQueryService构建",
            "sourceType", sourceType,
            "status", "pending"
        );
    }
    
    /**
     * 在数据源执行SQL
     */
    @Tool(name = "executeOnDataSource", description = "在指定数据源执行SQL查询")
    public Map<String, Object> executeOnDataSource(
            @ToolParam(description = "数据源ID") String sourceId,
            @ToolParam(description = "SQL语句") String sql) {
        
        log.info("Executing SQL on datasource: {}", sourceId);
        
        try {
            DataSourceConfig config = metadataService.getDataSource(sourceId)
                    .orElseThrow(() -> new RuntimeException("数据源不存在: " + sourceId));
            
            List<Map<String, Object>> results = dynamicQueryService.executeQuery(config, sql);
            
            return Map.of(
                "success", true,
                "data", results,
                "rowCount", results.size()
            );
            
        } catch (Exception e) {
            log.error("Query execution failed", e);
            return Map.of(
                "success", false,
                "error", e.getMessage()
            );
        }
    }
    
    /**
     * 格式化数值
     */
    @Tool(name = "formatNumbers", description = "格式化数值，添加千分位和单位")
    public String formatNumbers(
            @ToolParam(description = "数值") double value,
            @ToolParam(description = "单位：元/个/家等") String unit) {
        
        NumberFormat formatter = NumberFormat.getInstance(Locale.CHINA);
        
        // 大于1万用"万"表示
        if (value >= 10000 && value < 100000000) {
            return String.format("%.2f万%s", value / 10000, unit);
        }
        // 大于1亿用"亿"表示
        if (value >= 100000000) {
            return String.format("%.2f亿%s", value / 100000000, unit);
        }
        
        return formatter.format(value) + unit;
    }
    
    /**
     * 生成结果摘要
     */
    @Tool(name = "generateSummary", description = "根据查询结果生成文本摘要")
    public String generateSummary(
            @ToolParam(description = "结果数据JSON") String dataJson,
            @ToolParam(description = "分析类型：TREND/RANKING/COMPARISON/CROSS_SECTION") String analysisType,
            @ToolParam(description = "指标名称") String indicatorName) {
        
        // 简化实现
        return switch (analysisType) {
            case "TREND" -> indicatorName + "趋势分析完成";
            case "RANKING" -> indicatorName + "排名分析完成";
            case "COMPARISON" -> indicatorName + "对比分析完成";
            case "CROSS_SECTION" -> indicatorName + "截面分析完成";
            default -> indicatorName + "查询完成";
        };
    }
    
    /**
     * 推荐相关问题
     */
    @Tool(name = "suggestRelatedQueries", description = "根据当前查询推荐相关问题")
    public List<String> suggestRelatedQueries(
            @ToolParam(description = "指标ID") String indicatorId,
            @ToolParam(description = "维度ID") String dimensionId) {
        
        return List.of(
            "其他" + dimensionId + "的数据对比",
            "该指标的趋势变化",
            "与其他指标的关联分析"
        );
    }
}
