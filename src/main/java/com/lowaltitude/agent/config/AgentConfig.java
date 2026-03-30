package com.lowaltitude.agent.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.AgentTool;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.skills.registry.SkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.classpath.ClasspathSkillRegistry;
import com.lowaltitude.agent.config.datasource.DynamicDataSourceManager;
import com.lowaltitude.agent.config.interceptor.ToolMonitoringInterceptor;
import com.lowaltitude.agent.tool.DataQueryTool;
import com.lowaltitude.agent.tool.PolicySearchTools;
import com.lowaltitude.agent.tool.ResultFormatTool;

import lombok.extern.slf4j.Slf4j;

/**
 * Agent 配置类
 * 配置所有智能体：SupervisorAgent（中央协调器）、DataQueryAgent（数据查询）、ChatAgent（闲聊）
 */
@Slf4j
@Configuration
public class AgentConfig {

    @Value("${spring.ai.dashscope.api-key:}")
    private String dashScopeApiKey;

    /**
     * 创建 ChatModel
     */
    @Bean
    public ChatModel chatModel() {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(dashScopeApiKey)
                .build();
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .build();
    }

    /**
     * 动态数据源管理器
     */
    @Bean
    public DynamicDataSourceManager dynamicDataSourceManager() {
        return new DynamicDataSourceManager();
    }

    /**
     * Skill 注册表 - 从 classpath 加载 skills
     */
    @Bean
    public SkillRegistry skillRegistry() {
        return ClasspathSkillRegistry.builder()
                .classpathPath("skills")
                .build();
    }

    /**
     * Skill 钩子 - 让 Agent 可以调用 read_skill
     */
    @Bean
    public SkillsAgentHook skillsAgentHook(SkillRegistry skillRegistry) {
        return SkillsAgentHook.builder()
                .skillRegistry(skillRegistry)
                .build();
    }

    // ==================== Agent 定义 ====================

    /**
     * 闲聊 Agent - 处理问候、简单问答
     */
    @Bean
    public ReactAgent chatAgent(OllamaChatModel chatModel) {
        return ReactAgent.builder()
                .name("chat_agent")
                .model(chatModel)
                .description("处理闲聊和问候，友好回应用户")
                .instruction("""
                        你是低空经济智能体的助手，友好、简洁地回应用户。
                        
                        你的职责：
                        1. 回应问候（你好、谢谢等）
                        2. 介绍自己的能力：
                           - 数据查询：招聘薪资、企业数量、岗位数量、专利数量等
                           - 趋势分析：时间维度变化
                           - 排名对比：各省份/城市对比
                        3. 回答关于低空经济的一般性问题
                        
                        如果用户询问具体数据，引导用户使用数据查询功能。
                        """)
                .saver(new MemorySaver())
                .build();
    }

