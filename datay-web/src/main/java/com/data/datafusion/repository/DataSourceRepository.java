package com.data.datafusion.repository;

import com.data.datafusion.domain.DataSource;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the DataSource entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DataSourceRepository extends JpaRepository<DataSource, Long> {}
