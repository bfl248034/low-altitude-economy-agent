-- 初始化数据

-- ==========================
-- 1. 数据源配置
-- ==========================
INSERT INTO db_data_source (source_id, source_name, source_type, host, port, database_name, username, password) VALUES
('ds_h2_demo', 'H2演示数据库', 'h2', 'mem', NULL, 'lowaltitude', 'sa', '');

-- ==========================
-- 2. 指标元数据
-- ==========================
INSERT INTO db_indicator (indicator_id, indicator_name, unit, frequency, valid_measures, table_id, remark, domain, subdomain, tags) VALUES
('I_RPA_ICN_RAE_SALARY_AMOUNT', '招聘岗位平均薪酬', '元', 'M', '当期，当期同比', 'ads_rpa_w_icn_recruit_salary_amount_m', '招聘岗位的平均薪资水平', '招聘就业', '薪资水平', '薪资,工资,薪酬,收入,待遇'),
('I_RPA_ICN_RAE_POSITION_NUM', '招聘岗位数量', '个', 'M', '当期，当期同比', 'ads_rpa_w_icn_edu_recruit_position_num_m', '企业发布的招聘岗位数量', '招聘就业', '招聘需求', '招聘,岗位,职位,用工'),
('I_ENT_ICN_RAE_ENTERPRISE_NUM', '存续企业数量', '家', 'M', '当期，当期同比', 'ads_ent_w_icn_enterprise_num_m', '低空经济领域存续企业数量', '企业主体', '企业数量', '企业,公司,主体');

-- ==========================
-- 3. 维度值
-- ==========================
-- 地区维度
INSERT INTO dimension_values (dimension_id, dimension_name, value_code, value_name, synonyms) VALUES
('region', '地区', '110000', '北京', '北京市,京城,首都'),
('region', '地区', '310000', '上海', '上海市,魔都'),
('region', '地区', '440100', '广州', '广州市,羊城'),
('region', '地区', '440300', '深圳', '深圳市,鹏城'),
('region', '地区', '330100', '杭州', '杭州市');

-- 学历维度
INSERT INTO dimension_values (dimension_id, dimension_name, value_code, value_name, synonyms) VALUES
('edu_level', '学历', '0', '不限', '不限学历'),
('edu_level', '学历', '1', '高中/中专/中技', '高中,中专'),
('edu_level', '学历', '2', '大专', '专科,大专'),
('edu_level', '学历', '3', '本科', '本科,学士'),
('edu_level', '学历', '4', '硕士', '硕士研究生'),
('edu_level', '学历', '5', '博士', '博士研究生');

-- 时间维度（简化示例）
INSERT INTO dimension_values (dimension_id, dimension_name, value_code, value_name) VALUES
('time', '时间', '202401', '2024年1月'),
('time', '时间', '202402', '2024年2月'),
('time', '时间', '202403', '2024年3月'),
('time', '时间', '202404', '2024年4月'),
('time', '时间', '202405', '2024年5月'),
('time', '时间', '202406', '2024年6月');