    /**
     * 数据查询 Agent - 专业处理低空经济数据查询
     */
    @Bean
    public ReactAgent dataQueryAgent(
    		OllamaChatModel chatModel,
            SkillsAgentHook skillsAgentHook,
            DataQueryTool dataQueryTool,
            ResultFormatTool resultFormatTool) {
        
        List<ToolCallback> toolCallbacks = Arrays.asList(ToolCallbacks.from(dataQueryTool));
        List<ToolCallback> toolCallbacks1 = Arrays.asList(ToolCallbacks.from(resultFormatTool));
        
        return ReactAgent.builder()
                .name("data_query_agent")
                .model(chatModel)
                .description("专业处理低空经济数据查询，包括招聘、企业、专利等指标")
                .instruction("""
                        你是低空经济数据查询专家。使用 Skills + Tools 完成查询任务。
                        
                        ## 核心能力
                        你可以查询低空经济相关的各类指标数据：
                        - 招聘类：招聘薪资、岗位数量、招聘企业数
                        - 企业类：企业数量、新增企业、注册资本分布
                        - 创新类：专利数量、专利类型分布
                        
                        ## 执行流程（3个粗粒度工具）
                        
                        Step 1: 指标匹配
                        - 使用 matchIndicators 工具从用户查询中匹配指标
                        - 支持多指标匹配
                        
                        Step 2: 维度解析+SQL生成（合并工具）
                        - 使用 parseAndBuildSql 工具，传入用户查询和匹配到的指标列表
                        - 工具内部会：
                          1. 收集所有指标所在表的维度，合并去重
                          2. 找出所有指标中的最大最新时间作为基准
                          3. LLM解析维度条件，根据最大时间推算时间范围（如近3个月=最大时间-3个月）
                          4. 按表分组生成SQL（不同表的指标分开查询）
                        - 返回 sqlTasks 列表，每项包含 tableId、sourceId、indicatorIds、sql
                        
                        Step 3: 多源并行查询
                        - 使用 executeMultiQuery 工具并行执行所有SQL
                        - 传入从 sqlTasks 提取的 sourceId 和 sql
                        - 自动合并结果并按时间排序
                        
                        ## 多指标跨库查询示例
                        
                        用户查询"北京和上海的招聘薪资和企业数量"：
                        1. matchIndicators 匹配到[招聘薪资, 企业数量]两个指标
                        2. parseDimensions 解析出地区：codes=["110000","310000"]
                        3. 检查两个指标的数据源：
                           - 招聘薪资在 ds_recruitment
                           - 企业数量在 ds_enterprise
                        4. 分别生成SQL：
                           - SQL1: SELECT ... FROM recruitment WHERE region_id IN ('110000','310000')
                           - SQL2: SELECT ... FROM enterprise WHERE region_id IN ('110000','310000')
                        5. executeMultiQuery 并行执行两个查询，传入：
                           [\\{"sourceId":"ds_recruitment","sql":"SQL1..."\\},
                            \\{"sourceId":"ds_enterprise","sql":"SQL2..."\\}]
                        6. 获取合并后的结果，按时间排序
                        - 自动翻译编码、返回格式化数据
                        
                        ## 输出要求
                        返回格式化的数据结果，包含：
                        - 查询的指标名称
                        - 时间范围和地区
                        - 数据表格或趋势描述
                        - 简要分析结论
                        - 推荐的相关问题
                        
                        ## 注意事项
                        - 支持多指标查询（如"薪资和岗位数量"）
                        - 支持多维度值（如"本科和硕士"）
                        - 支持截面分析（如"不同学历对比"）- 需排除默认值
                        - 支持省级/市级排名
                        """)
                .tools(toolCallbacks)
                .tools(toolCallbacks1)
                .hooks(List.of(skillsAgentHook))
                .interceptors(new ToolMonitoringInterceptor())
//                .enableLogging(true)
                .saver(new MemorySaver())
                .build();
    }

    /**
     * 文章检索 Agent - 预留（功能开发中）
     */
    @Bean
    public ReactAgent articleAgent(OllamaChatModel chatModel) {
        return ReactAgent.builder()
                .name("article_agent")
                .model(chatModel)
                .description("[预留] 文章检索功能")
                .instruction("""
                        文章检索功能正在开发中。
                        
                        请回复用户：
                        "文章检索功能即将上线，敬请期待！目前您可以：
                        1. 查询低空经济数据（招聘、企业、专利等）
                        2. 了解行业趋势和排名"
                        """)
                .saver(new MemorySaver())
                .build();
    }

    /**
     * 政策分析 Agent - 预留（功能开发中）
     */
    @Bean
    public ReactAgent policyAgent(OllamaChatModel chatModel,PolicySearchTools policySearchTools) {
    	List<ToolCallback> toolCallbacks = Arrays.asList(ToolCallbacks.from(policySearchTools));
        return ReactAgent.builder()
                .name("policy_agent")
                .model(chatModel)
                .description("专业处理低空经济相关政策的查询")
                .instruction("""
                        政策分析功能正在开发中。
                        你是低空经济政策查询专家。使用 Tools 完成查询政策查询任务。
                        
                        ## 核心能力
                        你可以查询低空经济相关的各类政策内容数据，
                        """)
                .saver(new MemorySaver())
                .tools(toolCallbacks)
                .interceptors(new ToolMonitoringInterceptor())
                .build();
    }

