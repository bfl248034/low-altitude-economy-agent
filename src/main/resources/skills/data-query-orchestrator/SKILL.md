---
name: data-query-orchestrator
description: |
  数据查询编排器：一站式完成指标匹配、维度解析、SQL生成、查询执行和结果处理。
  内部流程：指标匹配 → 维度解析+SQL生成 → 查询执行
---

# 数据查询编排技能 (Data Query Orchestrator)

## 功能
作为数据查询的核心编排器，串联完整的数据查询流程。

## 输入
```json
{
  "userQuery": "北京和上海近3个月本科招聘薪资"
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
    "timeRange": {"start": "2024-04-30", "end": "2024-06-30"},
    "region": {"codes": ["110000", "310000"], "name": "北京和上海", "level": 2},
    "dimensions": [{"dimensionId": "edu_level", "values": ["3"], "valueNames": ["本科"]}],
    "analysisType": "TREND"
  },
  "sqlTasks": [
    {
      "tableId": "fact_recruitment",
      "sourceId": "ds_recruitment",
      "indicatorIds": ["I_RPA_ICN_RAE_SALARY_AMOUNT"],
      "sql": "SELECT ... WHERE ..."
    }
  ],
  "data": [
    {"time_id": "2024-06-30", "region_id": "110000", "fact_value": 12500},
    {"time_id": "2024-06-30", "region_id": "310000", "fact_value": 13200}
  ],
  "summary": "北京和上海近3个月本科招聘薪资趋势..."
}
```

## 执行流程（3个粗粒度工具）

### Step 1: 指标匹配
**使用Tool**：`matchIndicators`

**输入**：
- `query`: 用户原始查询
- `topK`: 返回候选数量（默认10）

**内部流程**：
1. 提取关键词（过滤地区和时间词）
2. 扩展同义词（薪资→[薪资,工资,薪酬,收入]）
3. 向量+BM25混合检索候选指标
4. LLM精排确认最终匹配（支持多指标）

**输出**：
```json
{
  "success": true,
  "indicators": [
    {
      "indicatorId": "...",
      "indicatorName": "...",
      "tableId": "...",
      "sourceId": "...",
      "matchScore": 0.95
    }
  ],
  "isMultiMetric": true,
  "reasoning": "匹配理由",
  "keywords": ["关键词列表"]
}
```

### Step 2: 维度解析 + SQL生成（合并工具）
**使用Tool**：`parseAndBuildSql`

**输入**：
- `query`: 用户原始查询
- `indicators`: 匹配到的指标列表（包含 indicatorId, tableId, sourceId）

**内部流程**：
1. **收集所有指标信息**：遍历所有指标，获取元数据、最新时间、维度配置
2. **找出最大最新时间**：所有指标中最新时间的最大值作为基准
3. **合并维度集合**：收集所有指标所在表的维度，去重后交给LLM
4. **LLM解析维度**（传入最大时间用于推算）：
   - 地区：支持多地区（如"北京和上海"→codes:["110000","310000"]）
   - 时间：**根据最大时间推算**（如近3个月=最大时间-3个月）
   - 其他维度：从合并后的维度集合中匹配
   - 分析类型：TREND/RANKING/COMPARISON/CROSS_SECTION
5. **按表分组生成SQL**：不同表的指标生成独立的SQL语句

**时间推算规则**（基于最大最新时间）：
- "近3个月" → 开始时间 = 最大时间往前推3个月的最后一天，结束时间 = 最大时间
- "近6个月" → 开始时间 = 最大时间往前推6个月的最后一天
- "今年" → 开始时间 = 当年1月1日，结束时间 = 最大时间
- "去年" → 开始时间 = 去年1月1日，结束时间 = 去年12月31日
- 默认（未指定时间）→ 近6个月

**输出**：
```json
{
  "success": true,
  "indicators": [...],
  "indicatorIds": ["..."],
  "allDimensions": [...],
  "maxLatestDate": "2024-06-30",
  "frequency": "M",
  "timeRange": {"start": "2024-04-30", "end": "2024-06-30"},
  "regionCodes": ["110000", "310000"],
  "regionLevel": 2,
  "dimensionConditions": {"edu_level": ["3"]},
  "sqlTasks": [
    {
      "tableId": "fact_recruitment",
      "sourceId": "ds_recruitment",
      "indicatorIds": ["I_RPA_ICN_RAE_SALARY_AMOUNT"],
      "sql": "SELECT time_id, region_id, ... WHERE ..."
    }
  ]
}
```

