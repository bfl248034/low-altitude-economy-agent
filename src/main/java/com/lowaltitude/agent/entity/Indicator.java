package com.lowaltitude.agent.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "db_indicator")
public class Indicator {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "indicator_id", nullable = false, unique = true, length = 64)
    private String indicatorId;
    
    @Column(name = "indicator_name", nullable = false, length = 128)
    private String indicatorName;
    
    @Column(length = 32)
    private String unit;
    
    @Column(nullable = false, length = 10)
    private String frequency;
    
    @Column(name = "valid_measures", length = 256)
    private String validMeasures;
    
    @Column(name = "table_id", length = 128)
    private String tableId;
    
    @Column(columnDefinition = "TEXT")
    private String remark;
    
    @Column(length = 64)
    private String domain;
    
    @Column(length = 64)
    private String subdomain;
    
    @Column(length = 256)
    private String tags;
    
    @Column(name = "indexed")
    private Boolean indexed = false;
    
    @Column(name = "index_version")
    private Long indexVersion = 0L;
    
    @Column(name = "last_indexed_at")
    private LocalDateTime lastIndexedAt;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
