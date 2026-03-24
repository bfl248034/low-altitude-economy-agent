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
import com.lowaltitude.agent.service.query.DataQueryOrchestrator;
import lombok.extern.slf4j.Slf4j;
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
    public ReactAgent dataQueryAgent(ChatModel chatModel,
                                     SkillsAgentHook skillsAgentHook,
                                     DataQueryOrchestrator dataQueryOrchestrator) {
        return ReactAgent.builder()
                .name("data_query_agent")
                .model(chatModel)
                .description("专业处理低空经济数据查询")
                .instruction("""
                        你是低空经济数据查询专家。
                        
                        查询流程：
                        1. 意图分析：判断是数据查询还是闲聊
                        2. 指标匹配：使用向量检索+BM25+同义词+LLM精排找到最匹配的指标
                        3. 维度解析：LLM直接抽取地区、时间、其他维度
                        4. SQL生成：基于指标和维度生成查询SQL
                        5. 执行查询：在对应数据源执行
                        6. 结果格式化：返回人类可读的格式
                        
                        支持的查询类型：
                        - 单指标查询："北京招聘薪资"
                        - 多指标查询："招聘薪资和岗位数量"
                        - 趋势分析："近6个月薪资趋势"
                        - 排名分析："各省招聘数量排名"
                        - 对比分析："本科和硕士薪资对比"
                        - 截面分析："不同学历的招聘数量"
                        
                        注意：
                        - 数据表已聚合，不需要GROUP BY
                        - 支持多指标（IN条件）
                        - 支持多维度值（IN条件）
                        - 支持地区级别筛选（全国/省级/市级）
                        """)
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
                - **特点**: 
                  - 向量+BM25+同义词+LLM精排进行指标匹配
                  - LLM直接抽取维度
                  - 支持多指标、多维度值、截面分析
                
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
