# 低空经济智能体 - 数据查询架构设计

## 整体架构

```
用户输入
    ↓
[SupervisorAgent] 意图路由
    ↓
[DataQueryAgent] ReactAgent
    ↓
┌─────────────────────────────────────────────────────────────┐
│                      数据查询流程                            │
├─────────────────────────────────────────────────────────────┤
│ 1. 指标匹配 (IndicatorMatchingService)                      │
│    ├── 关键词提取："北京本科招聘薪资" → ["招聘","薪资"]       │
│    ├── 同义词扩展：招聘→[招聘,招工,岗位,职位]                │
│    ├── 向量+BM25混合检索：内存实现                          │
│    └── LLM精排：确认最终匹配指标（支持多指标）              │
│                                                             │
│ 2. 维度解析 (LlmDimensionParser)                            │
│    ├── 输入：用户查询 + 指标信息 + 最新时间 + 维度值列表     │
│    ├── 地区解析：自然语言 → region_code + level            │
│    ├── 时间计算：基于频率和最新时间推算范围                  │
│    ├── 维度匹配：从dimension_values匹配其他维度值            │
│    └── 输出：解析后的维度条件 + 分析类型                     │
│                                                             │
│ 3. SQL生成 (SqlGenerationService)                          │
│    ├── 多指标：indicator_id IN ('id1', 'id2')              │
│    ├── 多维度值：dimension IN ('v1', 'v2')                 │
│    ├── 截面分析：dimension != 'default_value'              │
│    └── 地区级别：region_level_num = 2 (省级)               │
│                                                             │
│ 4. 执行查询 (DynamicQueryService)                          │
│    └── 在对应数据源执行SQL，返回结果                         │
└─────────────────────────────────────────────────────────────┘
    ↓
格式化结果返回
```

---

## 1. 指标检索层

### 1.1 核心组件
| 组件 | 职责 |
|------|------|
| `InMemoryVectorSearchService` | 内存向量库，向量+BM25混合检索 |
| `SynonymService` | 同义词扩展（指标+维度值） |
| `IndicatorMatchingService` | 整合检索+LLM精排 |

### 1.2 检索流程
```
用户查询: "北京本科招聘薪资"
    ↓
关键词提取: ["招聘", "薪资"]
    ↓
同义词扩展: 
  - 招聘 → [招聘,招工,岗位,职位,用工,人才]
  - 薪资 → [薪资,工资,薪酬,收入,待遇]
    ↓
向量检索: 每个关键词Top5候选
    ↓
BM25计算: 关键词匹配分数
    ↓
混合分数: 0.6*向量 + 0.4*BM25
    ↓
LLM精排: 从候选中选择最终指标
    ↓
输出: [招聘岗位平均薪酬, 招聘岗位数量]
```

### 1.3 向量存储
- 启动时从 `db_indicator` 表加载
- 使用 `EmbeddingModel` 生成向量
- 存储在内存 `ConcurrentHashMap` 中

---

## 2. 维度解析层

### 2.1 核心组件
| 组件 | 职责 |
|------|------|
| `LlmDimensionParser` | LLM直接抽取维度 |

### 2.2 LLM输入上下文
```
## 用户查询
北京近6个月本科招聘薪资趋势

## 指标信息
- 指标名称: 招聘岗位平均薪酬
- 频率: M (月)
- 数据表: ads_rpa_w_icn_recruit_salary_amount_m

## 最新数据时间
- latestTimeId: 202406
- latestDate: 2024-06-30
- 频率: M

## 维度值列表
维度: 学历 (字段名: edu_level)
可选值:
  - 0: 不限 [默认值]
  - 1: 高中/中专/中技
  - 2: 大专
  - 3: 本科
  - 4: 硕士
  - 5: 博士
```

### 2.3 解析输出
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
  "analysisType": "TREND"
}
```

### 2.4 分析类型
| 类型 | 关键词 | SQL特点 |
|------|--------|---------|
| SINGLE | 无 | 查询具体值 |
| TREND | 趋势、走势 | 时序排序 |
| RANKING | 排名、排行 | 按值排序 |
| COMPARISON | 对比、比较 | 多值对比 |
| CROSS_SECTION | 不同、分、各 | 排除默认值 |

---

## 3. SQL生成层

### 3.1 核心组件
| 组件 | 职责 |
|------|------|
| `SqlGenerationService` | 基于指标和维度生成SQL |

### 3.2 SQL生成规则

#### 多指标条件
```sql
-- 单指标
indicator_id = 'I_RPA_ICN_RAE_SALARY_AMOUNT'

