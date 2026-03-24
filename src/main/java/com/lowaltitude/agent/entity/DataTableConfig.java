package com.lowaltitude.agent.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "db_data_table")
public class DataTableConfig {
    
    @Id
    @Column(name = "table_id", length = 64)
    private String tableId;
    
    @Column(name = "table_name", nullable = false, length = 128)
    private String tableName;
    
    @Column(name = "table_alias", length = 128)
    private String tableAlias;
    
    @Column(name = "source_id", nullable = false, length = 64)
    private String sourceId;  // 所属数据源
    
    @Column(name = "database_name", length = 64)
    private String databaseName;
    
    @Column(name = "schema_name", length = 64)
    private String schemaName;
    
    @Column(name = "table_type", length = 20)
    private String tableType = "fact";
    
    @Column(length = 500)
    private String description;
    
    @Column(name = "time_column", length = 64)
    private String timeColumn = "time_id";
    
    @Column(name = "region_column", length = 64)
    private String regionColumn = "region_id";
    
    @Column(name = "region_level_column", length = 64)
    private String regionLevelColumn = "region_level_num";
    
    @Column(name = "value_column", length = 64)
    private String valueColumn = "fact_value";
    
    @Column(name = "indicator_column", length = 64)
    private String indicatorColumn = "indicator_id";
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
