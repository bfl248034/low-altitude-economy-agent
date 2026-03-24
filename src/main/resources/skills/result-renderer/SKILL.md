---
name: result-renderer
description: |
  格式化查询结果为人类可读文本。
  使用 Tool：generateSummary → suggestRelatedQueries
---

# 结果渲染技能 (Result Renderer)

## 功能
将查询结果转换为自然语言描述，生成摘要和推荐问题。

## 输入
```json
{
  "data": [...],
  "indicators": [...],
  "dimensions": {...},
  "analysisType": "TREND",
  "rowCount": 6
}
```

## 输出
```json
{
  "formattedText": "北京近6个月本科招聘薪资趋势：\n2024年1月：12,300元\n2024年2月：12,500元\n...",
  "chartType": "line",
  "summary": "整体呈上涨趋势，平均薪资约12,650元，最高出现在2024年6月（12,800元）",
  "suggestedQueries": [
    "硕士学历薪资对比",
    "其他城市薪资趋势",
    "招聘数量趋势"
  ]
}
```

## 执行流程

### Step 1: 生成摘要
调用 `generateSummary(data, analysisType, indicators)`

**趋势分析摘要**:
- 起始值、结束值
- 整体趋势（上涨/下跌/平稳）
- 平均值、最高值、最低值

**排名分析摘要**:
- 前几名地区/维度
- 排名变化（如有历史对比）

**对比分析摘要**:
- 各对比项数值
- 差异分析

### Step 2: 格式化文本
根据分析类型生成不同格式：

**趋势格式**:
```
北京近6个月本科招聘薪资趋势：
2024年1月：12,300元
2024年2月：12,500元
...
```

**排名格式**:
```
各省招聘数量排名（2024年6月）：
1. 广东：15,000个
2. 江苏：12,000个
...
```

### Step 3: 推荐相关问题
调用 `suggestRelatedQueries(indicators, dimensions)`
- 基于当前指标推荐相关问题
- 基于当前维度推荐切换维度的问题

## 图表建议
| 分析类型 | 推荐图表 |
|---------|---------|
| TREND | line（折线图） |
| RANKING | bar（柱状图） |
| COMPARISON | groupedBar（分组柱状图） |
| CROSS_SECTION | pie（饼图）或 bar |
