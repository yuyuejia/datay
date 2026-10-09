package com.data.datafusion.repository;

import com.data.datafusion.domain.DataSyncTableConfig;
import com.data.datafusion.service.dto.DataSyncTableConfigDTO;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the DataSyncTableConfig entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DataSyncTableConfigRepository extends JpaRepository<DataSyncTableConfig, String> {
    List<DataSyncTableConfig> findAllBySyncTask(String taskId);

    void deleteAllBySyncTask(String taskId);
}
