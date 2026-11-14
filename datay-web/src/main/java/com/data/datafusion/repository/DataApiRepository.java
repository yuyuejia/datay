package com.data.datafusion.repository;

import com.data.datafusion.domain.DataApi;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link DataApi} entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DataApiRepository extends JpaRepository<DataApi, String>, JpaSpecificationExecutor<DataApi> {
    Optional<DataApi> findByCode(String code);
    Optional<DataApi> findByCodeAndIdNot(String code, String id);
    Optional<DataApi> findByDatasetId(String datasetId);
    Optional<DataApi> findByDatasetIdAndIdNot(String datasetId, String id);
    List<DataApi> findByDatasetIdIn(Collection<String> datasetIds);
    List<DataApi> findByDirectoryId(String directoryId);
}
