package com.data.datafusion.repository;

import com.data.datafusion.config.SkipTenantFilter;
import com.data.datafusion.domain.JobInstance;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the JobInstance entity.
 */
@SuppressWarnings("unused")
@Repository
public interface JobInstanceRepository extends JpaRepository<JobInstance, Long> {
    @Query(
        value = "SELECT i.* FROM dp_job_instance i where i.status = :status  and i.job_code = :parentJobCode order by i.end_time DESC limit 1",
        nativeQuery = true
    )
    JobInstance findLastInstance(@Param("parentJobCode") String parentJobCode, @Param("status") String taskStatusSuccessful);

    @SkipTenantFilter
    Optional<JobInstance> findByInstanceCode(String instanceCode);

    List<JobInstance> findByStatus(String status);
    /**
     * 根据任务代码和状态查询任务实例
     *
     * @param jobCode 任务代码
     * @param status  任务状态
     * @return 任务实例列表
     */
    List<JobInstance> findByJobCodeAndStatus(String jobCode, String status);

    Page<JobInstance> findAllByJobCode(String jobCode, Pageable pageable);
}