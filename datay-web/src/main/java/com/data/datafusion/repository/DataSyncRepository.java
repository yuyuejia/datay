package com.data.datafusion.repository;

import com.data.datafusion.domain.DataSync;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the DataSync entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DataSyncRepository extends JpaRepository<DataSync, Long> {}
