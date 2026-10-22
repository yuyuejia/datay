package com.data.datafusion.repository;

import com.data.datafusion.domain.AnalysisDataset;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link AnalysisDataset} entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AnalysisDatasetRepository extends JpaRepository<AnalysisDataset, String>, JpaSpecificationExecutor<AnalysisDataset> {
    Optional<AnalysisDataset> findByCode(String code);

    Optional<AnalysisDataset> findByCodeAndIdNot(String code, String id);
}
