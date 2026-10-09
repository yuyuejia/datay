package com.data.datafusion.repository;

import com.data.datafusion.domain.ETLComponent;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the ETLComponent entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ETLComponentRepository extends JpaRepository<ETLComponent, String> {}
