package com.lowaltitude.agent.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "db_data_source")
public class DataSourceConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "source_id", nullable = false, unique = true, length = 64)
    private String sourceId;
    
    @Column(name = "source_name", nullable = false, length = 128)
    private String sourceName;
    
    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;  // h2, mysql, kylin, clickhouse
    
    @Column(length = 256)
    private String host;
    
    private Integer port;
    
    @Column(name = "database_name", length = 64)
    private String databaseName;
    
    @Column(length = 64)
    private String username;
    
    @Column(length = 256)
    private String password;
    
    @Column(name = "connection_params", columnDefinition = "TEXT")
    private String connectionParams;  // JSON格式额外参数
    
    @Column(name = "is_active")
    private Boolean isActive = true;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
