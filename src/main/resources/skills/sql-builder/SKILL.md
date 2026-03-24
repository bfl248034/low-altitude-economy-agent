---
name: sql-builder
description: |
  用于根据指标和维度条件生成SQL查询语句。
  支持 MySQL、Kylin、H2 等多种数据库方言。
---

# SQL构建技能 (SQL Builder)

## 功能
根据已识别的指标和维度条件，生成可执行的SQL查询语句。

## SQL结构模板

```sql
SELECT 
  {time_column},
  {region_column},
  {dimension_columns},
  {value_column}
FROM {table_name}
WHERE {conditions}
GROUP BY {group_by}
ORDER BY {order_by}
```

## 维度条件映射

### 时间条件
| 维度类型 | SQL条件示例 |
|---------|------------|
| 具体月份 | `time_id = '202401'` |
| 时间范围 | `time_id BETWEEN '202401' AND '202406'` |
| 最近N个月 | `time_id >= DATE_FORMAT(DATE_SUB(CURDATE(), INTERVAL 6 MONTH), '%Y%m')` |

### 地区条件
| 维度类型 | SQL条件示例 |
|---------|------------|
| 具体地区 | `region_id = '110000'` |
| 省级分组 | `region_level_num = 2` |
| 地市级分组 | `region_level_num = 3` |

### 学历条件
| 维度类型 | SQL条件示例 |
|---------|------------|
| 具体学历 | `edu_level = '3'` |
| 多值学历 | `edu_level IN ('3', '4')` |
| 学历以上 | `edu_level >= '3'` |

## 方言适配

### H2 方言
- 使用标准SQL语法
- 时间函数：FORMATDATETIME, PARSEDATETIME
- 字符串拼接：使用 CONCAT

### MySQL 方言
- 使用反引号 `` ` `` 包裹标识符
- 时间函数：DATE_FORMAT, DATE_SUB

### Kylin 方言
- 使用双引号 `"` 包裹标识符（如有必要）
- 支持标准SQL语法

## 查询类型生成

### 1. 趋势查询
```sql
SELECT time_id, fact_value
FROM ads_rpa_w_icn_recruit_salary_amount_m
WHERE time_id BETWEEN '202401' AND '202406'
  AND region_id = '110000'
  AND edu_level = '3'
ORDER BY time_id
```

### 2. 排名查询
```sql
SELECT region_id, region_name, fact_value
FROM ads_rpa_w_icn_recruit_salary_amount_m a
JOIN dim_region r ON a.region_id = r.id
WHERE time_id = (SELECT MAX(time_id) FROM ads_rpa_w_icn_recruit_salary_amount_m)
  AND region_level_num = 2
ORDER BY fact_value DESC
LIMIT 10
```

### 3. 对比查询
```sql
SELECT time_id, edu_level, fact_value
FROM ads_rpa_w_icn_recruit_salary_amount_m
WHERE time_id BETWEEN '202401' AND '202406'
  AND region_id = '110000'
  AND edu_level IN ('3', '4')
ORDER BY time_id, edu_level
```

## 输出格式

```json
{
  "sql": "生成的SQL语句",
  "dialect": "h2",
  "parameters": {
    "tableName": "ads_rpa_w_icn_recruit_salary_amount_m",
    "timeRange": ["202401", "202406"],
    "regionId": "110000",
    "eduLevel": "3"
  },
  "reasoning": "SQL生成逻辑说明"
}
```

## 示例

**输入**: 
- 指标: I_RPA_ICN_RAE_SALARY_AMOUNT
- 维度: 时间=近6个月, 地区=北京, 学历=本科

**输出**:
```json
{
  "sql": "SELECT time_id, fact_value FROM ads_rpa_w_icn_recruit_salary_amount_m WHERE time_id BETWEEN '202401' AND '202406' AND region_id = '110000' AND edu_level = '3' ORDER BY time_id",
  "dialect": "h2",
  "parameters": {
    "tableName": "ads_rpa_w_icn_recruit_salary_amount_m",
    "indicatorColumn": "indicator_id",
    "timeColumn": "time_id",
    "regionColumn": "region_id",
    "valueColumn": "fact_value"
  }
}
```
