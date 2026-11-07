package com.data.datafusion.repository;

import com.data.datafusion.domain.DataQualityTask;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DataQualityTaskRepository extends JpaRepository<DataQualityTask, String>, JpaSpecificationExecutor<DataQualityTask> {
    Optional<DataQualityTask> findByJobId(String jobId);

    Optional<DataQualityTask> findFirstByTaskName(String taskName);
}
