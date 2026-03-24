package com.lowaltitude.agent.repository;

import com.lowaltitude.agent.entity.DimensionValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DimensionValueRepository extends JpaRepository<DimensionValue, Long> {
    
    List<DimensionValue> findByDimensionId(String dimensionId);
    
    Optional<DimensionValue> findByDimensionIdAndValueCode(String dimensionId, String valueCode);
    
    @Query("SELECT dv FROM DimensionValue dv WHERE dv.dimensionId = :dimensionId AND " +
           "(dv.valueName LIKE %:name% OR dv.synonyms LIKE %:name%)")
    List<DimensionValue> findByDimensionIdAndNameLike(@Param("dimensionId") String dimensionId, 
                                                       @Param("name") String name);
    
    @Query("SELECT dv FROM DimensionValue dv WHERE dv.dimensionId = 'region' AND " +
           "(dv.valueName = :name OR dv.synonyms LIKE %:name%)")
    Optional<DimensionValue> findRegionByName(@Param("name") String name);
    
    @Query("SELECT dv FROM DimensionValue dv WHERE dv.dimensionId = 'edu_level' AND " +
           "(dv.valueName = :name OR dv.synonyms LIKE %:name%)")
    Optional<DimensionValue> findEduLevelByName(@Param("name") String name);
}
