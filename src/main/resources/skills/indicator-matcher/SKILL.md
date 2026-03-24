---
name: indicator-matcher
description: |
  将用户查询匹配到具体指标。支持多指标识别。
  使用 Tool：extractKeywords → expandSynonyms → vectorSearch → bm25Score → llmRerank
---

# 指标匹配技能 (Indicator Matcher)

## 功能
根据用户自然语言查询，通过向量+BM25+同义词+LLM精排匹配最相关的指标。

## 输入
- `userQuery`: string - 用户原始查询

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
      "matchReason": "用户询问薪资，匹配招聘岗位平均薪酬指标"
    }
  ],
  "isMultiMetric": false,
  "reasoning": "通过向量检索+LLM精排匹配到1个指标"
}
```

## 执行流程

### Step 1: 提取关键词
调用 `extractKeywords(userQuery)`
- 从查询中提取潜在指标词
- 示例: "北京本科招聘薪资" → ["招聘", "薪资"]

### Step 2: 同义词扩展
调用 `expandSynonyms(keywords)`
- 扩展同义词以增加召回
- 示例: "薪资" → ["薪资", "工资", "薪酬", "收入", "待遇"]

### Step 3: 向量检索
调用 `vectorSearch(expandedKeywords, topK=10)`
- 每个扩展词分别进行向量检索
- 合并去重，取Top候选

### Step 4: BM25打分
调用 `bm25Score(candidates, userQuery)`
- 对候选指标计算BM25文本匹配分数
- 因素：名称匹配、标签匹配、描述匹配

### Step 5: 混合排序
计算混合分数: `score = 0.6 * vectorScore + 0.4 * bm25Score`

### Step 6: LLM精排
调用 `llmRerank(userQuery, topCandidates)`
- 将Top候选和用户查询输入LLM
- LLM决定最终匹配的指标（可多选）
- 返回匹配理由

## 多指标识别
当用户查询包含多个指标时：
- "招聘薪资和岗位数量" → 识别2个指标
- "企业数和招聘趋势对比" → 识别2个指标

LLM精排时判断是否需要多指标，并说明理由。

## 失败处理
如果未匹配到任何指标：
- `indicators` 为空数组
- `reasoning` 说明未匹配原因
- 下游Skill应提示用户换种说法
