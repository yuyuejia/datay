package com.data.datafusion.repository;

import com.data.datafusion.domain.DataApiCallLog;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link DataApiCallLog} entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DataApiCallLogRepository extends JpaRepository<DataApiCallLog, String>, JpaSpecificationExecutor<DataApiCallLog> {
    long deleteByApiId(String apiId);

    @Query(
        """
        select l.statDate as statDate,
               count(l) as totalCount,
               sum(case when l.status = 'SUCCESS' then 1 else 0 end) as successCount,
               sum(case when l.status = 'SUCCESS' then 0 else 1 end) as failureCount,
               sum(l.durationMs) as totalDurationMs,
               max(l.durationMs) as maxDurationMs
        from DataApiCallLog l
        where l.apiId = :apiId and l.statDate between :startDate and :endDate
        group by l.statDate
        order by l.statDate asc
        """
    )
    List<DailyStat> findDailyStats(
        @Param("apiId") String apiId,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /** 按天聚合的调用统计投影。 */
    interface DailyStat {
        LocalDate getStatDate();

        Long getTotalCount();

        Long getSuccessCount();

        Long getFailureCount();

        Long getTotalDurationMs();

        Long getMaxDurationMs();
    }
}
