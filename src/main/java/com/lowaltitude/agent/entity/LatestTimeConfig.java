package com.lowaltitude.agent.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 指标最新时间配置 - 记录每个指标的最大数据时间
 */
@Data
@Entity
@Table(name = "latest_time_config")
public class LatestTimeConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "indicator_id", nullable = false, unique = true, length = 64)
    private String indicatorId;
    
    @Column(name = "table_id", nullable = false, length = 128)
    private String tableId;
    
    @Column(name = "frequency", nullable = false, length = 10)
    private String frequency;  // D/W/M/Q/Y
    
    @Column(name = "latest_time_id", nullable = false, length = 20)
    private String latestTimeId;  // 如：202406
    
    @Column(name = "latest_date", nullable = false)
    private String latestDate;  // yyyy-MM-dd 格式
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
