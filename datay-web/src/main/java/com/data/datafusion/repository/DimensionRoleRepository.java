package com.data.datafusion.repository;

import com.data.datafusion.domain.DimensionRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link DimensionRole} entity.
 */
@Repository
public interface DimensionRoleRepository extends JpaRepository<DimensionRole, String> {
    List<DimensionRole> findByDimensionModelIdOrderBySortOrderAscCodeAsc(String dimensionModelId);

    Optional<DimensionRole> findByDimensionModelIdAndCodeIgnoreCase(String dimensionModelId, String code);

    boolean existsByDimensionModelIdAndCodeIgnoreCase(String dimensionModelId, String code);

    boolean existsByDimensionModelIdAndCodeIgnoreCaseAndIdNot(String dimensionModelId, String code, String id);

    void deleteByDimensionModelId(String dimensionModelId);
}
