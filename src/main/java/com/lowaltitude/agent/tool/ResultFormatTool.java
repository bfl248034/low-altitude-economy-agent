package com.lowaltitude.agent.tool;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 结果格式化工具 - 处理结果展示
 * 包含：数值格式化、摘要生成、推荐问题
 */
@Slf4j
@Component
public class ResultFormatTool {

    /**
     * 格式化数值，添加千分位和单位
     */
    @Tool(name = "formatNumber", description = "格式化数值，大数转万/亿，加千分位和单位")
    public String formatNumber(
            @ToolParam(description = "数值") double value,
            @ToolParam(description = "单位：元/个/家等") String unit) {
        
        NumberFormat formatter = NumberFormat.getInstance(Locale.CHINA);
        
        if (value >= 10000 && value < 100000000) {
            return String.format("%.2f万%s", value / 10000, unit);
        }
        if (value >= 100000000) {
            return String.format("%.2f亿%s", value / 100000000, unit);
        }
        return formatter.format(value) + unit;
    }

    /**
     * 生成数据摘要
     */
    @Tool(name = "generateSummary", description = "根据查询结果生成文本摘要")
    public String generateSummary(
            @ToolParam(description = "结果数据JSON") String dataJson,
            @ToolParam(description = "分析类型：TREND/RANKING/COMPARISON/CROSS_SECTION") String analysisType,
            @ToolParam(description = "指标名称") String indicatorName) {
        
        return switch (analysisType) {
            case "TREND" -> indicatorName + "趋势分析：整体变化情况";
            case "RANKING" -> indicatorName + "排名分析：TOP地区情况";
            case "COMPARISON" -> indicatorName + "对比分析：各维度差异";
            case "CROSS_SECTION" -> indicatorName + "分布分析：各维度占比";
            default -> indicatorName + "查询完成";
        };
    }

    /**
     * 推荐相关问题
     */
    @Tool(name = "suggestQueries", description = "根据当前查询推荐相关问题")
    public List<String> suggestQueries(
            @ToolParam(description = "指标ID") String indicatorId,
            @ToolParam(description = "维度ID") String dimensionId) {
        
        return List.of(
            "其他" + dimensionId + "的数据对比",
            "该指标的趋势变化",
            "与其他指标的关联分析"
        );
    }
}
