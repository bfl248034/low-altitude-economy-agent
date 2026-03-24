package com.lowaltitude.agent.repository;

import com.lowaltitude.agent.entity.DataSourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataSourceConfigRepository extends JpaRepository<DataSourceConfig, Long> {
    
    Optional<DataSourceConfig> findBySourceId(String sourceId);
    
    List<DataSourceConfig> findByIsActiveTrue();
    
    List<DataSourceConfig> findBySourceType(String sourceType);
}
