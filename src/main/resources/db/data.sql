-- 初始化数据

-- ==========================
-- 1. 数据源配置
-- ==========================
INSERT INTO db_data_source (source_id, source_name, source_type, host, port, database_name, username, password, is_active) VALUES
('ds_h2_demo', 'H2演示数据源', 'h2', 'mem', NULL, 'lowaltitude', 'sa', '', true);

-- ==========================
-- 2. 数据表登记
-- ==========================
INSERT INTO db_data_table (table_id, table_name, table_alias, source_id, database_name, schema_name, table_type, description, time_column, region_column, region_level_column, value_column, indicator_column, is_active) VALUES
('ads_rpa_w_icn_recruit_salary_amount_m', 'ads_rpa_w_icn_recruit_salary_amount_m', '招聘薪资月表', 'ds_h2_demo', 'lowaltitude', NULL, 'fact', '招聘岗位平均薪酬月表', 'time_id', 'region_id', 'region_level_num', 'fact_value', 'indicator_id', true),
('ads_rpa_w_icn_edu_recruit_position_num_m', 'ads_rpa_w_icn_edu_recruit_position_num_m', '招聘岗位数月表', 'ds_h2_demo', 'lowaltitude', NULL, 'fact', '招聘岗位数量月表', 'time_id', 'region_id', 'region_level_num', 'fact_value', 'indicator_id', true),
('ads_ent_w_icn_enterprise_num_m', 'ads_ent_w_icn_enterprise_num_m', '企业数量月表', 'ds_h2_demo', 'lowaltitude', NULL, 'fact', '存续企业数量月表', 'time_id', 'region_id', 'region_level_num', 'fact_value', 'indicator_id', true);

-- ==========================
-- 3. 指标元数据
-- ==========================
INSERT INTO db_indicator (indicator_id, indicator_name, unit, frequency, valid_measures, table_id, remark, domain, subdomain, tags, indexed, index_version) VALUES
('I_RPA_ICN_RAE_SALARY_AMOUNT', '招聘岗位平均薪酬', '元', 'M', '当期，当期同比', 'ads_rpa_w_icn_recruit_salary_amount_m', '招聘岗位的平均薪资水平，反映劳动力市场价格', '招聘就业', '薪资水平', '薪资,工资,薪酬,收入,待遇,报酬,月薪,年薪', true, 1),
('I_RPA_ICN_RAE_POSITION_NUM', '招聘岗位数量', '个', 'M', '当期，当期同比', 'ads_rpa_w_icn_edu_recruit_position_num_m', '企业发布的招聘岗位数量，反映用工需求景气度', '招聘就业', '招聘需求', '招聘,岗位,职位,用工,就业,劳动力,人才,招工', true, 1),
('I_ENT_ICN_RAE_ENTERPRISE_NUM', '存续企业数量', '家', 'M', '当期，当期同比', 'ads_ent_w_icn_enterprise_num_m', '低空经济领域存续企业数量', '企业主体', '企业数量', '企业,公司,主体,厂商', true, 1);

-- ==========================
-- 4. 维度值 - 地区
-- ==========================
INSERT INTO dimension_values (dimension_id, dimension_name, value_code, value_name, synonyms, sort_order) VALUES
('region', '地区', '110000', '北京', '北京市,京城,首都', 1),
('region', '地区', '310000', '上海', '上海市,魔都', 2),
('region', '地区', '440100', '广州', '广州市,羊城', 3),
('region', '地区', '440300', '深圳', '深圳市,鹏城', 4),
('region', '地区', '330100', '杭州', '杭州市', 5);

-- ==========================
-- 5. 维度值 - 学历
-- ==========================
INSERT INTO dimension_values (dimension_id, dimension_name, value_code, value_name, synonyms, sort_order) VALUES
('edu_level', '学历', '0', '不限', '不限学历', 0),
('edu_level', '学历', '1', '高中/中专/中技', '高中,中专', 1),
('edu_level', '学历', '2', '大专', '专科,大专', 2),
('edu_level', '学历', '3', '本科', '本科,学士', 3),
('edu_level', '学历', '4', '硕士', '硕士研究生,研究生', 4),
('edu_level', '学历', '5', '博士', '博士研究生', 5);

-- ==========================
-- 6. 维度值 - 时间（简化示例）
-- ==========================
INSERT INTO dimension_values (dimension_id, dimension_name, value_code, value_name, sort_order) VALUES
('time', '时间', '202401', '2024年1月', 1),
('time', '时间', '202402', '2024年2月', 2),
('time', '时间', '202403', '2024年3月', 3),
('time', '时间', '202404', '2024年4月', 4),
('time', '时间', '202405', '2024年5月', 5),
('time', '时间', '202406', '2024年6月', 6);

-- ==========================
-- 7. 数据维度关联配置
-- ==========================
INSERT INTO db_data_dimension (table_id, dimension_id, dimension_name, dimension_code, is_common, is_required, default_value, dimension_type, sort_order) VALUES
('ads_rpa_w_icn_recruit_salary_amount_m', 'time', '时间', 'time_id', true, true, NULL, 'time', 1),
('ads_rpa_w_icn_recruit_salary_amount_m', 'region', '地区', 'region_id', true, true, NULL, 'region', 2),
('ads_rpa_w_icn_recruit_salary_amount_m', 'edu_level', '学历', 'edu_level', true, false, '0', 'enum', 3),
('ads_rpa_w_icn_edu_recruit_position_num_m', 'time', '时间', 'time_id', true, true, NULL, 'time', 1),
('ads_rpa_w_icn_edu_recruit_position_num_m', 'region', '地区', 'region_id', true, true, NULL, 'region', 2),
('ads_rpa_w_icn_edu_recruit_position_num_m', 'edu_level', '学历', 'edu_level', true, false, '0', 'enum', 3),
('ads_ent_w_icn_enterprise_num_m', 'time', '时间', 'time_id', true, true, NULL, 'time', 1),
('ads_ent_w_icn_enterprise_num_m', 'region', '地区', 'region_id', true, true, NULL, 'region', 2);

-- ==========================
-- 8. 最新时间配置
-- ==========================
INSERT INTO latest_time_config (indicator_id, table_id, frequency, latest_time_id, latest_date) VALUES
('I_RPA_ICN_RAE_SALARY_AMOUNT', 'ads_rpa_w_icn_recruit_salary_amount_m', 'M', '202406', '2024-06-30'),
('I_RPA_ICN_RAE_POSITION_NUM', 'ads_rpa_w_icn_edu_recruit_position_num_m', 'M', '202406', '2024-06-30'),
('I_ENT_ICN_RAE_ENTERPRISE_NUM', 'ads_ent_w_icn_enterprise_num_m', 'M', '202406', '2024-06-30');
