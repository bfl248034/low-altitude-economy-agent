package com.lowaltitude.agent.service.retrieval;

import com.lowaltitude.agent.entity.DimensionValue;
import com.lowaltitude.agent.repository.DimensionValueRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 同义词服务 - 管理维度值同义词和指标同义词扩展
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SynonymService {
    
    private final DimensionValueRepository dimensionValueRepository;
    
    // 指标同义词映射（硬编码，后续可数据库化）
    private final Map<String, List<String>> indicatorSynonyms = new HashMap<>();
    
    // 维度值同义词缓存 dimensionId -> (name -> DimensionValue)
    private Map<String, Map<String, DimensionValue>> dimensionValueCache = new HashMap<>();
    
    @PostConstruct
    public void init() {
        // 初始化指标同义词
        indicatorSynonyms.put("薪资", List.of("工资", "薪酬", "收入", "待遇", "报酬", "月薪", "年薪"));
        indicatorSynonyms.put("招聘", List.of("招工", "岗位", "职位", "用工", "人才", "劳动力"));
        indicatorSynonyms.put("企业", List.of("公司", "主体", "厂商", "单位"));
        indicatorSynonyms.put("数量", List.of("数目", "个数", "规模"));
        
        // 加载维度值同义词
        refreshDimensionValueCache();
    }
    
    /**
     * 刷新维度值缓存
     */
    public void refreshDimensionValueCache() {
        List<DimensionValue> allValues = dimensionValueRepository.findAll();
        dimensionValueCache = allValues.stream()
                .collect(Collectors.groupingBy(
                        DimensionValue::getDimensionId,
                        Collectors.toMap(
                                v -> v.getValueName().toLowerCase(),
                                v -> v,
                                (v1, v2) -> v1  // 处理重复
                        )
                ));
        
        // 加载同义词映射
        for (DimensionValue value : allValues) {
            if (value.getSynonyms() != null && !value.getSynonyms().isEmpty()) {
                String dimId = value.getDimensionId();
                Map<String, DimensionValue> dimMap = dimensionValueCache.computeIfAbsent(dimId, k -> new HashMap<>());
                
                String[] synonyms = value.getSynonyms().split(",");
                for (String syn : synonyms) {
                    dimMap.put(syn.trim().toLowerCase(), value);
                }
            }
        }
        
        log.info("Loaded {} dimension values with synonyms", allValues.size());
    }
    
    /**
     * 扩展指标关键词
     */
    public List<String> expandIndicatorKeywords(String keyword) {
        Set<String> expanded = new HashSet<>();
        expanded.add(keyword);
        
        // 查找包含该词的同义词组
        for (Map.Entry<String, List<String>> entry : indicatorSynonyms.entrySet()) {
            if (entry.getKey().equals(keyword) || entry.getValue().contains(keyword)) {
                expanded.add(entry.getKey());
                expanded.addAll(entry.getValue());
            }
        }
        
        return new ArrayList<>(expanded);
    }
    
    /**
     * 匹配维度值
     */
    public Optional<DimensionValue> matchDimensionValue(String dimensionId, String name) {
        Map<String, DimensionValue> dimMap = dimensionValueCache.get(dimensionId);
        if (dimMap == null) {
            return Optional.empty();
        }
        
        String key = name.toLowerCase();
        
        // 直接匹配
        DimensionValue value = dimMap.get(key);
        if (value != null) {
            return Optional.of(value);
        }
        
        // 模糊匹配
        return dimMap.entrySet().stream()
                .filter(e -> e.getKey().contains(key) || key.contains(e.getKey()))
                .max(Comparator.comparingInt(e -> {
                    // 计算匹配度：包含关系越强分数越高
                    String k = e.getKey();
                    if (k.equals(key)) return 100;
                    if (k.contains(key)) return 50 + key.length() * 2;
                    if (key.contains(k)) return 25 + k.length();
                    return 0;
                }))
                .map(Map.Entry::getValue);
    }
    
    /**
     * 获取维度所有值
     */
    public List<DimensionValue> getDimensionValues(String dimensionId) {
        return dimensionValueRepository.findByDimensionId(dimensionId);
    }
}
