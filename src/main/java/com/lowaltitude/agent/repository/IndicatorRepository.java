package com.lowaltitude.agent.repository;

import com.lowaltitude.agent.entity.Indicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IndicatorRepository extends JpaRepository<Indicator, Long> {
    
    Optional<Indicator> findByIndicatorId(String indicatorId);
    
    List<Indicator> findByTableId(String tableId);
    
    @Query("SELECT i FROM Indicator i WHERE " +
           "i.indicatorName LIKE %:keyword% OR " +
           "i.tags LIKE %:keyword% OR " +
           "i.remark LIKE %:keyword%")
    List<Indicator> searchByKeyword(@Param("keyword") String keyword);
    
    List<Indicator> findByDomain(String domain);
}
