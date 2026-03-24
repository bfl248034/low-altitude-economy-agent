---
name: sql-generator
description: |
  SQL生成技能：基于已匹配的指标和解析的维度生成查询SQL。
  支持多指标（IN条件）、多维度值（IN条件）、截面分析（排除默认值）。
  数据表已聚合，不需要GROUP BY。
---

# SQL生成技能 (SQL Generator)

## 功能
根据指标和维度信息生成可执行的SQL。

## 输入

### 1. 指标信息（多指标）
```json
{
  "indicators": [
    {"indicatorId": "I_RPA_ICN_RAE_SALARY_AMOUNT", ...},
    {"indicatorId": "I_RPA_ICN_RAE_POSITION_NUM", ...}
  ]
}
```

### 2. 维度信息
- region: 地区编码、名称、级别
- timeRange: 起止时间（yyyy-MM-dd）
- dimensions: 其他维度值列表
- analysisType: 分析类型

### 3. 表结构信息
从db_data_table获取：
- table_name: 物理表名
- time_column: 时间字段（默认time_id）
- region_column: 地区字段（默认region_id）
- region_level_column: 地区级别字段（默认region_level_num）
- value_column: 数值字段（默认fact_value）
- indicator_column: 指标字段（默认indicator_id）

## SQL生成规则

### 1. SELECT子句
```sql
SELECT 
  time_id,           -- 时间
  region_id,         -- 地区
  region_level_num,  -- 地区级别
  indicator_id,      -- 指标（用于区分多指标）
  edu_level,         -- 其他维度
  fact_value         -- 数值
```

### 2. FROM子句
```sql
FROM schema.table_name
```

### 3. WHERE子句

#### 多指标条件
```sql
-- 单指标
indicator_id = 'I_RPA_ICN_RAE_SALARY_AMOUNT'

-- 多指标（IN）
indicator_id IN ('I_RPA_ICN_RAE_SALARY_AMOUNT', 'I_RPA_ICN_RAE_POSITION_NUM')
```

#### 时间范围
```sql
-- 按月表（time_id格式为yyyyMM）
time_id BETWEEN '202401' AND '202406'

-- 时间范围计算：将yyyy-MM-dd转换为yyyyMM
```

#### 地区条件
```sql
-- 指定具体地区
region_id = '110000' AND region_level_num = 2

-- 指定地区级别（如只看省级）
region_level_num = 2

-- 全国（不过滤）
-- 无region条件
```

#### 其他维度
```sql
-- 单值
dimension_column = 'value'

-- 多值（IN）
dimension_column IN ('value1', 'value2')

-- 截面分析（排除默认值）
dimension_column != 'default_value'
```

### 4. ORDER BY
```sql
ORDER BY time_id DESC
```

## 生成示例

### 示例1：单指标趋势
**用户**: "北京近6个月本科薪资趋势"
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
**用户**: "招聘薪资和岗位数量对比"
```sql
SELECT time_id, region_id, region_level_num, indicator_id, edu_level, fact_value
FROM ads_rpa_w_icn_recruit_salary_amount_m
WHERE indicator_id IN ('I_RPA_ICN_RAE_SALARY_AMOUNT', 'I_RPA_ICN_RAE_POSITION_NUM')
  AND time_id BETWEEN '202401' AND '202406'
  AND region_level_num = 1
ORDER BY time_id DESC, indicator_id
```

### 示例3：多维度值
**用户**: "本科和硕士的招聘数量"
```sql
SELECT time_id, region_id, region_level_num, indicator_id, edu_level, fact_value
FROM ads_rpa_w_icn_edu_recruit_position_num_m
WHERE indicator_id = 'I_RPA_ICN_RAE_POSITION_NUM'
  AND time_id BETWEEN '202401' AND '202406'
  AND region_level_num = 1
  AND edu_level IN ('3', '4')
ORDER BY time_id DESC
```

### 示例4：截面分析
**用户**: "不同学历的招聘数量（排除不限）"
```sql
SELECT time_id, region_id, region_level_num, indicator_id, edu_level, fact_value
FROM ads_rpa_w_icn_edu_recruit_position_num_m
WHERE indicator_id = 'I_RPA_ICN_RAE_POSITION_NUM'
  AND time_id BETWEEN '202401' AND '202406'
  AND region_level_num = 1
  AND edu_level != '0'  -- 排除"不限"
ORDER BY time_id DESC, edu_level
```

### 示例5：省级排名
**用户**: "各省招聘数量排名"
```sql
SELECT time_id, region_id, region_level_num, indicator_id, edu_level, fact_value
FROM ads_rpa_w_icn_edu_recruit_position_num_m
WHERE indicator_id = 'I_RPA_ICN_RAE_POSITION_NUM'
  AND time_id = '202406'  -- 最新一期
  AND region_level_num = 2  -- 只看省级
  AND edu_level = '0'  -- 全部学历（默认值）
ORDER BY fact_value DESC
```

## 注意事项
1. 数据表已聚合，不需要GROUP BY
2. 指标作为查询条件之一（indicator_id）
3. 时间格式转换：yyyy-MM-dd → yyyyMM
4. 地区级别通过region_level_num筛选
5. 多维度值使用IN，截面分析使用!=排除默认值
