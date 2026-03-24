package com.lowaltitude.agent.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.lowaltitude.agent.tool.QueryDataTool;
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
                        3. 回答关于低空经济的一般性问题（无需查询数据）
                        
                        注意：如果用户需要具体数据（如"北京招聘薪资"），请建议用户使用数据查询功能。
                        """)
                .saver(new MemorySaver())
                .build();
    }

    @Bean
    public ReactAgent dataQueryAgent(ChatModel chatModel, 
                                     SkillsAgentHook skillsAgentHook,
                                     QueryDataTool queryDataTool) {
        return ReactAgent.builder()
                .name("data_query_agent")
                .model(chatModel)
                .description("专业处理低空经济数据查询")
                .instruction("""
                        你是低空经济数据查询专家。请严格按以下流程执行：
                        
                        步骤1：调用 read_skill("indicator-retriever") 识别用户需要的指标
                        步骤2：调用 read_skill("dimension-resolver") 解析时间、地区、学历等维度
                        步骤3：调用 read_skill("sql-builder") 生成查询SQL
                        步骤4：使用 queryData 工具执行查询（传入SQL字符串）
                        步骤5：调用 read_skill("result-formatter") 格式化查询结果
                        
                        注意：
                        - 必须按顺序执行步骤
                        - 每个步骤完成后才能进入下一步
                        - 最终返回格式化后的结果
                        """)
                .tools(queryDataTool)
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
                .instruction("该功能正在开发中，请用户稍后再试。你可以回复：文章检索功能即将上线，敬请期待！")
                .saver(new MemorySaver())
                .build();
    }

    @Bean
    public ReactAgent policyAgent(ChatModel chatModel) {
        return ReactAgent.builder()
                .name("policy_agent")
                .model(chatModel)
                .description("[预留] 政策分析功能")
                .instruction("该功能正在开发中，请用户稍后再试。你可以回复：政策分析功能即将上线，敬请期待！")
                .saver(new MemorySaver())
                .build();
    }

    @Bean
    public SupervisorAgent supervisorAgent(ChatModel chatModel,
                                           ReactAgent chatAgent,
                                           ReactAgent dataQueryAgent,
                                           ReactAgent articleAgent,
                                           ReactAgent policyAgent) {
        String systemPrompt = """
                你是低空经济智能体的入口协调者，负责识别用户意图并路由到合适的Agent。
                
                ## 可用的子Agent
                
                ### chat_agent
                - **功能**: 处理闲聊、问候、简单问答
                - **适用场景**: 
                  * "你好"、"谢谢"、"在吗"等问候
                  * "你能做什么"等能力询问
                  * 与低空经济无关的一般性问题
                - **输出**: chat_response
                
                ### data_query_agent
                - **功能**: 处理低空经济数据查询
                - **适用场景**:
                  * 涉及"招聘"、"薪资"、"企业数量"、"岗位"等具体指标
                  * 需要查询具体数值、排名、趋势
                  * 包含时间、地区、学历等限定条件
                - **输出**: query_result
                
                ### article_agent
                - **功能**: 文章检索（开发中）
                - **适用**: 如用户询问文章/新闻，先检查是否支持，如不支持则返回提示
                
                ### policy_agent
                - **功能**: 政策分析（开发中）
                - **适用**: 如用户询问政策，先检查是否支持，如不支持则返回提示
                
                ## 决策规则
                1. 闲聊/问候 -> chat_agent -> FINISH
                2. 数据查询（包含指标关键词）-> data_query_agent -> FINISH
                3. 文章/政策需求 -> 如该功能已就绪则路由，否则返回提示信息
                4. 模糊意图 -> chat_agent 进行澄清
                
                ## 响应格式
                只返回Agent名称（chat_agent/data_query_agent/article_agent/policy_agent）或FINISH，不要包含其他解释。
                """;

        return SupervisorAgent.builder()
                .name("low_altitude_supervisor")
                .description("低空经济智能体中央协调器")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .subAgents(List.of(chatAgent, dataQueryAgent, articleAgent, policyAgent))
                .build();
    }
}
