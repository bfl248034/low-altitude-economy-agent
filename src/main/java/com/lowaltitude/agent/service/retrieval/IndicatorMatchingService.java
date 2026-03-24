package com.lowaltitude.agent.service.retrieval;

import com.lowaltitude.agent.entity.Indicator;
import com.lowaltitude.agent.repository.IndicatorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 指标匹配服务 - 整合向量+BM25+同义词+LLM精排
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndicatorMatchingService {
    
    private final InMemoryVectorSearchService vectorSearchService;
    private final SynonymService synonymService;
    private final IndicatorRepository indicatorRepository;
    private final ChatModel chatModel;
    
    /**
     * 初始化向量库
     */
    @PostConstruct
    public void init() {
        List<Indicator> indicators = indicatorRepository.findAll();
        vectorSearchService.initVectorStore(indicators);
        log.info("IndicatorMatchingService initialized with {} indicators", indicators.size());
    }
    
    /**
     * 多指标检索结果
     */
    public record MatchedIndicator(
            String indicatorId,
            String indicatorName,
            String unit,
            String frequency,
            String tableId,
            double matchScore,
            String matchReason
    ) {}
    
    /**
     * 单查询多指标匹配
     * 支持用户一句话中包含多个指标（如"招聘和薪资"）
     */
    public List<MatchedIndicator> matchIndicators(String query, int topK) {
        // 1. 提取可能的多个指标关键词
        List<String> keywords = extractIndicatorKeywords(query);
        log.info("Extracted keywords from query: {}", keywords);
        
        // 2. 同义词扩展
        Set<String> expandedKeywords = new HashSet<>();
        for (String keyword : keywords) {
            expandedKeywords.addAll(synonymService.expandIndicatorKeywords(keyword));
        }
        log.info("Expanded keywords: {}", expandedKeywords);
        
        // 3. 向量检索 - 每个关键词检索TopN候选
        Set<InMemoryVectorSearchService.IndicatorVector> candidates = new HashSet<>();
        for (String keyword : expandedKeywords) {
            candidates.addAll(vectorSearchService.hybridSearch(keyword, 5));
        }
        
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 4. LLM精排 - 从候选中选择最匹配的
        return llmRerank(query, new ArrayList<>(candidates), topK);
    }
    
    /**
     * 从查询中提取可能的指标关键词
     * 例如："北京本科招聘薪资" -> ["招聘", "薪资"]
     */
    private List<String> extractIndicatorKeywords(String query) {
        // 简单实现：基于分隔符和常见模式
        List<String> keywords = new ArrayList<>();
        
        // 按常见分隔符分割
        String[] parts = query.split("[和与、，,的\\s]+");
        
        for (String part : parts) {
            // 过滤掉纯地区词、纯时间词
            if (!isRegionWord(part) && !isTimeWord(part) && part.length() >= 2) {
                keywords.add(part);
            }
        }
        
        // 如果没有提取到，使用整句
        if (keywords.isEmpty()) {
            keywords.add(query);
        }
        
        return keywords;
    }
    
    /**
     * LLM精排
     */
    private List<MatchedIndicator> llmRerank(String query, 
                                              List<InMemoryVectorSearchService.IndicatorVector> candidates,
                                              int topK) {
        
        String candidatesStr = candidates.stream()
                .map(c -> String.format("- %s: %s (标签: %s, 描述: %s)",
                        c.indicatorId(), c.indicatorName(), 
                        c.tags() != null ? c.tags() : "无",
                        c.remark() != null ? c.remark() : "无"))
                .collect(Collectors.joining("\n"));
        
        String prompt = """
                分析用户查询，从候选指标中选择最匹配的指标。
                
                用户查询: {query}
                
                候选指标:
                {candidates}
                
                要求:
                1. 判断用户查询中包含几个指标需求（可能多个）
                2. 从候选中选择最匹配的指标（可多选）
                3. 每个匹配的指标给出匹配理由
                
                输出JSON格式:
                {{
                  "matches": [
                    {{
                      "indicatorId": "指标ID",
                      "indicatorName": "指标名称",
                      "score": 0.95,
                      "reason": "匹配理由"
                    }}
                  ]
                }}
                
                如果都不匹配，返回空数组。
                """;
        
        PromptTemplate template = new PromptTemplate(prompt);
        Prompt p = template.create(Map.of("query", query, "candidates", candidatesStr));
        
        try {
            String response = chatModel.call(p).getResult().getOutput().getContent();
            log.info("LLM rerank response: {}", response);
            
            // 解析JSON响应
            return parseLlmResponse(response, candidates);
            
        } catch (Exception e) {
            log.error("LLM rerank failed, fallback to vector score", e);
            // 降级：按向量分数排序
            return candidates.stream()
                    .sorted(Comparator.comparingDouble(InMemoryVectorSearchService.IndicatorVector::hashCode).reversed())
                    .limit(topK)
                    .map(c -> new MatchedIndicator(
                            c.indicatorId(), c.indicatorName(), c.unit(),
                            c.frequency(), c.tableId(), 0.8, "向量匹配"
                    ))
                    .collect(Collectors.toList());
        }
    }
    
    /**
     * 解析LLM响应
     */
    private List<MatchedIndicator> parseLlmResponse(String response, 
                                                    List<InMemoryVectorSearchService.IndicatorVector> candidates) {
        // 简化实现：提取JSON中的matches数组
        List<MatchedIndicator> results = new ArrayList<>();
        
        // 构建候选Map
        Map<String, InMemoryVectorSearchService.IndicatorVector> candidateMap = candidates.stream()
                .collect(Collectors.toMap(InMemoryVectorSearchService.IndicatorVector::indicatorId, c -> c));
        
        // 简单的JSON解析（实际项目建议用Jackson）
        try {
            // 查找indicatorId
            for (InMemoryVectorSearchService.IndicatorVector candidate : candidates) {
                if (response.contains(candidate.indicatorId())) {
                    results.add(new MatchedIndicator(
                            candidate.indicatorId(),
                            candidate.indicatorName(),
                            candidate.unit(),
                            candidate.frequency(),
                            candidate.tableId(),
                            0.95,
                            "LLM确认匹配"
                    ));
                }
            }
        } catch (Exception e) {
            log.error("Parse LLM response failed", e);
        }
        
        return results.isEmpty() ? 
                candidates.stream().limit(3).map(c -> new MatchedIndicator(
                        c.indicatorId(), c.indicatorName(), c.unit(),
                        c.frequency(), c.tableId(), 0.7, "向量匹配"
                )).collect(Collectors.toList()) : results;
    }
    
    private boolean isRegionWord(String word) {
        Set<String> regions = Set.of("北京", "上海", "广州", "深圳", "杭州", "全国", "各省", "各地");
        return regions.contains(word);
    }
    
    private boolean isTimeWord(String word) {
        return word.matches(".*(近|最近|最近\\d+|\\d+个月|\\d+年|年|月|日).*");
    }
}
