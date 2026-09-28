package com.data.datafusion.repository;

import com.data.datafusion.domain.ETLTask;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the ETLTask entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ETLTaskRepository extends JpaRepository<ETLTask, Long>, JpaSpecificationExecutor<ETLTask> {
    Optional<ETLTask> findByJobId(Long jobId);
}
