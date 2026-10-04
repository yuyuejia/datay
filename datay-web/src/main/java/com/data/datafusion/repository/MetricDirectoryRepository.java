package com.data.datafusion.repository;

import com.data.datafusion.domain.MetricDirectory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link MetricDirectory} entity.
 */
@SuppressWarnings("unused")
@Repository
public interface MetricDirectoryRepository extends JpaRepository<MetricDirectory, Long> {
    List<MetricDirectory> findByParentIdOrderBySortOrderAsc(Long parentId);
    List<MetricDirectory> findByParentIdIsNullOrderBySortOrderAsc();
    Optional<MetricDirectory> findFirstByNameAndParentId(String name, Long parentId);
    Optional<MetricDirectory> findFirstByNameAndParentIdIsNull(String name);
}
