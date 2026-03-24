package com.lowaltitude.agent.tool;

import com.lowaltitude.agent.entity.DataSourceConfig;
import com.lowaltitude.agent.service.DynamicQueryService;
import com.lowaltitude.agent.service.MetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicQueryTool {

    private final MetadataService metadataService;
    private final DynamicQueryService dynamicQueryService;

    @Tool(name = "executeQueryOnDataSource", description = "在指定数据源上执行SQL查询")
    public String executeQueryOnDataSource(
            @ToolParam(description = "数据源ID，如'ds_h2_demo'") String sourceId,
            @ToolParam(description = "要执行的SQL语句") String sql) {
        log.info("Executing query on datasource [{}]: {}", sourceId, sql);
        
        DataSourceConfig config = metadataService.getDataSource(sourceId)
                .orElseThrow(() -> new RuntimeException("数据源不存在: " + sourceId));
        
        return dynamicQueryService.executeQueryAsString(config, sql);
    }
}
