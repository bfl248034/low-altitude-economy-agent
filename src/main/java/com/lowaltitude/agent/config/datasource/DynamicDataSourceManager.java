package com.lowaltitude.agent.config.datasource;

import com.lowaltitude.agent.entity.DataSourceConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class DynamicDataSourceManager {

    private final Map<String, DataSource> dataSourceMap = new ConcurrentHashMap<>();
    private final Map<String, JdbcTemplate> jdbcTemplateMap = new ConcurrentHashMap<>();

    public JdbcTemplate getJdbcTemplate(DataSourceConfig config) {
        String sourceId = config.getSourceId();
        
        return jdbcTemplateMap.computeIfAbsent(sourceId, k -> {
            DataSource dataSource = createDataSource(config);
            dataSourceMap.put(sourceId, dataSource);
            return new JdbcTemplate(dataSource);
        });
    }

    private DataSource createDataSource(DataSourceConfig config) {
        HikariConfig hikariConfig = new HikariConfig();
        
        String jdbcUrl = buildJdbcUrl(config);
        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        
        hikariConfig.setMaximumPoolSize(5);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(10000);
        hikariConfig.setIdleTimeout(300000);
        hikariConfig.setMaxLifetime(600000);
        
        // 驱动类
        hikariConfig.setDriverClassName(getDriverClass(config.getSourceType()));
        
        log.info("Created DataSource for: {} ({}, {})", 
                config.getSourceId(), config.getSourceType(), jdbcUrl);
        
        return new HikariDataSource(hikariConfig);
    }

    private String buildJdbcUrl(DataSourceConfig config) {
        String type = config.getSourceType().toLowerCase();
        String host = config.getHost();
        Integer port = config.getPort();
        String database = config.getDatabaseName();
        
        return switch (type) {
            case "mysql" -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC", 
                    host, port != null ? port : 3306, database);
            case "h2" -> "jdbc:h2:mem:" + database;
            case "kylin" -> String.format("jdbc:kylin://%s:%d/%s", 
                    host, port != null ? port : 7070, database);
            default -> throw new IllegalArgumentException("Unsupported datasource type: " + type);
        };
    }

    private String getDriverClass(String sourceType) {
        return switch (sourceType.toLowerCase()) {
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "h2" -> "org.h2.Driver";
            default -> throw new IllegalArgumentException("Unknown driver for: " + sourceType);
        };
    }

    public void removeDataSource(String sourceId) {
        DataSource ds = dataSourceMap.remove(sourceId);
        jdbcTemplateMap.remove(sourceId);
        if (ds instanceof HikariDataSource hds) {
            hds.close();
            log.info("Closed DataSource: {}", sourceId);
        }
    }
}
