package com.lowaltitude.agent.repository;

import com.lowaltitude.agent.entity.LatestTimeConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LatestTimeConfigRepository extends JpaRepository<LatestTimeConfig, Long> {
    
    Optional<LatestTimeConfig> findByIndicatorId(String indicatorId);
}