-- 多指标（IN）
indicator_id IN ('I_RPA_ICN_RAE_SALARY_AMOUNT', 'I_RPA_ICN_RAE_POSITION_NUM')
```

#### 时间范围（根据频率转换）
```sql
-- 月表 (time_id格式: yyyyMM)
time_id BETWEEN '202401' AND '202406'
```

#### 地区条件
```sql
-- 指定地区
region_id = '110000' AND region_level_num = 2

-- 指定级别（如只看省级）
region_level_num = 2

-- 全国（无地区过滤）
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

### 3.3 SQL示例

**示例1：单指标趋势**
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

**示例2：多指标对比**
```sql
SELECT time_id, region_id, region_level_num, indicator_id, edu_level, fact_value
FROM ads_rpa_w_icn_recruit_salary_amount_m
WHERE indicator_id IN ('I_RPA_ICN_RAE_SALARY_AMOUNT', 'I_RPA_ICN_RAE_POSITION_NUM')
  AND time_id BETWEEN '202401' AND '202406'
ORDER BY time_id DESC, indicator_id
```

**示例3：截面分析（不同学历）**
```sql
SELECT time_id, region_id, region_level_num, indicator_id, edu_level, fact_value
FROM ads_rpa_w_icn_edu_recruit_position_num_m
WHERE indicator_id = 'I_RPA_ICN_RAE_POSITION_NUM'
  AND time_id BETWEEN '202401' AND '202406'
  AND edu_level != '0'  -- 排除"不限"
ORDER BY time_id DESC, edu_level
```

**示例4：省级排名**
```sql
SELECT time_id, region_id, region_level_num, indicator_id, edu_level, fact_value
FROM ads_rpa_w_icn_edu_recruit_position_num_m
WHERE indicator_id = 'I_RPA_ICN_RAE_POSITION_NUM'
  AND time_id = '202406'
  AND region_level_num = 2
ORDER BY fact_value DESC
```

---

## 4. 数据表设计

### 4.1 核心表
| 表名 | 用途 |
|------|------|
| `db_indicator` | 指标元数据 |
| `db_data_source` | 数据源配置 |
| `db_data_table` | 数据表结构 |
| `db_data_dimension` | 表-维度关联（含默认值） |
| `dimension_values` | 维度值列表（含同义词） |
| `latest_time_config` | 指标最新时间 |

### 4.2 关键配置
- **默认值**：在 `db_data_dimension.default_value` 中配置
- **地区级别**：`region_level_num`（1:全国 2:省级 3:市级 4:区县）
- **指标字段**：数据表必须包含 `indicator_id` 字段以支持多指标

---

## 5. 产业链预留

### 5.1 当前状态
- 当前只支持"低空经济"
- 通过 `indicator_id` 或表名隐含区分

### 5.2 扩展方案
- 新增 `industry_chain` 表定义产业链体系
- 指标表增加 `industry_chain_id` 字段
- 查询时先匹配产业链，再匹配指标

---

## 6. 性能优化

### 6.1 并行查询
```java
CompletableFuture<Optional<Indicator>> indicatorFuture = 
    CompletableFuture.supplyAsync(() -> indicatorRepository.findById(id));

CompletableFuture<Optional<DataTableConfig>> tableFuture = 
    CompletableFuture.supplyAsync(() -> tableConfigRepository.findById(tableId));

CompletableFuture.allOf(indicatorFuture, tableFuture).join();
```

### 6.2 无JOIN设计
- 数据表已聚合，不需要GROUP BY
- 每个查询独立执行，无表关联

### 6.3 内存向量库
- 启动时加载到内存
- 检索不依赖外部ES服务

---

## 7. 支持的查询类型

| 查询 | 示例 | 特点 |
|------|------|------|
| 单指标查询 | "北京招聘薪资" | 单指标+具体维度 |
| 多指标查询 | "招聘薪资和岗位数量" | IN条件多指标 |
| 趋势分析 | "近6个月薪资趋势" | 时序排序 |
| 排名分析 | "各省招聘数量排名" | region_level=2 |
| 对比分析 | "本科和硕士薪资对比" | 多维度值IN |
| 截面分析 | "不同学历的招聘数量" | 排除默认值 |
| 多维度值 | "本科和硕士的招聘数量" | edu_level IN ('3','4') |
| 地区级别 | "各省招聘情况" | region_level_num筛选 |
