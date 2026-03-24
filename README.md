# 低空经济智能体

基于 Spring AI Alibaba 构建的低空经济领域智能问答系统。

## 功能特性

- **闲聊对话**：问候、能力介绍等日常交互
- **数据查询**：招聘薪资、企业数量等低空经济指标查询
- **预留扩展**：文章检索、政策分析（开发中）

## 技术栈

- Spring Boot 3.2.5
- Spring AI Alibaba 1.1.2.1
- DashScope (通义千问)
- H2 Database
- Tailwind CSS

## 快速启动

### 1. 配置环境变量

```bash
export DASHSCOPE_API_KEY="your-api-key"
```

### 2. 编译运行

```bash
mvn clean package -DskipTests
java -jar target/low-altitude-economy-agent-1.0.0.jar
```

### 3. 访问

- 前端页面：http://localhost:8080/
- 对话接口：POST /api/chat

## 项目结构

```
src/main/
├── java/com/lowaltitude/agent/
│   ├── LowAltitudeEconomyAgentApplication.java
│   ├── config/AgentConfig.java      # Agent配置
│   ├── controller/ChatController.java # REST接口
│   └── tool/QueryDataTool.java       # 数据查询工具
└── resources/
    ├── static/index.html             # 前端页面
    ├── skills/                       # Skills目录
    │   ├── indicator-retriever/
    │   ├── dimension-resolver/
    │   ├── sql-builder/
    │   ├── result-formatter/
    │   ├── article-retriever/        # 预留
    │   └── policy-retriever/         # 预留
    └── db/                           # 数据库脚本
```

## 智能体架构

```
SupervisorAgent (中央协调器)
├── ChatAgent (闲聊)
├── DataQueryAgent (数据查询)
│   ├── Skill: indicator-retriever
│   ├── Skill: dimension-resolver
│   ├── Skill: sql-builder
│   └── Skill: result-formatter
├── ArticleAgent (预留)
└── PolicyAgent (预留)
```

## 示例问题

- "你好"
- "能做什么"
- "北京招聘薪资"
- "各省份企业数量排名"
