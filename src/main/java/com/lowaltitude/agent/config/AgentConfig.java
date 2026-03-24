package com.lowaltitude.agent.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.AgentTool;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.lowaltitude.agent.config.datasource.DynamicDataSourceManager;
import com.lowaltitude.agent.tool.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class AgentConfig {

    @Value("${spring.ai.dashscope.api-key:}")
    private String dashScopeApiKey;

    @Bean
    public ChatModel chatModel() {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .build();
    }
    

    @Bean
    public DynamicDataSourceManager dynamicDataSourceManager() {
        return new DynamicDataSourceManager();
    }

    @Bean
    public SkillRegistry skillRegistry() {
        return ClasspathSkillRegistry.builder()
                .classpathPath("skills")
                .build();
    }

    @Bean
    public SkillsAgentHook skillsAgentHook(SkillRegistry skillRegistry) {
        return SkillsAgentHook.builder()
                .skillRegistry(skillRegistry)
                .build();
    }

    // ==================== Agent定义 ====================

    /**
     * 闲聊Agent - 处理问候、简单问答
     */
    @Bean
    public ReactAgent chatAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("chat_agent")
                .model(chatModel)
                .description("处理闲聊和问候")
                .instruction("""
                        你是低空经济智能体的助手，友好、简洁地回应用户。
                        
                        你的职责：
                        1. 回应问候（你好、谢谢等）
                        2. 介绍自己的能力（可以查询低空经济指标如招聘薪资、企业数量等）
                        3. 回答关于低空经济的一般性问题
                        """)
                .saver(new MemorySaver())
                .build();
    }

    /**
     * 数据查询Agent - 使用3-Skill设计
     * Skill: intent-router → data-query-orchestrator → result-presenter
     */
    @Bean
    public ReactAgent dataQueryAgent(
            ChatModel chatModel,
            SkillsAgentHook skillsAgentHook,
            TextProcessingTool textProcessingTool,
            MetadataQueryTool metadataQueryTool,
            VectorSearchTool vectorSearchTool,
            SqlExecutionTool sqlExecutionTool) {
        
        return ReactAgent.builder()
                .name("data_query_agent")
                .model(chatModel)
                .description("专业处理低空经济数据查询")
                .instruction("""
                        你是低空经济数据查询专家。使用3-Skill流程完成查询：
                        
                        ## 执行流程
                        
                        Step 1: 意图路由
                        - 调用 read_skill("intent-router")
                        - 判断用户意图：CHAT / DATA_QUERY / UNKNOWN
                        - 如果是CHAT，直接友好回复
                        - 如果是DATA_QUERY，继续下一步
                        
                        Step 2: 数据查询编排（核心）
                        - 调用 read_skill("data-query-orchestrator")
                        - 内部自动完成：指标匹配 → 维度解析 → SQL生成 → 执行 → 结果处理
                        - 使用的Tool：
                          * extractKeywords, expandSynonyms, vectorSearch, llmRerank（指标匹配）
                          * getIndicatorMeta, getLatestTime, getDimensionValues, llmParseDimensions（维度解析）
                          * getTableSchema, buildSql（SQL生成）
                          * executeOnDataSource, translateCodes, formatNumbers（查询执行）
                          * generateSummary（结果处理）
                        - 返回：{success, indicators, dimensions, data, summary}
                        
                        Step 3: 结果展示
                        - 调用 read_skill("result-presenter")
                        - 生成美观的回复文本
                        - 推荐相关问题
                        
                        ## 注意事项
                        - 严格按1→2→3顺序执行
                        - data-query-orchestrator内部已处理多指标、多维度、截面分析
                        - 如Step 1判定为CHAT，直接回复，不执行后续步骤
                        """)
                .tools(textProcessingTool, metadataQueryTool, vectorSearchTool, sqlExecutionTool)
                .hooks(List.of(skillsAgentHook))
                .saver(new MemorySaver())
                .build();
    }

    /**
     * 文章检索Agent - 预留
     */
    @Bean
    public ReactAgent articleAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("article_agent")
                .model(chatModel)
                .description("[预留] 文章检索功能")
                .instruction("该功能正在开发中，请用户稍后再试。回复：文章检索功能即将上线，敬请期待！")
                .saver(new MemorySaver())
                .build();
    }

    /**
     * 政策分析Agent - 预留
     */
    @Bean
    public ReactAgent policyAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("policy_agent")
                .model(chatModel)
                .description("[预留] 政策分析功能")
                .instruction("该功能正在开发中，请用户稍后再试。回复：政策分析功能即将上线，敬请期待！")
                .saver(new MemorySaver())
                .build();
    }

    /**
     * SupervisorAgent - 中央协调器
     */
    @Bean
    public ReactAgent supervisorAgent(ChatModel chatModel,
                                           ReactAgent chatAgent,
                                           ReactAgent dataQueryAgent,
                                           ReactAgent articleAgent,
                                           ReactAgent policyAgent) {
        String systemPrompt = """
                你是低空经济智能体的入口协调者，负责识别用户意图并路由到合适的Agent。
                
                ## 可用的子Agent
                
                ### chat_agent
                - **功能**: 处理闲聊、问候、简单问答
                - **适用场景**: "你好"、"能做什么"、"谢谢"等
                - **处理方式**: 直接回复，FINISH
                
                ### data_query_agent
                - **功能**: 处理低空经济数据查询
                - **适用场景**: 涉及"招聘"、"薪资"、"企业数量"、"岗位"、"专利"、"排名"、"趋势"等
                - **内部流程**（3-Skill）:
                  1. intent-router（意图路由）
                  2. data-query-orchestrator（查询编排：指标→维度→SQL→执行→结果）
                  3. result-presenter（结果展示）
                - **处理方式**: 单步调用，FINISH
                
                ### article_agent
                - **功能**: 文章检索（开发中）
                - **处理方式**: 返回提示信息
                
                ### policy_agent
                - **功能**: 政策分析（开发中）
                - **处理方式**: 返回提示信息
                
                ## 决策规则
                1. **闲聊/问候** → chat_agent → FINISH
                2. **数据查询**（含指标关键词）→ data_query_agent → FINISH
                3. **文章需求** → 返回"文章检索功能开发中"
                4. **政策需求** → 返回"政策分析功能开发中"
                5. **模糊/无法判断** → chat_agent 进行澄清 → FINISH
                
                ## 响应格式
                直接调用对应Agent的工具，或返回FINISH。
                """;

        return ReactAgent.builder()
                .name("low_altitude_supervisor")
                .description("低空经济智能体中央协调器")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .tools(AgentTool.create(chatAgent),AgentTool.create(dataQueryAgent),AgentTool.create(articleAgent),
                		AgentTool.create(policyAgent))
//                .subAgents(List.of(chatAgent, dataQueryAgent, articleAgent, policyAgent))
//                .
                .build();
    }
}
