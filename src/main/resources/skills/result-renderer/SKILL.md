---
name: result-renderer
description: |
  格式化查询结果，将编码翻译为可读文本。
  使用 getDimensionValueName 工具从 dimension_values 表获取中文名称。
---

# 结果渲染技能 (Result Renderer)

## 功能
将数据库查询的原始结果转换为人类可读的格式。

## 使用工具

### 1. getDimensionValueName(dimensionId: string, valueCode: string): string
从 dimension_values 表查询编码对应的中文名称：
- dimensionId='region': 地区编码 → 地区名称
- dimensionId='edu_level': 学历编码 → 学历名称
- dimensionId='time': 时间编码 → 时间描述

## 格式化任务

### 1. 编码翻译
将结果中的编码替换为中文：
- region_id='110000' → "北京"
- edu_level='3' → "本科"
- time_id='202401' → "2024年1月"

### 2. 数值格式化
- 薪资：12500 → "12,500元"
- 数量：1500000 → "150万个"

### 3. 展示建议
根据分析类型推荐展示方式：
| 类型 | 建议 |
|------|------|
| trend | 折线图展示趋势 |
| ranking | 柱状图/表格展示排名 |
| comparison | 分组柱状图对比 |
| single | 指标卡片展示 |

## 输出格式

```json
{
  "formattedText": "北京近6个月本科招聘薪资趋势：\n2024年1月：12,500元\n2024年2月：12,800元\n...",
  "chartType": "line",
  "summary": "整体呈上涨趋势，平均薪资约12,650元"
}
```

## 示例流程

**原始结果**:
```
time_id=202401, region_id=110000, edu_level=3, fact_value=12500
```

**翻译步骤**:
1. 调用 getDimensionValueName('region', '110000') → "北京"
2. 调用 getDimensionValueName('edu_level', '3') → "本科"
3. time_id 本地格式化 → "2024年1月"
4. fact_value 格式化 → "12,500元"

**最终输出**: "北京 2024年1月 本科 招聘薪资：12,500元"
