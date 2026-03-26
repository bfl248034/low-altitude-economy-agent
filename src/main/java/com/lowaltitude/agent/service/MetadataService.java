package com.lowaltitude.agent.service;

import com.lowaltitude.agent.entity.*;
import com.lowaltitude.agent.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataService {

    private final IndicatorRepository indicatorRepository;
    private final DimensionValueRepository dimensionValueRepository;
    private final DataSourceConfigRepository dataSourceConfigRepository;
    private final DataTableConfigRepository dataTableConfigRepository;
    private final DataDimensionConfigRepository dataDimensionConfigRepository;
    private final LatestTimeConfigRepository latestTimeConfigRepository;

    // ==================== 指标查询 ====================

    public List<Indicator> searchIndicators(String keyword) {
        return indicatorRepository.searchByKeyword(keyword);
    }

    public Optional<Indicator> getIndicatorById(String indicatorId) {
        return indicatorRepository.findByIndicatorId(indicatorId);
    }

    public List<Indicator> getIndicatorsByTable(String tableId) {
        return indicatorRepository.findByTableId(tableId);
    }

    // ==================== 维度值查询 ====================

    public List<DimensionValue> getDimensionValues(String dimensionId) {
        return dimensionValueRepository.findByDimensionId(dimensionId);
    }

    public Optional<DimensionValue> findRegionByName(String name) {
        return dimensionValueRepository.findRegionByName(name);
    }

    public Optional<DimensionValue> findEduLevelByName(String name) {
        return dimensionValueRepository.findEduLevelByName(name);
    }

    public String getDimensionValueName(String dimensionId, String valueCode) {
        return dimensionValueRepository.findByDimensionIdAndValueCode(dimensionId, valueCode)
                .map(DimensionValue::getValueName)
                .orElse(valueCode);
    }

    // ==================== 最新时间查询 ====================

    public Optional<LatestTimeConfig> getLatestTime(String indicatorId) {
        return latestTimeConfigRepository.findByIndicatorId(indicatorId);
    }

    // ==================== 数据源查询 ====================

    public Optional<DataSourceConfig> getDataSource(String sourceId) {
        return dataSourceConfigRepository.findBySourceId(sourceId);
    }

    public List<DataSourceConfig> getActiveDataSources() {
        return dataSourceConfigRepository.findByIsActiveTrue();
    }

    // ==================== 数据表查询 ====================

    public Optional<DataTableConfig> getDataTable(String tableId) {
        return dataTableConfigRepository.findByTableId(tableId);
    }

    public List<DataTableConfig> getTablesBySource(String sourceId) {
        return dataTableConfigRepository.findBySourceId(sourceId);
    }

    // ==================== 维度配置查询 ====================

    public List<DataDimensionConfig> getTableDimensions(String tableId) {
        return dataDimensionConfigRepository.findByTableId(tableId);
    }

    /**
     * 获取表的维度配置（包含默认值）
     * 从 db_data_dimension 表中获取 default_value 字段
     */
    public List<DimensionConfigWithValues> getTableDimensionConfigs(String tableId, boolean excludeTimeRegion) {
        List<DataDimensionConfig> dimensions = dataDimensionConfigRepository.findByTableId(tableId);

        if (excludeTimeRegion) {
            dimensions = dimensions.stream()
                    .filter(d -> !"time".equals(d.getDimensionId()) && !"region".equals(d.getDimensionId()))
                    .toList();
        }

        return dimensions.stream()
                .map(config -> {
                    List<DimensionValue> values = dimensionValueRepository.findByDimensionId(config.getDimensionId());
                    return new DimensionConfigWithValues(config, values);
                })
                .toList();
    }

    /**
     * 维度配置及其值
     */
    public record DimensionConfigWithValues(
            DataDimensionConfig config,
            List<DimensionValue> values
    ) {
        public String getDimensionId() {
            return config.getDimensionId();
        }

        public String getDimensionName() {
            return config.getDimensionName();
        }

        public String getColumnName() {
            return config.getDimensionCode();
        }

        /**
         * 获取默认值编码（来自 db_data_dimension.default_value）
         */
        public String getDefaultValue() {
            return config.getDefaultValue();
        }

        /**
         * 获取默认值名称
         */
        public String getDefaultValueName() {
            String defaultCode = config.getDefaultValue();
            if (defaultCode == null) return null;
            return values.stream()
                    .filter(v -> defaultCode.equals(v.getValueCode()))
                    .findFirst()
                    .map(DimensionValue::getValueName)
                    .orElse(defaultCode);
        }
    }

    // ==================== 维度值查询（按表） ====================

    public List<DimensionValue> getDimensionValuesByTable(String tableId, boolean excludeTimeRegion) {
        List<DataDimensionConfig> dimensions = dataDimensionConfigRepository.findByTableId(tableId);

        if (excludeTimeRegion) {
            dimensions = dimensions.stream()
                    .filter(d -> !"time".equals(d.getDimensionId()) && !"region".equals(d.getDimensionId()))
                    .toList();
        }

        // 获取所有维度值
        return dimensions.stream()
                .flatMap(d -> dimensionValueRepository.findByDimensionId(d.getDimensionId()).stream())
                .toList();
    }

    // ==================== 时间解析 ====================

    public TimeRange parseTimeExpression(String expression) {
        LocalDate now = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMM");

        if (expression.contains("近") || expression.contains("最近")) {
            int months = extractNumber(expression);
            if (months > 0) {
                LocalDate start = now.minusMonths(months);
                return new TimeRange(start.format(formatter), now.format(formatter));
            }
        }

        // 默认最近6个月
        LocalDate start = now.minusMonths(6);
        return new TimeRange(start.format(formatter), now.format(formatter));
    }

    private int extractNumber(String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return 6; // 默认
    }

    public record TimeRange(String start, String end) {}
}
