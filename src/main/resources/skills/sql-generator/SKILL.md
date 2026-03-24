---
name: sql-generator
description: |
  根据指标和维度生成可执行SQL。
  使用 Tool：getTableSchema → getDataSource → buildSql
---

# SQL生成技能 (SQL Generator)

## 功能
根据已匹配的指标、解析的维度和表结构，生成适配对应数据源的SQL。

## 输入
```json
{
  "indicators": [
    {
      "indicatorId": "I_RPA_ICN_RAE_SALARY_AMOUNT",
      "indicatorName": "招聘岗位平均薪酬"
    }
  ],
  "dimensions": {
    "timeRange": {...},
    "region": {...},
    "dimensions": [...],
    "analysisType": "TREND"
  },
  "tableId": "ads_rpa_w_icn_recruit_salary_amount_m"
}
```

## 输出
```json
{
  "sql": "SELECT time_id, region_id, region_level_num, indicator_id, edu_level, fact_value FROM ads_rpa_w_icn_recruit_salary_amount_m WHERE indicator_id = 'I_RPA_ICN_RAE_SALARY_AMOUNT' AND time_id BETWEEN '202401' AND '202406' AND region_id = '110000' AND region_level_num = 2 AND edu_level = '3' ORDER BY time_id DESC",
  "tableId": "ads_rpa_w_icn_recruit_salary_amount_m",
  "sourceId": "ds_h2_demo",
  "sourceType": "h2",
  "explanation": "查询北京近6个月本科招聘薪资趋势"
}
```

## 执行流程

### Step 1: 获取表结构
调用 `getTableSchema(tableId)`
- 表名、数据源ID
- 字段映射：time_column, region_column, region_level_column, value_column, indicator_column
- 支持的维度列表

### Step 2: 获取数据源
调用 `getDataSource(sourceId)`
- 数据源类型：h2/mysql/kylin
- 用于SQL方言适配

### Step 3: 构建SQL
调用 `buildSql(indicators, dimensions, schema, sourceType)`

## SQL构建规则

### SELECT 子句
```sql
SELECT 
  time_id,              -- 时间
  region_id,            -- 地区
  region_level_num,     -- 地区级别
  indicator_id,         -- 指标（多指标时必须）
  edu_level,            -- 其他维度字段
  fact_value            -- 数值
```

### FROM 子句
```sql
FROM table_name
-- 或 schema.table_name (如果有schema)
```

### WHERE 子句（按顺序）

#### 1. 指标条件
```sql
-- 单指标
indicator_id = 'I_RPA_ICN_RAE_SALARY_AMOUNT'

-- 多指标（IN）
indicator_id IN ('I_RPA_ICN_RAE_SALARY_AMOUNT', 'I_RPA_ICN_RAE_POSITION_NUM')
```

#### 2. 时间范围
```sql
-- 月表 (time_id格式: yyyyMM)
time_id BETWEEN '202401' AND '202406'

-- 日表 (time_id格式: yyyyMMdd)
time_id BETWEEN '20240101' AND '20240630'
```

#### 3. 地区条件
```sql
-- 指定具体地区
region_id = '110000' AND region_level_num = 2

-- 指定级别（如只看省级）
region_level_num = 2

-- 全国（无地区过滤条件）
```

#### 4. 其他维度
```sql
-- 单值
dimension_column = 'value'

-- 多值（IN）
dimension_column IN ('value1', 'value2')

-- 截面分析（排除默认值）
dimension_column != 'default_value'
```

### ORDER BY
```sql
-- 趋势分析：按时间降序
ORDER BY time_id DESC

-- 排名分析：按数值降序
ORDER BY fact_value DESC

-- 对比分析：按时间、维度排序
ORDER BY time_id DESC, dimension_column
```

## 示例

### 示例1：单指标趋势
输入: 北京近6个月本科薪资趋势
```sql
SELECT time_id, region_id, region_level_num, indicator_id, edu_level, fact_value
FROM ads_rpa_w_icn_recruit_salary_amount_m
WHERE indicator_id = 'I_RPA_ICN_RAE_SALARY_AMOUNT'
  AND time_id BETWEEN '202401' AND '202406'
  AND region_id = '110000'
  AND region_level_num = 2
  AND edu_level = '3'
ORDER BY time_id DESC
```

### 示例2：多指标对比
输入: 招聘薪资和岗位数量对比
```sql
SELECT time_id, region_id, region_level_num, indicator_id, edu_level, fact_value
FROM ads_rpa_w_icn_recruit_salary_amount_m
WHERE indicator_id IN ('I_RPA_ICN_RAE_SALARY_AMOUNT', 'I_RPA_ICN_RAE_POSITION_NUM')
  AND time_id BETWEEN '202401' AND '202406'
ORDER BY time_id DESC, indicator_id
```

### 示例3：多维度值
输入: 本科和硕士的招聘数量
```sql
SELECT time_id, region_id, region_level_num, indicator_id, edu_level, fact_value
FROM ads_rpa_w_icn_edu_recruit_position_num_m
WHERE indicator_id = 'I_RPA_ICN_RAE_POSITION_NUM'
  AND time_id BETWEEN '202401' AND '202406'
  AND edu_level IN ('3', '4')
ORDER BY time_id DESC
```

### 示例4：截面分析
输入: 不同学历的招聘数量（排除不限）
```sql
SELECT time_id, region_id, region_level_num, indicator_id, edu_level, fact_value
FROM ads_rpa_w_icn_edu_recruit_position_num_m
WHERE indicator_id = 'I_RPA_ICN_RAE_POSITION_NUM'
  AND time_id BETWEEN '202401' AND '202406'
  AND edu_level != '0'  -- 排除"不限"
ORDER BY time_id DESC, edu_level
```

### 示例5：省级排名
输入: 各省招聘数量排名
```sql
SELECT time_id, region_id, region_level_num, indicator_id, edu_level, fact_value
FROM ads_rpa_w_icn_edu_recruit_position_num_m
WHERE indicator_id = 'I_RPA_ICN_RAE_POSITION_NUM'
  AND time_id = '202406'  -- 最新一期
  AND region_level_num = 2
ORDER BY fact_value DESC
```

## 注意事项
1. 数据表已聚合，**不需要GROUP BY**
2. 指标作为查询条件之一（indicator_id）
3. 时间格式根据频率转换：yyyy-MM-dd → yyyyMM
4. 地区级别通过region_level_num筛选
