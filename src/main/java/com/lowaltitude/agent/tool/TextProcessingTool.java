package com.lowaltitude.agent.tool;

import com.lowaltitude.agent.service.retrieval.SynonymService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 文本处理工具 - 关键词提取、同义词扩展
 */
@Slf4j
@Component
public class TextProcessingTool {

    private SynonymService synonymService;

    public TextProcessingTool(SynonymService synonymService) {
        this.synonymService = synonymService;
    }
    
    /**
     * 从用户查询中提取潜在指标关键词
     */
    @Tool(name = "extractKeywords", description = "从用户查询中提取潜在指标关键词")
    public List<String> extractKeywords(
            @ToolParam(description = "用户原始查询") String query) {
        
        log.info("Extracting keywords from: {}", query);
        Set<String> keywords = new LinkedHashSet<>();
        
        // 1. 按常见分隔符分割
        String[] parts = query.split("[和与、，,的\\s]+");
        
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.length() >= 2 && !isRegionWord(trimmed) && !isTimeWord(trimmed)) {
                keywords.add(trimmed);
            }
        }
        
        // 2. 如果没有提取到有效词，返回整句
        if (keywords.isEmpty()) {
            keywords.add(query);
        }
        
        log.info("Extracted keywords: {}", keywords);
        return new ArrayList<>(keywords);
    }
    
    /**
     * 扩展关键词的同义词
     */
    @Tool(name = "expandSynonyms", description = "扩展关键词的同义词列表")
    public List<String> expandSynonyms(
            @ToolParam(description = "关键词列表") List<String> keywords) {
        
        log.info("Expanding synonyms for: {}", keywords);
        Set<String> expanded = new LinkedHashSet<>();
        
        for (String keyword : keywords) {
            expanded.add(keyword);
            expanded.addAll(synonymService.expandIndicatorKeywords(keyword));
        }
        
        log.info("Expanded keywords: {}", expanded);
        return new ArrayList<>(expanded);
    }
    
    /**
     * 计算BM25分数
     */
    @Tool(name = "bm25Score", description = "计算查询与候选指标的BM25文本匹配分数")
    public double bm25Score(
            @ToolParam(description = "用户查询") String query,
            @ToolParam(description = "指标名称") String indicatorName,
            @ToolParam(description = "指标标签") String tags,
            @ToolParam(description = "指标描述") String remark) {
        
        String queryLower = query.toLowerCase();
        String[] queryTerms = queryLower.split("\\s+");
        
        double score = 0.0;
        String text = (indicatorName + " " + (tags != null ? tags : "") + " " + 
                      (remark != null ? remark : "")).toLowerCase();
        
        for (String term : queryTerms) {
            // 精确匹配指标名称
            if (indicatorName.toLowerCase().contains(term)) {
                score += 2.0;
            }
            // 标签匹配
            if (tags != null && tags.toLowerCase().contains(term)) {
                score += 1.5;
            }
            // 描述匹配
            if (remark != null && remark.toLowerCase().contains(term)) {
                score += 1.0;
            }
        }
        
        // 简单长度归一化
        return score / (1 + 0.01 * text.length());
    }
    
    private boolean isRegionWord(String word) {
        Set<String> regions = Set.of("北京", "上海", "广州", "深圳", "杭州", "全国", 
                                      "各省", "各地", "城市", "地区");
        return regions.contains(word);
    }
    
    private boolean isTimeWord(String word) {
        return Pattern.matches(".*(近|最近|最近\\d+|\\d+个月|\\d+年|年|月|日|趋势).*", word);
    }
}
