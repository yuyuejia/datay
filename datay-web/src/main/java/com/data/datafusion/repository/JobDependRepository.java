package com.data.datafusion.repository;

import com.data.datafusion.domain.JobDepend;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the JobDepend entity.
 */
@SuppressWarnings("unused")
@Repository
public interface JobDependRepository extends JpaRepository<JobDepend, String> {
    List<JobDepend> findByChildJobCode(String childJobCode);

    List<JobDepend> findByParentJobCode(String jobCode);
}
