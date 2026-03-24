package com.lowaltitude.agent.service.retrieval;

import com.lowaltitude.agent.entity.Indicator;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存向量检索服务 - 使用Spring AI Embedding
 */
@Slf4j
@Service
public class InMemoryVectorSearchService {
    
    private final EmbeddingModel embeddingModel;
    private final Map<String, IndicatorVector> vectorStore = new ConcurrentHashMap<>();
    
    public InMemoryVectorSearchService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }
    
    /**
     * 指标向量文档
     */
    public record IndicatorVector(
            String indicatorId,
            String indicatorName,
            String tags,
            String remark,
            List<Float> embedding,
            String unit,
            String frequency,
            String tableId
    ) {}
    
    /**
     * 初始化向量存储
     */
    public void initVectorStore(List<Indicator> indicators) {
        log.info("Initializing vector store with {} indicators", indicators.size());
        vectorStore.clear();
        
        for (Indicator indicator : indicators) {
            // 构建搜索文本
            String searchText = buildSearchText(indicator);
            
            try {
                // 生成向量
                EmbeddingResponse response = embeddingModel.embedForResponse(List.of(searchText));
                List<Float> embedding = response.getResults().get(0).getOutput();
                
                IndicatorVector vector = new IndicatorVector(
                        indicator.getIndicatorId(),
                        indicator.getIndicatorName(),
                        indicator.getTags(),
                        indicator.getRemark(),
                        embedding,
                        indicator.getUnit(),
                        indicator.getFrequency(),
                        indicator.getTableId()
                );
                
                vectorStore.put(indicator.getIndicatorId(), vector);
                
            } catch (Exception e) {
                log.error("Failed to embed indicator: {}", indicator.getIndicatorId(), e);
            }
        }
        
        log.info("Vector store initialized with {} entries", vectorStore.size());
    }
    
    /**
     * 向量检索 + BM25混合搜索
     */
    public List<IndicatorVector> hybridSearch(String query, int topK) {
        if (vectorStore.isEmpty()) {
            return Collections.emptyList();
        }
        
        // 1. 向量检索
        EmbeddingResponse response = embeddingModel.embedForResponse(List.of(query));
        List<Float> queryEmbedding = response.getResults().get(0).getOutput();
        
        List<ScoredIndicator> scored = new ArrayList<>();
        
        for (IndicatorVector vector : vectorStore.values()) {
            // 向量相似度 (余弦)
            double vectorScore = cosineSimilarity(queryEmbedding, vector.embedding());
            
            // BM25分数
            double bm25Score = calculateBM25(query, vector);
            
            // 混合分数 (可调整权重)
            double finalScore = 0.6 * vectorScore + 0.4 * bm25Score;
            
            scored.add(new ScoredIndicator(vector, finalScore));
        }
        
        // 排序并取TopK
        return scored.stream()
                .sorted(Comparator.comparing(ScoredIndicator::score).reversed())
                .limit(topK)
                .map(ScoredIndicator::vector)
                .collect(Collectors.toList());
    }
    
    /**
     * 批量检索多个指标
     */
    public Map<String, List<IndicatorVector>> batchSearch(List<String> queries, int topKPerQuery) {
        Map<String, List<IndicatorVector>> results = new HashMap<>();
        for (String query : queries) {
            results.put(query, hybridSearch(query, topKPerQuery));
        }
        return results;
    }
    
    /**
     * 根据ID获取向量
     */
    public Optional<IndicatorVector> getById(String indicatorId) {
        return Optional.ofNullable(vectorStore.get(indicatorId));
    }
    
    /**
     * 构建搜索文本
     */
    private String buildSearchText(Indicator indicator) {
        StringBuilder sb = new StringBuilder();
        sb.append(indicator.getIndicatorName());
        if (indicator.getTags() != null) {
            sb.append(" ").append(indicator.getTags().replace(",", " "));
        }
        if (indicator.getRemark() != null) {
            sb.append(" ").append(indicator.getRemark());
        }
        return sb.toString();
    }
    
    /**
     * 余弦相似度
     */
    private double cosineSimilarity(List<Float> a, List<Float> b) {
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
    
    /**
     * 简化版BM25计算
     */
    private double calculateBM25(String query, IndicatorVector vector) {
        String text = (vector.indicatorName() + " " + 
                      (vector.tags() != null ? vector.tags() : "") + " " +
                      (vector.remark() != null ? vector.remark() : "")).toLowerCase();
        
        String[] queryTerms = query.toLowerCase().split("\\s+");
        double score = 0.0;
        
        for (String term : queryTerms) {
            // 精确匹配
            if (vector.indicatorName().toLowerCase().contains(term)) {
                score += 2.0;
            }
            // 标签匹配
            if (vector.tags() != null && vector.tags().toLowerCase().contains(term)) {
                score += 1.5;
            }
            // 描述匹配
            if (vector.remark() != null && vector.remark().toLowerCase().contains(term)) {
                score += 1.0;
            }
        }
        
        return score / (1 + 0.1 * text.length());  // 简单长度归一化
    }
    
    private record ScoredIndicator(IndicatorVector vector, double score) {}
}
