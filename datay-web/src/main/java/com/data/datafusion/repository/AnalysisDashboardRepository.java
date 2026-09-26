package com.data.datafusion.repository;

import com.data.datafusion.domain.AnalysisDashboard;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link AnalysisDashboard} entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AnalysisDashboardRepository extends JpaRepository<AnalysisDashboard, Long>, JpaSpecificationExecutor<AnalysisDashboard> {
    Optional<AnalysisDashboard> findByCode(String code);

    Optional<AnalysisDashboard> findByCodeAndIdNot(String code, Long id);
}