    /**
     * Supervisor Agent - 中央协调器
     * 负责识别用户意图并路由到合适的子 Agent
     */
    @Bean
    @Primary
    public ReactAgent supervisorAgent(
    		OllamaChatModel chatModel,
            ReactAgent chatAgent,
            ReactAgent dataQueryAgent,
            ReactAgent articleAgent,
            ReactAgent policyAgent) {
        
        String systemPrompt = """
                你是低空经济智能体的入口协调者，负责识别用户意图并路由到合适的 Agent。
                
                ## 可用的子 Agent
                
                ### chat_agent
                - **功能**: 处理闲聊、问候、简单问答
                - **适用场景**: 
                  * "你好"、"嗨"、"在吗"
                  * "能做什么"、"有什么功能"
                  * "谢谢"、"再见"
                  * 其他一般性问候
                - **处理方式**: 调用 chat_agent，然后 FINISH
                
                ### data_query_agent
                - **功能**: 专业处理低空经济数据查询
                - **适用场景**（关键词判断）:
                  * 招聘相关：招聘、薪资、工资、薪酬、岗位、职位、招工
                  * 企业相关：企业数量、公司数、注册企业、新增企业
                  * 创新相关：专利、发明、实用新型、创新
                  * 地区相关：北京、上海、深圳、各省、各城市
                  * 时间相关：近6个月、2024年、趋势、走势
                  * 分析类型：排名、对比、分布、占比
                - **处理方式**: 调用 data_query_agent，然后直接返回智能体结果
                
                ### article_agent
                - **功能**: 文章检索（开发中）
                - **适用场景**: 用户要求查询文章、资讯、新闻
                - **处理方式**: 直接回复"文章检索功能开发中"
                
                ### policy_agent
                - **功能**: 政策查询分析
                - **适用场景**: 用户要求查询政策、法规、文件
                - **处理方式**: 调用 policy_agent，然后直接返回智能体结果
                
                ## 决策规则（优先级从高到低）
                
                1. **问候语/闲聊** → chat_agent
                   关键词：你好、您好、嗨、在吗、谢谢、再见、帮助、介绍
                
                2. **数据查询** → data_query_agent
                   关键词：招聘、薪资、工资、岗位、企业、专利、数量、排名、趋势、对比
                   地区词：北京、上海、广州、深圳、各省、各城市、全国
                   时间词：近X个月、2024年、去年、今年
                
                3. **文章资讯** → 回复开发中
                   关键词：文章、新闻、资讯、报道
                
                4. **政策法规** → policy_agent
                   关键词：政策、法规、文件、规定
                
                5. **模糊/无法判断** → chat_agent 进行澄清
                
                ## 执行方式
                - 使用 AgentTool 调用子 Agent
                - 单步路由：调用子 Agent 后直接 FINISH
                - 不需要多轮循环，每个 Agent 都是独立的完整流程
                
                ## 响应格式
                路由决策后，直接调用对应 Agent，将结果返回给用户。
                """;

        return ReactAgent.builder()
                .name("low_altitude_supervisor")
                .description("低空经济智能体中央协调器")
                .model(chatModel)
                .systemPrompt(systemPrompt)
                .tools(AgentTool.getFunctionToolCallback(chatAgent),
                       AgentTool.getFunctionToolCallback(dataQueryAgent),
                       AgentTool.getFunctionToolCallback(articleAgent),
                       AgentTool.getFunctionToolCallback(policyAgent))
                .saver(new MemorySaver())
                .build();
    }
}
