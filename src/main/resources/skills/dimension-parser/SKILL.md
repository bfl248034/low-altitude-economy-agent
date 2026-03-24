---
name: dimension-parser
description: |
  解析用户查询中的维度条件。LLM直接抽取，不给地区/时间维度值。
  使用 Tool：getIndicatorMeta → getLatestTime → getDimensionValues → llmParseDimensions
---

# 维度解析技能 (Dimension Parser)

## 功能
从用户查询中解析时间、地区、其他维度条件。

## 输入
```json
{
  "userQuery": "北京近6个月本科招聘薪资",
  "indicators": [
    {
      "indicatorId": "I_RPA_ICN_RAE_SALARY_AMOUNT",
      "indicatorName": "招聘岗位平均薪酬",
      "frequency": "M",
      "tableId": "ads_rpa_w_icn_recruit_salary_amount_m"
    }
  ]
}
```

## 输出
```json
{
  "timeRange": {
    "start": "2024-01-01",
    "end": "2024-06-30",
    "timeColumn": "time_id",
    "originalText": "近6个月"
  },
  "region": {
    "code": "110000",
    "name": "北京",
    "level": 2,
    "column": "region_id",
    "levelColumn": "region_level_num"
  },
  "dimensions": [
    {
      "dimensionId": "edu_level",
      "dimensionName": "学历",
      "column": "edu_level",
      "values": ["3"],
      "valueNames": ["本科"],
      "isDefault": false,
      "isCrossSection": false
    }
  ],
  "analysisType": "TREND",
  "reasoning": "解析出北京（地区）、近6个月（时间）、本科（学历）、趋势分析"
}
```

## 执行流程

### Step 1: 获取指标元数据
调用 `getIndicatorMeta(indicatorId)`
- 获取指标频率（D/W/M/Q/Y）
- 获取数据表ID

### Step 2: 获取最新时间
调用 `getLatestTime(indicatorId)`
- 获取该指标最新数据时间
- 示例: latestTimeId=202406, latestDate=2024-06-30

### Step 3: 获取维度值列表
调用 `getDimensionValues(tableId, excludeTimeRegion=true)`
- 获取该表的所有非时间、非地区维度值
- 包含：维度编码、维度名称、可选值列表、默认值

### Step 4: LLM解析维度
调用 `llmParseDimensions(userQuery, context)`

**输入Context结构**:
```json
{
  "userQuery": "北京近6个月本科招聘薪资",
  "indicator": {
    "name": "招聘岗位平均薪酬",
    "frequency": "M",
    "latestTimeId": "202406",
    "latestDate": "2024-06-30"
  },
  "dimensions": [
    {
      "dimensionId": "edu_level",
      "dimensionName": "学历",
      "column": "edu_level",
      "values": [
        {"code": "0", "name": "不限", "isDefault": true},
        {"code": "3", "name": "本科"},
        {"code": "4", "name": "硕士"}
      ]
    }
  ]
}
```

**LLM任务**:
1. **地区解析**: 自然语言 → region_code + region_name + region_level
   - "北京" → code: "110000", name: "北京", level: 2
   - "全国" → code: "0", name: "全国", level: 1
   - "各省" → 只指定 level: 2，不指定具体code

2. **时间计算**: 基于频率和最新时间
   - 频率M + "近6个月" + latestDate=2024-06-30
   - → start: "2024-01-01", end: "2024-06-30"

3. **维度匹配**: 从提供的维度值列表中匹配
   - "本科" → code: "3", name: "本科"
   - 支持多值: "本科和硕士" → ["3", "4"]
   - 未提及的使用默认值

4. **分析类型识别**:
   - SINGLE: 无特殊关键词
   - TREND: 趋势、走势、变化
   - RANKING: 排名、排行、第几
   - COMPARISON: 对比、比较、vs
   - CROSS_SECTION: 不同、分、各、按

## 特殊处理

### 截面分析 (CROSS_SECTION)
当用户说"不同学历的招聘数量"：
- `isCrossSection: true`
- SQL生成时: `edu_level != '0'` (排除默认值"不限")
- 返回该维度所有非默认值的数据

### 地区级别
数据表有 `region_level_num` 字段：
- 1: 全国级
- 2: 省级
- 3: 市级
- 4: 区县级

用户说"各省排名" → level=2，不指定具体region_id

### 多维度值
用户说"本科和硕士" → values: ["3", "4"]
SQL生成时用 IN 条件
