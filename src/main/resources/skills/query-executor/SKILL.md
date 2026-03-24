---
name: query-executor
description: |
  执行SQL查询并处理结果。
  使用 Tool：executeOnDataSource → translateCodes → formatNumbers
---

# 查询执行技能 (Query Executor)

## 功能
在指定数据源执行SQL，翻译编码为中文，格式化数值。

## 输入
```json
{
  "sql": "SELECT ...",
  "sourceId": "ds_h2_demo",
  "dimensions": {
    "region": {...},
    "dimensions": [...]
  }
}
```

## 输出
```json
{
  "rawData": [
    {"time_id": "202406", "region_id": "110000", "edu_level": "3", "fact_value": 12500}
  ],
  "translatedData": [
    {"time": "2024年6月", "region": "北京", "edu_level": "本科", "value": 12500, "unit": "元"}
  ],
  "rowCount": 6,
  "executionTimeMs": 45
}
```

## 执行流程

### Step 1: 执行查询
调用 `executeOnDataSource(sourceId, sql)`
- 在指定数据源执行SQL
- 返回原始结果列表

### Step 2: 编码翻译
调用 `translateCodes(dimensionId, code)`
- 将region_id翻译为地区名称
- 将edu_level翻译为学历名称
- 将time_id翻译为时间描述

### Step 3: 数值格式化
调用 `formatNumbers(value, unit)`
- 薪资：12500 → "12,500元"
- 数量：1500000 → "150万"

## 错误处理
- 数据源连接失败 → 返回错误信息
- SQL执行失败 → 返回SQL错误信息
- 无数据 → rowCount=0，提示无数据
