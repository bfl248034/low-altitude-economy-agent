package com.lowaltitude.agent.repository;

import com.lowaltitude.agent.entity.DataDimensionConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DataDimensionConfigRepository extends JpaRepository<DataDimensionConfig, Long> {
    
    List<DataDimensionConfig> findByTableId(String tableId);
}
