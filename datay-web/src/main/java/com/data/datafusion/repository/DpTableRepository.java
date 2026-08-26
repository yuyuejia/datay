package com.data.datafusion.repository;

import com.data.datafusion.domain.DpTable;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the DpTable entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DpTableRepository extends JpaRepository<DpTable, Long> {}
