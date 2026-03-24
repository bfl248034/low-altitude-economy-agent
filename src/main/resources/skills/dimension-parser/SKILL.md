---
name: dimension-parser
description: |
  维度解析技能：使用LLM直接抽取用户查询中的维度条件。
  输入：用户查询 + 指标信息 + 最新时间 + 维度值列表
  输出：解析后的地区、时间、其他维度值
---

# 维度解析技能 (Dimension Parser)

## 功能
从用户查询中解析并抽取维度条件。

## 输入
调用LLM时提供以下上下文：

### 1. 用户查询
原始用户输入，如："北京近6个月本科招聘薪资"

### 2. 指标信息
- 指标名称
- 指标ID
- 频率（D/W/M/Q/Y）
- 数据表ID

### 3. 最新数据时间
从latest_time_config表获取：
- latestTimeId: "202406"
- latestDate: "2024-06-30"
- frequency: "M"

### 4. 维度值列表
从db_data_dimension + dimension_values表获取：
- 维度名称和字段名
- 所有可选值及其编码
- 默认值标记
- 同义词

**不包含**：地区维度和时间维度（由LLM直接推理）

## 解析任务

### 1. 地区解析
将自然语言地区转换为编码和级别：
| 用户说法 | region_code | region_name | region_level |
|---------|------------|-------------|--------------|
| 全国 | 0 | 全国 | 1 |
| 北京 | 110000 | 北京 | 2 |
| 各省 | - | 各省 | 2 |
| 分省份 | - | 分省份 | 2 |

### 2. 时间解析
基于频率和最新时间计算：
| 频率 | 用户说法 | 计算方式 | 结果 |
|------|---------|---------|------|
| M | 近6个月 | latestDate-6个月 | 2024-01-01 至 2024-06-30 |
| M | 2024年 | 当年全年 | 2024-01-01 至 2024-12-31 |
| Q | 近4季度 | latestDate-12个月 | 2023-07-01 至 2024-06-30 |

时间格式：yyyy-MM-dd

### 3. 其他维度解析
从维度值列表中匹配：
- 精确匹配："本科" → edu_level = "3"
- 同义词匹配："研究生" → edu_level = "4"（硕士）
- 多值匹配："本科和硕士" → edu_level IN ("3", "4")
- 默认值：未提及的维度使用db_data_dimension中配置的默认值

### 4. 分析类型识别
| 关键词 | 类型 | 说明 |
|--------|------|------|
| 无 | SINGLE | 查询具体值 |
| 趋势、走势 | TREND | 时序分析 |
| 排名、排行 | RANKING | 排序分析 |
| 对比、比较 | COMPARISON | 对比分析 |
| 不同、分、各 | CROSS_SECTION | 截面分析 |

## 输出

```json
{
  "region": {
    "code": "110000",
    "name": "北京",
    "level": 2
  },
  "timeRange": {
    "start": "2024-01-01",
    "end": "2024-06-30"
  },
  "dimensions": [
    {
      "dimensionId": "edu_level",
      "columnName": "edu_level",
      "values": ["3"],
      "valueNames": ["本科"],
      "useDefault": false,
      "isCrossSection": false
    }
  ],
  "analysisType": "TREND",
  "reasoning": "用户询问北京近6个月本科薪资趋势"
}
```

## 截面分析特殊处理
当分析类型为CROSS_SECTION时：
- 目标维度不指定具体值（或排除默认值）
- SQL生成时添加条件：`dimension_column != 'default_value'`
- 示例："不同学历的招聘数量" → `edu_level != '0'`（排除"不限"）
