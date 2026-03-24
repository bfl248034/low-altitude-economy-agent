-- 低空经济智能体 - H2数据库初始化脚本

-- ==========================
-- 1. 创建数据源配置表
-- ==========================
DROP TABLE IF EXISTS db_data_source;
CREATE TABLE db_data_source (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_id VARCHAR(64) NOT NULL,
  source_name VARCHAR(128) NOT NULL,
  source_type VARCHAR(20) NOT NULL,
  host VARCHAR(256),
  port INT,
  database_name VARCHAR(64),
  username VARCHAR(64),
  password VARCHAR(256),
  connection_params TEXT,
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_ds_source_id ON db_data_source(source_id);

-- ==========================
-- 2. 创建数据表登记表
-- ==========================
DROP TABLE IF EXISTS db_data_table;
CREATE TABLE db_data_table (
  table_id VARCHAR(64) PRIMARY KEY,
  table_name VARCHAR(128) NOT NULL,
  table_alias VARCHAR(128),
  source_id VARCHAR(64) NOT NULL,
  database_name VARCHAR(64),
  schema_name VARCHAR(64),
  table_type VARCHAR(20) DEFAULT 'fact',
  description VARCHAR(500),
  time_column VARCHAR(64) DEFAULT 'time_id',
  region_column VARCHAR(64) DEFAULT 'region_id',
  region_level_column VARCHAR(64) DEFAULT 'region_level_num',
  value_column VARCHAR(64) DEFAULT 'fact_value',
  indicator_column VARCHAR(64) DEFAULT 'indicator_id',
  is_active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ==========================
-- 3. 创建数据维度关联表
-- ==========================
DROP TABLE IF EXISTS db_data_dimension;
CREATE TABLE db_data_dimension (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  table_id VARCHAR(128) NOT NULL,
  dimension_id VARCHAR(64) NOT NULL,
  dimension_name VARCHAR(64),
  dimension_code VARCHAR(64),
  is_common BOOLEAN DEFAULT FALSE,
  is_required BOOLEAN DEFAULT FALSE,
  default_value VARCHAR(64),
  dimension_type VARCHAR(20),
  sort_order INT DEFAULT 0
);

CREATE INDEX idx_dd_table ON db_data_dimension(table_id);

-- ==========================
-- 4. 创建指标元数据表
-- ==========================
DROP TABLE IF EXISTS db_indicator;
CREATE TABLE db_indicator (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  indicator_id VARCHAR(64) NOT NULL,
  indicator_name VARCHAR(128) NOT NULL,
  unit VARCHAR(32),
  frequency VARCHAR(10) NOT NULL,
  valid_measures VARCHAR(256),
  table_id VARCHAR(128),
  remark TEXT,
  domain VARCHAR(64),
  subdomain VARCHAR(64),
  tags VARCHAR(256),
  indexed BOOLEAN DEFAULT FALSE,
  index_version BIGINT DEFAULT 0,
  last_indexed_at TIMESTAMP,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_ind_indicator_id ON db_indicator(indicator_id);

-- ==========================
-- 5. 创建维度值表
-- ==========================
DROP TABLE IF EXISTS dimension_values;
CREATE TABLE dimension_values (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  dimension_id VARCHAR(64) NOT NULL,
  dimension_name VARCHAR(64),
  value_code VARCHAR(64) NOT NULL,
  value_name VARCHAR(128) NOT NULL,
  synonyms VARCHAR(500),
  parent_code VARCHAR(64),
  sort_order INT DEFAULT 0,
  indexed BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dv_dimension ON dimension_values(dimension_id);
