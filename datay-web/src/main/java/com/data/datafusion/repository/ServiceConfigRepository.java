package com.data.datafusion.repository;

import com.data.datafusion.domain.ServiceConfig;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the ServiceConfig entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ServiceConfigRepository extends JpaRepository<ServiceConfig, Long> {}
