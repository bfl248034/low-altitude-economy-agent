package com.lowaltitude.agent.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "db_data_dimension")
public class DataDimensionConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "table_id", nullable = false, length = 128)
    private String tableId;
    
    @Column(name = "dimension_id", nullable = false, length = 64)
    private String dimensionId;
    
    @Column(name = "dimension_name", length = 64)
    private String dimensionName;
    
    @Column(name = "dimension_code", length = 64)
    private String dimensionCode;  // 表中的列名
    
    @Column(name = "is_common")
    private Boolean isCommon = false;
    
    @Column(name = "is_required")
    private Boolean isRequired = false;
    
    @Column(name = "default_value", length = 64)
    private String defaultValue;
    
    @Column(name = "dimension_type", length = 20)
    private String dimensionType;  // time, region, enum
    
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
}
