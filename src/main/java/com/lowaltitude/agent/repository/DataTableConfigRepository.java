package com.lowaltitude.agent.repository;

import com.lowaltitude.agent.entity.DataTableConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DataTableConfigRepository extends JpaRepository<DataTableConfig, String> {
    
    Optional<DataTableConfig> findByTableId(String tableId);
    
    List<DataTableConfig> findBySourceId(String sourceId);
    
    List<DataTableConfig> findByIsActiveTrue();
}