### Step 3: 多源并行查询执行
**使用Tool**：`executeMultiQuery`

**输入**：
- `queryTasks`: 查询任务列表，每项包含 `sourceId` 和 `sql`
  ```json
  [
    {"sourceId": "ds_recruitment", "sql": "SELECT ..."},
    {"sourceId": "ds_enterprise", "sql": "SELECT ..."}
  ]
  ```

**内部流程**：
1. 并行执行所有查询任务（线程池）
2. 自动翻译结果中的编码（如region_id→地区名）
3. 合并所有结果，添加数据源标识
4. 按时间排序返回

**输出**：
```json
{
  "success": true,
  "data": [
    {"time_id": "2024-06-30", "region_id": "110000", "fact_value": 12500, "_dataSource": "招聘数据", "_sourceId": "ds_recruitment"},
    {"time_id": "2024-06-30", "region_id": "310000", "fact_value": 13200, "_dataSource": "招聘数据", "_sourceId": "ds_recruitment"}
  ],
  "rowCount": 12,
  "queryCount": 1,
  "successCount": 1
}
```

**特点**：
- 支持多指标跨不同数据源并行查询
- 自动处理部分查询失败的情况
- 结果包含数据源标识，便于区分

### Step 4: 结果处理
使用 `ResultFormatTool` 完成：
- 格式化数值：12500→"12,500元"
- 生成数据摘要
- 推荐相关问题

## 错误处理

| 错误场景 | 处理方式 |
|---------|---------|
| 未匹配到指标 | 返回`success:false`，建议用户换种说法 |
| 维度解析失败 | 使用默认时间范围（近6个月）和地区（全国）继续 |
| SQL执行失败 | 返回错误信息，其他查询继续执行 |
| 无数据 | 返回`rowCount:0`，提示无数据 |

## 完整示例

**输入**："北京和上海近3个月本科招聘薪资"

**处理过程**：
1. **指标匹配**：调用 `matchIndicators`
   - 匹配到 `招聘岗位平均薪酬` 指标
   - 返回指标信息（含tableId, sourceId）
   
2. **维度解析+SQL生成**：调用 `parseAndBuildSql`
   - 收集维度：该指标所在表的维度（edu_level等）
   - 找出最大时间：假设为 "2024-06-30"
   - LLM解析：
     - 地区：北京和上海 → codes:["110000","310000"]
     - 时间：近3个月 → start="2024-04-30", end="2024-06-30"（最大时间往前推3个月）
     - 学历：本科 → edu_level:["3"]
   - 生成SQL：包含多地区条件和维度条件
   
3. **查询执行**：调用 `executeMultiQuery`
   - 执行SQL，返回北京和上海的数据
   
4. **结果处理**：生成趋势摘要

**输出**：
```
北京和上海近3个月本科招聘薪资：

北京：
- 2024年4月：12,000元
- 2024年5月：12,300元
- 2024年6月：12,500元

上海：
- 2024年4月：12,800元
- 2024年5月：13,000元
- 2024年6月：13,200元

两地平均薪资呈上涨趋势，上海略高于北京。
```

## Tool列表

| Tool名 | 功能 | 内部流程 |
|--------|------|----------|
| `matchIndicators` | 指标匹配 | 关键词提取→同义词扩展→向量检索→LLM精排 |
| `parseAndBuildSql` | 维度解析+SQL生成 | 收集所有指标维度→找出最大时间→LLM解析→生成SQL |
| `executeMultiQuery` | 多源并行查询 | 并行执行→合并结果→按时间排序 |

## 多表多源查询场景

当用户查询涉及不同表的指标时（如"招聘薪资和企业数量"）：

1. `matchIndicators` 匹配到多个指标，包含各自的 tableId 和 sourceId
2. `parseAndBuildSql` 按 tableId 分组，为每个表生成独立的 SQL
3. `executeMultiQuery` 并行执行所有 SQL，合并结果

示例 sqlTasks 输出：
```json
[
  {
    "tableId": "fact_recruitment",
    "sourceId": "ds_recruitment",
    "indicatorIds": ["I_RPA_ICN_RAE_SALARY_AMOUNT"],
    "sql": "SELECT ... FROM fact_recruitment WHERE ..."
  },
  {
    "tableId": "fact_enterprise",
    "sourceId": "ds_enterprise",
    "indicatorIds": ["I_ENT_COUNT"],
    "sql": "SELECT ... FROM fact_enterprise WHERE ..."
  }
]
```
