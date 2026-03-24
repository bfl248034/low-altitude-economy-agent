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
                        你是低空经济数据查询专家。严格按以下流程执行：
                        
                        ## 执行流程
                        
                        Step 1: 意图分析
                        - 调用 read_skill("intent-analyzer")
                        - 如果是CHAT，直接回复
                        - 如果是DATA_QUERY，继续下一步
                        
                        Step 2: 指标匹配
                        - 调用 read_skill("indicator-matcher")
                        - 使用Tool：extractKeywords → expandSynonyms → vectorSearch → llmRerank
                        - 获取匹配的指标列表
                        
                        Step 3: 维度解析
                        - 调用 read_skill("dimension-parser")
                        - 使用Tool：getIndicatorMeta → getLatestTime → getDimensionValues → llmParseDimensions
                        - 获取解析后的维度（时间、地区、其他维度）
                        
                        Step 4: SQL生成
                        - 调用 read_skill("sql-generator")
                        - 使用Tool：getTableSchema → getDataSource → buildSql
                        - 获取生成的SQL
                        
                        Step 5: 查询执行
                        - 调用 read_skill("query-executor")
                        - 使用Tool：executeOnDataSource → translateCodes → formatNumbers
                        - 获取查询结果
                        
                        Step 6: 结果渲染
                        - 调用 read_skill("result-renderer")
                        - 使用Tool：generateSummary → suggestRelatedQueries
                        - 生成最终回复
                        
                        ## 注意事项
                        - 必须按顺序执行每个步骤
                        - 每个步骤使用对应的Tool获取数据
                        - 支持多指标、多维度值、截面分析
                        """)
                .tools(textProcessingTool, metadataQueryTool, vectorSearchTool, sqlExecutionTool)
                .hooks(List.of(skillsAgentHook))
                .saver(new MemorySaver())
                .build();
    }

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
                - **适用场景**: "你好"、"能做什么"等
                
                ### data_query_agent
                - **功能**: 处理低空经济数据查询
                - **适用场景**: 涉及"招聘"、"薪资"、"企业数量"等具体指标
                - **执行流程**: 
                  1. intent-analyzer（意图分析）
                  2. indicator-matcher（指标匹配：向量+BM25+LLM精排）
                  3. dimension-parser（维度解析：LLM直接抽取）
                  4. sql-generator（SQL生成）
                  5. query-executor（查询执行）
                  6. result-renderer（结果渲染）
                
                ### article_agent
                - **功能**: 文章检索（开发中）
                
                ### policy_agent
                - **功能**: 政策分析（开发中）
                
                ## 决策规则
                1. 闲聊/问候 -> chat_agent -> FINISH
                2. 数据查询（指标关键词）-> data_query_agent -> FINISH
                3. 文章/政策需求 -> 如开发中则返回提示
                4. 模糊意图 -> chat_agent 进行澄清
                
                ## 响应格式
                只返回Agent名称或FINISH。
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
