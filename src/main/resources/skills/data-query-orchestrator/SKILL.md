---
name: data-query-orchestrator
description: |
  数据查询编排器：一站式完成指标匹配、维度解析、SQL生成、查询执行和结果处理。
  内部流程：指标匹配 → 维度解析 → SQL生成 → 查询执行 → 结果处理
---

# 数据查询编排技能 (Data Query Orchestrator)

## 功能
作为数据查询的核心编排器，串联完整的数据查询流程。

## 输入
```json
{
  "userQuery": "北京近6个月本科招聘薪资"
}
```

## 输出
```json
{
  "success": true,
  "indicators": [
    {
      "indicatorId": "I_RPA_ICN_RAE_SALARY_AMOUNT",
      "indicatorName": "招聘岗位平均薪酬",
      "unit": "元"
    }
  ],
  "dimensions": {
    "timeRange": {"start": "202401", "end": "202406"},
    "region": {"code": "110000", "name": "北京", "level": 2},
    "dimensions": [{"dimensionId": "edu_level", "values": ["3"], "valueNames": ["本科"]}],
    "analysisType": "TREND"
  },
  "sql": "SELECT ... FROM ... WHERE ...",
  "data": [
    {"time_id": "202406", "fact_value": 12500}
  ],
  "summary": "北京近6个月本科招聘薪资呈上涨趋势，平均约12,650元"
}
```

## 内部执行流程

### Step 1: 指标匹配
**使用Tool**：`extractKeywords` → `expandSynonyms` → `vectorSearch` → `llmRerank`

**流程**：
1. 提取用户查询中的潜在指标词
2. 扩展同义词（薪资→[薪资,工资,薪酬,收入]）
3. 向量检索候选指标
4. LLM精排确认最终匹配（支持多指标）

**输出**：匹配到的指标列表 + 是否多指标

### Step 2: 维度解析
**使用Tool**：`getIndicatorMeta` → `getLatestTime` → `getDimensionConfigs` → `llmParseDimensions`

**流程**：
1. 获取指标元数据（频率、表ID）
2. 获取最新数据时间
3. 获取维度配置（**包含默认值，来自 db_data_dimension.default_value**）
4. LLM解析：
   - 地区：自然语言 → code + level
   - 时间：基于频率计算范围
   - 其他维度：从提供的列表中匹配
   - 分析类型：TREND/RANKING/COMPARISON/CROSS_SECTION

**默认值说明**：
- 默认值存储在 `db_data_dimension` 表的 `default_value` 字段
- 例如：`edu_level` 维度的默认值可能是 `"0"`（代表"不限"）
- 截面分析时需要排除默认值（`dimension != default_value`）

**特殊处理**：
- "各省" → level=2，不指定具体region_id
- "本科和硕士" → values=["3","4"]
- "不同学历" → isCrossSection=true，排除默认值

### Step 3: SQL生成
**使用Tool**：`getTableSchema` → `buildSql`

**生成规则**：
```sql
SELECT time_id, region_id, region_level_num, indicator_id, [维度字段], fact_value
FROM [表名]
WHERE 
  indicator_id IN ('id1', 'id2')          -- 多指标
  AND time_id BETWEEN 'start' AND 'end'   -- 时间范围
  AND region_id = 'code'                  -- 指定地区（如有）
  AND region_level_num = 2                -- 地区级别（如有）
  AND dimension IN ('v1', 'v2')           -- 维度值（如有）
ORDER BY time_id DESC
```

**buildSql Tool 参数**：
- `tableId`: 表ID
- `indicatorIds`: 指标ID列表（支持多指标）
- `timeStart`: 时间开始（格式：yyyyMM）
- `timeEnd`: 时间结束（格式：yyyyMM）
- `regionCode`: 地区编码（可选）
- `regionLevel`: 地区级别（可选，1=全国,2=省级,3=市级,4=区县）
- `dimensionConditions`: 其他维度条件JSON（可选）

**注意**：
- 数据表已聚合，**不需要GROUP BY**
- 时间格式转换：yyyy-MM-dd → yyyyMM（月表）
- 截面分析：`dimension != default_value`（排除默认值）

### Step 4: 查询执行
**使用Tool**：`executeSql`

**流程**：
1. 在对应数据源执行SQL
2. 返回查询结果

### Step 5: 结果处理
**使用Tool**：`translateCodes` → `ResultFormatTool`

- 翻译编码：region_id→地区名，edu_level→学历名
- 格式化数值：12500→"12,500元"
- 生成数据摘要和推荐问题

## 错误处理

| 错误场景 | 处理方式 |
|---------|---------|
| 未匹配到指标 | 返回`success:false`，建议用户换种说法 |
| 维度解析失败 | 使用默认值继续 |
| SQL执行失败 | 返回错误信息 |
| 无数据 | 返回`rowCount:0`，提示无数据 |

## 完整示例

**输入**："北京近6个月本科招聘薪资"

**处理过程**：
1. 指标匹配：提取[招聘,薪资]→扩展→向量检索→LLM确认→`招聘岗位平均薪酬`
2. 维度解析：
   - 地区：北京→code:110000, level:2
   - 时间：频率M+近6个月→202401到202406
   - 学历：本科→code:3（从db_data_dimension获取默认值"0"表示"不限"）
   - 分析类型：TREND
3. SQL生成：使用buildSql构建带上述条件的查询
4. 执行查询：返回6个月的数据
5. 结果处理：生成趋势摘要

**输出**：
```
北京近6个月本科招聘薪资趋势：
- 2024年1月：12,300元
- 2024年2月：12,500元
- ...
- 2024年6月：12,800元

整体呈上涨趋势，平均薪资约12,650元。
```
