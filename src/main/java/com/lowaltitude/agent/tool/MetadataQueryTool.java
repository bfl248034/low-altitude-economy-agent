package com.lowaltitude.agent.tool;

import com.lowaltitude.agent.entity.DataSourceConfig;
import com.lowaltitude.agent.entity.DataTableConfig;
import com.lowaltitude.agent.entity.DimensionValue;
import com.lowaltitude.agent.entity.Indicator;
import com.lowaltitude.agent.entity.LatestTimeConfig;
import com.lowaltitude.agent.service.MetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 元数据查询工具 - 查询指标、维度、表结构等元数据
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetadataQueryTool {
    
    private final MetadataService metadataService;
    
    /**
     * 获取指标完整元数据
     */
    @Tool(name = "getIndicatorMeta", description = "根据指标ID获取完整元数据信息")
    public Map<String, Object> getIndicatorMeta(
            @ToolParam(description = "指标ID，如I_RPA_ICN_RAE_SALARY_AMOUNT") String indicatorId) {
        
        log.info("Getting indicator meta for: {}", indicatorId);
        
        Optional<Indicator> indicator = metadataService.getIndicatorById(indicatorId);
        
        if (indicator.isEmpty()) {
            return Map.of("error", "指标不存在: " + indicatorId);
        }
        
        Indicator ind = indicator.get();
        Map<String, Object> result = new HashMap<>();
        result.put("indicatorId", ind.getIndicatorId());
        result.put("indicatorName", ind.getIndicatorName());
        result.put("unit", ind.getUnit());
        result.put("frequency", ind.getFrequency());
        result.put("tableId", ind.getTableId());
        result.put("tags", ind.getTags());
        result.put("remark", ind.getRemark());
        
        return result;
    }
    
    /**
     * 获取指标最新时间配置
     */
    @Tool(name = "getLatestTime", description = "获取指标的最新数据时间配置")
    public Map<String, Object> getLatestTime(
            @ToolParam(description = "指标ID") String indicatorId) {
        
        log.info("Getting latest time for indicator: {}", indicatorId);
        
        Optional<LatestTimeConfig> config = metadataService.getLatestTime(indicatorId);
        
        if (config.isEmpty()) {
            // 返回默认值
            return Map.of(
                "indicatorId", indicatorId,
                "frequency", "M",
                "latestTimeId", "202406",
                "latestDate", "2024-06-30"
            );
        }
        
        LatestTimeConfig cfg = config.get();
        Map<String, Object> result = new HashMap<>();
        result.put("indicatorId", cfg.getIndicatorId());
        result.put("frequency", cfg.getFrequency());
        result.put("latestTimeId", cfg.getLatestTimeId());
        result.put("latestDate", cfg.getLatestDate());
        
        return result;
    }
    
    /**
     * 获取表的维度值列表（不包含时间和地区）
     */
    @Tool(name = "getDimensionValues", description = "获取数据表的维度值列表，可排除时间和地区维度")
    public List<Map<String, Object>> getDimensionValues(
            @ToolParam(description = "表ID") String tableId,
            @ToolParam(description = "是否排除时间和地区维度") boolean excludeTimeRegion) {
        
        log.info("Getting dimension values for table: {}, excludeTimeRegion: {}", 
                tableId, excludeTimeRegion);
        
        List<DimensionValue> values = metadataService.getDimensionValuesByTable(tableId, excludeTimeRegion);
        
        return values.stream().map(v -> {
            Map<String, Object> map = new HashMap<>();
            map.put("dimensionId", v.getDimensionId());
            map.put("dimensionName", v.getDimensionName());
            map.put("valueCode", v.getValueCode());
            map.put("valueName", v.getValueName());
            map.put("synonyms", v.getSynonyms());
//            map.put("isDefault", v.getIsDefault());
            return map;
        }).collect(Collectors.toList());
    }
    
    /**
     * 获取表结构信息
     */
    @Tool(name = "getTableSchema", description = "获取数据表的完整结构信息")
    public Map<String, Object> getTableSchema(
            @ToolParam(description = "表ID") String tableId) {
        
        log.info("Getting table schema for: {}", tableId);
        
        Optional<DataTableConfig> table = metadataService.getDataTable(tableId);
        
        if (table.isEmpty()) {
            return Map.of("error", "表不存在: " + tableId);
        }
        
        DataTableConfig t = table.get();
        Map<String, Object> result = new HashMap<>();
        result.put("tableId", t.getTableId());
        result.put("tableName", t.getTableName());
        result.put("sourceId", t.getSourceId());
        result.put("timeColumn", t.getTimeColumn());
        result.put("regionColumn", t.getRegionColumn());
        result.put("regionLevelColumn", t.getRegionLevelColumn());
        result.put("valueColumn", t.getValueColumn());
        result.put("indicatorColumn", t.getIndicatorColumn());
        
        return result;
    }
    
    /**
     * 获取数据源配置
     */
    @Tool(name = "getDataSource", description = "获取数据源配置信息")
    public Map<String, Object> getDataSource(
            @ToolParam(description = "数据源ID") String sourceId) {
        
        log.info("Getting data source: {}", sourceId);
        
        Optional<DataSourceConfig> source = metadataService.getDataSource(sourceId);
        
        if (source.isEmpty()) {
            return Map.of("error", "数据源不存在: " + sourceId);
        }
        
        DataSourceConfig s = source.get();
        Map<String, Object> result = new HashMap<>();
        result.put("sourceId", s.getSourceId());
        result.put("sourceName", s.getSourceName());
        result.put("sourceType", s.getSourceType());
        result.put("host", s.getHost());
        result.put("port", s.getPort());
        result.put("databaseName", s.getDatabaseName());
        
        return result;
    }
    
    /**
     * 翻译维度编码为中文名称
     */
    @Tool(name = "translateCodes", description = "将维度编码翻译为中文名称")
    public String translateCodes(
            @ToolParam(description = "维度ID，如region、edu_level") String dimensionId,
            @ToolParam(description = "编码值，如110000、3") String valueCode) {
        
        return metadataService.getDimensionValueName(dimensionId, valueCode);
    }
}
