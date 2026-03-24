package com.lowaltitude.agent.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryDataTool {

    private final JdbcTemplate jdbcTemplate;

    @Tool(name = "queryData", description = "执行SQL查询并返回结果")
    public String queryData(@ToolParam(description = "SQL查询语句") String sql) {
        log.info("Executing SQL: {}", sql);
        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            return formatResults(results);
        } catch (Exception e) {
            log.error("SQL execution failed: {}", e.getMessage());
            return "查询失败: " + e.getMessage();
        }
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
            sb.setLength(sb.length() - 2); // 移除最后的逗号空格
            sb.append("\n");
        }
        
        return sb.toString();
    }
}
