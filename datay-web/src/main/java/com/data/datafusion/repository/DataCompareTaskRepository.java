package com.data.datafusion.repository;

import com.data.datafusion.domain.DataCompareTask;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DataCompareTaskRepository extends JpaRepository<DataCompareTask, String>, JpaSpecificationExecutor<DataCompareTask> {
    Optional<DataCompareTask> findByJobId(String jobId);
}
