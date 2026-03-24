---
name: indicator-matcher
description: |
  指标匹配技能：使用向量+BM25+同义词+LLM精排匹配用户查询到具体指标。
  支持多指标识别（如"招聘和薪资"识别为两个指标）。
---

# 指标匹配技能 (Indicator Matcher)

## 功能
根据用户自然语言查询，从数据库中检索并匹配最相关的指标。

## 检索流程

### 1. 关键词提取
从用户查询中提取可能的指标关键词：
- "北京本科招聘薪资" → ["招聘", "薪资"]
- "企业数量和招聘趋势" → ["企业数量", "招聘"]

### 2. 同义词扩展
使用同义词服务扩展关键词：
- "薪资" → ["薪资", "工资", "薪酬", "收入"]
- "招聘" → ["招聘", "招工", "岗位", "职位"]

### 3. 向量+BM25混合检索
- **向量检索**：使用Embedding模型计算语义相似度
- **BM25**：基于关键词匹配的文本相似度
- **混合分数**：0.6*向量分数 + 0.4*BM25分数

### 4. LLM精排
将Top候选指标输入LLM，由LLM决定最终匹配的指标：
- 支持多指标识别
- 提供匹配理由

## 输出

```json
{
  "indicators": [
    {
      "indicatorId": "I_RPA_ICN_RAE_SALARY_AMOUNT",
      "indicatorName": "招聘岗位平均薪酬",
      "unit": "元",
      "frequency": "M",
      "tableId": "ads_rpa_w_icn_recruit_salary_amount_m",
      "matchScore": 0.95,
      "matchReason": "用户询问薪资，匹配到招聘岗位平均薪酬指标"
    },
    {
      "indicatorId": "I_RPA_ICN_RAE_POSITION_NUM",
      "indicatorName": "招聘岗位数量",
      "unit": "个",
      "frequency": "M",
      "tableId": "ads_rpa_w_icn_edu_recruit_position_num_m",
      "matchScore": 0.88,
      "matchReason": "用户同时询问招聘，匹配到招聘岗位数量指标"
    }
  ]
}
```

## 注意事项
- 指标向量库存储在内存中，启动时从db_indicator表加载
- 多指标查询时，每个指标单独进行后续维度解析和SQL生成
- 如果没有匹配到指标，返回空列表并提示用户
