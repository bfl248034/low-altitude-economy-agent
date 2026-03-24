package com.lowaltitude.agent.tool;

import com.lowaltitude.agent.entity.DataSourceConfig;
import com.lowaltitude.agent.entity.DataTableConfig;
import com.lowaltitude.agent.entity.DimensionValue;
import com.lowaltitude.agent.entity.Indicator;
import com.lowaltitude.agent.service.MetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class MetadataQueryTool {

    private final MetadataService metadataService;

    @Tool(name = "searchIndicators", description = "根据关键词模糊搜索指标")
    public String searchIndicators(
            @ToolParam(description = "搜索关键词，如'薪资'、'企业'、'招聘'") String keyword) {
        log.info("Searching indicators with keyword: {}", keyword);
        List<Indicator> indicators = metadataService.searchIndicators(keyword);
        
        if (indicators.isEmpty()) {
            return "未找到匹配的指标";
        }
        
        return indicators.stream()
                .map(i -> String.format("指标ID: %s, 名称: %s, 单位: %s, 表ID: %s",
                        i.getIndicatorId(), i.getIndicatorName(), i.getUnit(), i.getTableId()))
                .collect(Collectors.joining("\n"));
    }

    @Tool(name = "findRegionByName", description = "根据名称匹配地区编码")
    public String findRegionByName(
            @ToolParam(description = "地区名称，如'北京'、'上海'") String name) {
        Optional<DimensionValue> region = metadataService.findRegionByName(name);
        return region.map(r -> String.format("地区编码: %s, 名称: %s", r.getValueCode(), r.getValueName()))
                .orElse("未找到匹配的地区");
    }

    @Tool(name = "findEduLevelByName", description = "根据名称匹配学历编码")
    public String findEduLevelByName(
            @ToolParam(description = "学历名称，如'本科'、'硕士'") String name) {
        Optional<DimensionValue> edu = metadataService.findEduLevelByName(name);
        return edu.map(e -> String.format("学历编码: %s, 名称: %s", e.getValueCode(), e.getValueName()))
                .orElse("未找到匹配的学历");
    }

    @Tool(name = "getDataTable", description = "根据表ID获取数据表配置信息")
    public String getDataTable(
            @ToolParam(description = "表ID，如'ads_rpa_w_icn_recruit_salary_amount_m'") String tableId) {
        Optional<DataTableConfig> table = metadataService.getDataTable(tableId);
        return table.map(t -> String.format(
                "表ID: %s, 表名: %s, 数据源: %s, 时间列: %s, 地区列: %s, 数值列: %s",
                t.getTableId(), t.getTableName(), t.getSourceId(),
                t.getTimeColumn(), t.getRegionColumn(), t.getValueColumn()))
                .orElse("未找到表配置");
    }

    @Tool(name = "getDataSource", description = "根据数据源ID获取数据源配置")
    public String getDataSource(
            @ToolParam(description = "数据源ID，如'ds_h2_demo'") String sourceId) {
        Optional<DataSourceConfig> ds = metadataService.getDataSource(sourceId);
        return ds.map(d -> String.format(
                "数据源ID: %s, 名称: %s, 类型: %s, 主机: %s, 端口: %d, 数据库: %s",
                d.getSourceId(), d.getSourceName(), d.getSourceType(),
                d.getHost(), d.getPort(), d.getDatabaseName()))
                .orElse("未找到数据源配置");
    }

    @Tool(name = "getDimensionValueName", description = "根据维度ID和编码获取中文名称")
    public String getDimensionValueName(
            @ToolParam(description = "维度ID，如'region'、'edu_level'") String dimensionId,
            @ToolParam(description = "编码值，如'110000'") String valueCode) {
        String name = metadataService.getDimensionValueName(dimensionId, valueCode);
        return String.format("%s: %s", valueCode, name);
    }
}
