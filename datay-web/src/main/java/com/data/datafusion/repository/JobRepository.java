package com.data.datafusion.repository;

import com.data.datafusion.domain.Job;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the Job entity.
 */
@SuppressWarnings("unused")
@Repository
public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {
    List<Job> findByStatus(String status);

    List<Job> findByTypeOrderByIdAsc(String type);

    Optional<Job> findFirstByJobName(String jobName);
}
