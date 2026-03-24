package com.lowaltitude.agent.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "dimension_values")
public class DimensionValue {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "dimension_id", nullable = false, length = 64)
    private String dimensionId;
    
    @Column(name = "dimension_name", length = 64)
    private String dimensionName;
    
    @Column(name = "value_code", nullable = false, length = 64)
    private String valueCode;
    
    @Column(name = "value_name", nullable = false, length = 128)
    private String valueName;
    
    @Column(length = 500)
    private String synonyms;
    
    @Column(name = "parent_code", length = 64)
    private String parentCode;
    
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
    
    @Column(name = "indexed")
    private Boolean indexed = false;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
