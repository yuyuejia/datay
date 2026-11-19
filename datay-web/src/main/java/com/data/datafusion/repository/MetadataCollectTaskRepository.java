package com.data.datafusion.repository;

import com.data.datafusion.domain.MetadataCollectTask;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface MetadataCollectTaskRepository
    extends JpaRepository<MetadataCollectTask, String>, JpaSpecificationExecutor<MetadataCollectTask> {
    Optional<MetadataCollectTask> findByDataSourceId(String dataSourceId);

    List<MetadataCollectTask> findAllByDataSourceId(String dataSourceId);

    Optional<MetadataCollectTask> findByJobId(String jobId);

    void deleteByDataSourceId(String dataSourceId);
}
