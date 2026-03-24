---
name: indicator-retriever
description: |
  用于检索用户查询中涉及的低空经济指标。
  当用户询问包含"招聘薪资"、"企业数量"、"岗位数量"等具体指标时使用。
  支持混合检索（向量+关键词）和同义词扩展。
---

# 指标检索技能 (Indicator Retriever)

## 功能
从用户自然语言查询中识别并检索相关的低空经济指标。

## 使用流程

### 1. 同义词扩展
用户输入中的指标描述可能使用不同表达方式，需要扩展同义词：
- "工资"、"薪水"、"待遇"、"收入" → 统一理解为"薪酬"
- "公司数量"、"企业数"、"主体数" → 统一理解为"企业数量"
- "招工"、"职位"、"岗位" → 统一理解为"招聘"

### 2. 多指标识别
如果用户查询包含多个指标（如"招聘薪资和岗位数量"），需要分别识别：
- 使用关键词"和"、"与"、"及"、","分隔多个指标
- 对每个指标分别进行检索

### 3. 输出格式
返回结构化的指标信息：

```json
{
  "indicators": [
    {
      "indicatorId": "指标ID",
      "indicatorName": "指标名称",
      "tableId": "数据表ID",
      "unit": "单位",
      "confidence": 0.95
    }
  ],
  "reasoning": "识别逻辑说明"
}
```

## 可用的指标类型

| 指标ID | 指标名称 | 单位 | 数据表 |
|--------|---------|------|--------|
| I_RPA_ICN_RAE_SALARY_AMOUNT | 招聘岗位平均薪酬 | 元 | ads_rpa_w_icn_recruit_salary_amount_m |
| I_RPA_ICN_RAE_POSITION_NUM | 招聘岗位数量 | 个 | ads_rpa_w_icn_edu_recruit_position_num_m |
| I_ENT_ICN_RAE_ENTERPRISE_NUM | 存续企业数量 | 家 | ads_ent_w_icn_enterprise_num_m |

## 示例

**用户输入**: "北京近6个月本科招聘薪资"

**识别结果**:
```json
{
  "indicators": [{
    "indicatorId": "I_RPA_ICN_RAE_SALARY_AMOUNT",
    "indicatorName": "招聘岗位平均薪酬",
    "tableId": "ads_rpa_w_icn_recruit_salary_amount_m",
    "unit": "元",
    "confidence": 0.95
  }],
  "reasoning": "用户询问'招聘薪资'，匹配到指标'招聘岗位平均薪酬'"
}
```

**用户输入**: "各省份招聘薪资排名"

**识别结果**:
```json
{
  "indicators": [{
    "indicatorId": "I_RPA_ICN_RAE_SALARY_AMOUNT",
    "indicatorName": "招聘岗位平均薪酬",
    "tableId": "ads_rpa_w_icn_recruit_salary_amount_m",
    "unit": "元",
    "confidence": 0.90
  }],
  "reasoning": "用户询问'招聘薪资'，用于地区排名对比"
}
```
