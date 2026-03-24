package com.lowaltitude.agent.service.dimension;

import com.lowaltitude.agent.entity.*;
import com.lowaltitude.agent.repository.DimensionValueRepository;
import com.lowaltitude.agent.repository.LatestTimeConfigRepository;
import com.lowaltitude.agent.service.retrieval.SynonymService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LLM维度解析服务 - 直接使用LLM抽取维度值
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmDimensionParser {
    
    private final ChatModel chatModel;
    private final LatestTimeConfigRepository latestTimeConfigRepository;
    private final DimensionValueRepository dimensionValueRepository;
    private final SynonymService synonymService;
    
    /**
     * 解析后的维度结果
     */
    public record ParsedDimensions(
            TimeRange timeRange,
            RegionInfo region,
            List<DimensionValueInfo> otherDimensions,
            AnalysisType analysisType,
            String reasoning
    ) {
        public enum AnalysisType {
            SINGLE,      // 单值查询
            TREND,       // 趋势分析
            RANKING,     // 排名分析
            COMPARISON,  // 对比分析
            CROSS_SECTION // 截面分析
        }
    }
    
    public record TimeRange(String start, String end) {}
    
    public record RegionInfo(
            String regionCode,
            String regionName,
            Integer regionLevel  // 1:全国 2:省级 3:市级 4:区县
    ) {}
    
    public record DimensionValueInfo(
            String dimensionId,
            String dimensionName,
            String columnName,
            List<String> values,
            List<String> valueNames,
            boolean useDefault,
            boolean isCrossSection  // 是否截面分析（排除默认值）
    ) {}
    
    /**
     * 解析维度 - 核心方法
     * 
     * @param query 用户原始查询
     * @param indicator 已匹配的指标信息
     * @param tableDimensions 该指标表配置的维度（从db_data_dimension获取）
     */
    public ParsedDimensions parseDimensions(String query, 
                                            Indicator indicator,
                                            List<DataDimensionConfig> tableDimensions) {
        
        log.info("Parsing dimensions for query: {}, indicator: {}", query, indicator.getIndicatorId());
        
        // 1. 获取指标最新时间
        LatestTimeConfig timeConfig = latestTimeConfigRepository
                .findByIndicatorId(indicator.getIndicatorId())
                .orElseGet(() -> createDefaultTimeConfig(indicator));
        
        // 2. 构建维度值上下文（不包含地区和时间）
        String dimensionContext = buildDimensionContext(tableDimensions);
        
        // 3. 构建LLM提示
        String prompt = buildPrompt(query, indicator, timeConfig, dimensionContext);
        
        // 4. 调用LLM解析
        String llmResponse = callLlm(prompt);
        log.info("LLM dimension parse response: {}", llmResponse);
        
        // 5. 解析LLM响应
        return parseLlmResponse(llmResponse, tableDimensions, timeConfig);
    }
    
    /**
     * 构建维度值上下文
     */
    private String buildDimensionContext(List<DataDimensionConfig> dimensions) {
        StringBuilder sb = new StringBuilder();
        
        for (DataDimensionConfig dim : dimensions) {
            // 跳过地区和时间维度
            if ("region".equals(dim.getDimensionId()) || "time".equals(dim.getDimensionId())) {
                continue;
            }
            
            sb.append("\n维度: ").append(dim.getDimensionName())
              .append(" (字段名: ").append(dim.getDimensionCode()).append(")\n");
            
            // 获取该维度的所有值
            List<DimensionValue> values = synonymService.getDimensionValues(dim.getDimensionId());
            sb.append("可选值:\n");
            for (DimensionValue val : values) {
                sb.append("  - ").append(val.getValueCode()).append(": ").append(val.getValueName());
                if (val.getSynonyms() != null && !val.getSynonyms().isEmpty()) {
                    sb.append(" (同义词: ").append(val.getSynonyms()).append(")");
                }
                if (dim.getDefaultValue() != null && dim.getDefaultValue().equals(val.getValueCode())) {
                    sb.append(" [默认值]");
                }
                sb.append("\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 构建LLM提示
     */
    private String buildPrompt(String query, Indicator indicator, 
                               LatestTimeConfig timeConfig, String dimensionContext) {
        
        // 计算时间范围示例
        String timeExample = calculateTimeExample(timeConfig, indicator.getFrequency());
        
        return """
                作为数据查询分析助手，请从用户查询中提取维度条件。
                
                ## 用户查询
                {query}
                
                ## 指标信息
                - 指标名称: {indicatorName}
                - 指标ID: {indicatorId}
                - 频率: {frequency}
                - 数据表: {tableId}
                
                ## 最新数据时间
                - 最新时间ID: {latestTimeId}
                - 最新日期: {latestDate}
                - 频率: {frequency}
                
                ## 其他维度配置{dimensionContext}
                
                ## 任务
                1. **地区解析**：将用户提到的地区（如"北京"、"各省"、"全国"）解析为地区编码和级别
                   - 全国级(region_level=1): 全国
                   - 省级(region_level=2): 北京(110000)、上海(310000)等
                   - 市级(region_level=3): 广州(440100)、深圳(440300)等
                
                2. **时间解析**：基于频率和最新时间，计算查询时间范围
                   - 频率说明: D=日, W=周, M=月, Q=季, Y=年
                   - 时间范围格式: yyyy-MM-dd
                   - 示例: {timeExample}
                
                3. **其他维度解析**：从维度配置中匹配用户提到的维度值
                   - 支持多值（如"本科和硕士"）
                   - 未提到的维度使用默认值
                   - 截面分析时排除默认值
                
                4. **分析类型识别**：
                   - SINGLE: 查询具体值
                   - TREND: 包含"趋势"、"走势"
                   - RANKING: 包含"排名"、"排行"
                   - COMPARISON: 包含"对比"、"比较"
                   - CROSS_SECTION: 包含"不同"、"分"、"各"（如"不同学历"、"分省份"）
                
                ## 输出格式（严格JSON）
                {
                  "region": {
                    "code": "110000",
                    "name": "北京",
                    "level": 2
                  },
                  "timeRange": {
                    "start": "2024-01-01",
                    "end": "2024-06-30"
                  },
                  "dimensions": [
                    {
                      "dimensionId": "edu_level",
                      "columnName": "edu_level",
                      "values": ["3", "4"],
                      "valueNames": ["本科", "硕士"],
                      "useDefault": false,
                      "isCrossSection": false
                    }
                  ],
                  "analysisType": "TREND",
                  "reasoning": "解析理由..."
                }
                """
                .replace("{query}", query)
                .replace("{indicatorName}", indicator.getIndicatorName())
                .replace("{indicatorId}", indicator.getIndicatorId())
                .replace("{frequency}", indicator.getFrequency())
                .replace("{tableId}", indicator.getTableId())
                .replace("{latestTimeId}", timeConfig.getLatestTimeId())
                .replace("{latestDate}", timeConfig.getLatestDate())
                .replace("{dimensionContext}", dimensionContext)
                .replace("{timeExample}", timeExample);
    }
    
    /**
     * 计算时间范围示例
     */
    private String calculateTimeExample(LatestTimeConfig config, String frequency) {
        LocalDate latest = LocalDate.parse(config.getLatestDate());
        
        return switch (frequency.toUpperCase()) {
            case "D" -> {
                LocalDate start = latest.minusDays(30);
                yield String.format("近30天: %s 到 %s", start, latest);
            }
            case "W" -> {
                LocalDate start = latest.minusWeeks(12);
                yield String.format("近12周: %s 到 %s", start, latest);
            }
            case "M" -> {
                LocalDate start = latest.minusMonths(6).withDayOfMonth(1);
                yield String.format("近6个月: %s 到 %s", start, 
                        latest.with(TemporalAdjusters.lastDayOfMonth()));
            }
            case "Q" -> {
                LocalDate start = latest.minusMonths(4);
                yield String.format("近4个季度: %s 到 %s", start, latest);
            }
            case "Y" -> {
                LocalDate start = latest.minusYears(3).withDayOfYear(1);
                yield String.format("近3年: %s 到 %s", start, 
                        latest.with(TemporalAdjusters.lastDayOfYear()));
            }
            default -> String.format("默认: %s 到 %s", latest.minusMonths(6), latest);
        };
    }
    
    /**
     * 调用LLM
     */
    private String callLlm(String prompt) {
        Prompt p = new Prompt(prompt);
        return chatModel.call(p).getResult().getOutput().getText();
    }
    
    /**
     * 解析LLM响应
     */
    private ParsedDimensions parseLlmResponse(String response, 
                                               List<DataDimensionConfig> tableDimensions,
                                               LatestTimeConfig timeConfig) {
        try {
            // 简化解析：直接从文本中提取关键信息
            // 实际生产环境建议用JSON解析器
            
            // 提取地区
            RegionInfo region = extractRegion(response);
            
            // 提取时间范围
            TimeRange timeRange = extractTimeRange(response, timeConfig);
            
            // 提取其他维度
            List<DimensionValueInfo> otherDims = extractOtherDimensions(response, tableDimensions);
            
            // 提取分析类型
            ParsedDimensions.AnalysisType analysisType = extractAnalysisType(response);
            
            return new ParsedDimensions(timeRange, region, otherDims, analysisType, 
                    "LLM解析完成");
            
        } catch (Exception e) {
            log.error("Parse LLM response failed, using defaults", e);
            // 降级：使用默认值
            return buildDefaultDimensions(tableDimensions, timeConfig);
        }
    }
    
    // 简化提取方法（实际应完善JSON解析）
    private RegionInfo extractRegion(String response) {
        // 默认全国
        if (response.contains("\"code\": \"110000\"")) {
            return new RegionInfo("110000", "北京", 2);
        } else if (response.contains("全国") || response.contains("\"level\": 1")) {
            return new RegionInfo("0", "全国", 1);
        }
        return new RegionInfo("0", "全国", 1);
    }
    
    private TimeRange extractTimeRange(String response, LatestTimeConfig timeConfig) {
        // 从响应中提取或基于最新时间计算
        LocalDate latest = LocalDate.parse(timeConfig.getLatestDate());
        LocalDate start = latest.minusMonths(6);
        return new TimeRange(start.toString(), latest.toString());
    }
    
    private List<DimensionValueInfo> extractOtherDimensions(String response, 
                                                            List<DataDimensionConfig> tableDimensions) {
        List<DimensionValueInfo> result = new ArrayList<>();
        
        for (DataDimensionConfig dim : tableDimensions) {
            if ("region".equals(dim.getDimensionId()) || "time".equals(dim.getDimensionId())) {
                continue;
            }
            
            // 检查是否提到该维度
            boolean mentioned = response.toLowerCase().contains(dim.getDimensionName());
            
            if (mentioned) {
                // 从响应中提取值（简化实现）
                result.add(new DimensionValueInfo(
                        dim.getDimensionId(),
                        dim.getDimensionName(),
                        dim.getDimensionCode(),
                        List.of(), // 实际应从JSON解析
                        List.of(),
                        false,
                        response.contains("截面") || response.contains("不同")
                ));
            } else {
                // 使用默认值
                result.add(new DimensionValueInfo(
                        dim.getDimensionId(),
                        dim.getDimensionName(),
                        dim.getDimensionCode(),
                        List.of(dim.getDefaultValue() != null ? dim.getDefaultValue() : "0"),
                        List.of("全部"),
                        true,
                        false
                ));
            }
        }
        
        return result;
    }
    
    private ParsedDimensions.AnalysisType extractAnalysisType(String response) {
        if (response.contains("CROSS_SECTION") || response.contains("截面")) {
            return ParsedDimensions.AnalysisType.CROSS_SECTION;
        } else if (response.contains("TREND") || response.contains("趋势")) {
            return ParsedDimensions.AnalysisType.TREND;
        } else if (response.contains("RANKING") || response.contains("排名")) {
            return ParsedDimensions.AnalysisType.RANKING;
        } else if (response.contains("COMPARISON") || response.contains("对比")) {
            return ParsedDimensions.AnalysisType.COMPARISON;
        }
        return ParsedDimensions.AnalysisType.SINGLE;
    }
    
    private ParsedDimensions buildDefaultDimensions(List<DataDimensionConfig> tableDimensions,
                                                     LatestTimeConfig timeConfig) {
        LocalDate latest = LocalDate.parse(timeConfig.getLatestDate());
        
        List<DimensionValueInfo> defaults = tableDimensions.stream()
                .filter(d -> !"region".equals(d.getDimensionId()) && !"time".equals(d.getDimensionId()))
                .map(d -> new DimensionValueInfo(
                        d.getDimensionId(), d.getDimensionName(), d.getDimensionCode(),
                        List.of(d.getDefaultValue() != null ? d.getDefaultValue() : "0"),
                        List.of("全部"), true, false
                ))
                .collect(Collectors.toList());
        
        return new ParsedDimensions(
                new TimeRange(latest.minusMonths(6).toString(), latest.toString()),
                new RegionInfo("0", "全国", 1),
                defaults,
                ParsedDimensions.AnalysisType.SINGLE,
                "使用默认维度"
        );
    }
    
    private LatestTimeConfig createDefaultTimeConfig(Indicator indicator) {
        LatestTimeConfig config = new LatestTimeConfig();
        config.setIndicatorId(indicator.getIndicatorId());
        config.setTableId(indicator.getTableId());
        config.setFrequency(indicator.getFrequency());
        config.setLatestTimeId("202406");
        config.setLatestDate("2024-06-30");
        return config;
    }
}
