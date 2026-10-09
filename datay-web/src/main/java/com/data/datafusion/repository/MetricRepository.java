package com.data.datafusion.repository;

import com.data.datafusion.domain.Metric;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link Metric} entity.
 */
@SuppressWarnings("unused")
@Repository
public interface MetricRepository extends JpaRepository<Metric, String>, JpaSpecificationExecutor<Metric> {
    Optional<Metric> findByCode(String code);
    Optional<Metric> findFirstByName(String name);
    Optional<Metric> findByCodeAndIdNot(String code, String id);
    List<Metric> findByDirectoryId(String directoryId);
    List<Metric> findByMetricType(String metricType);
}
