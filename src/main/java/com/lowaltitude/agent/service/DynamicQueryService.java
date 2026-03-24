package com.lowaltitude.agent.service;

import com.lowaltitude.agent.config.datasource.DynamicDataSourceManager;
import com.lowaltitude.agent.entity.DataSourceConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicQueryService {

    private final DynamicDataSourceManager dataSourceManager;

    public List<Map<String, Object>> executeQuery(DataSourceConfig config, String sql) {
        log.info("Executing query on datasource [{}]: {}", config.getSourceId(), sql);
        
        try {
            JdbcTemplate jdbcTemplate = dataSourceManager.getJdbcTemplate(config);
            return jdbcTemplate.queryForList(sql);
        } catch (Exception e) {
            log.error("Query execution failed on datasource [{}]: {}", config.getSourceId(), e.getMessage());
            throw new RuntimeException("查询执行失败: " + e.getMessage(), e);
        }
    }

    public String executeQueryAsString(DataSourceConfig config, String sql) {
        List<Map<String, Object>> results = executeQuery(config, sql);
        return formatResults(results);
    }

    private String formatResults(List<Map<String, Object>> results) {
        if (results.isEmpty()) {
            return "未查询到数据";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("查询结果（共").append(results.size()).append("条）：\n\n");

        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> row = results.get(i);
            sb.append("[").append(i + 1).append("] ");
            row.forEach((key, value) -> {
                sb.append(key).append("=").append(value).append(", ");
            });
            if (sb.length() > 2) {
                sb.setLength(sb.length() - 2);
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
