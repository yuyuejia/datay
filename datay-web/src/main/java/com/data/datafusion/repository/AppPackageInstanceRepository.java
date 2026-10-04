package com.data.datafusion.repository;

import com.data.datafusion.domain.AppPackageInstance;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link AppPackageInstance} entity.
 */
@SuppressWarnings("unused")
@Repository
public interface AppPackageInstanceRepository extends JpaRepository<AppPackageInstance, Long> {
    Page<AppPackageInstance> findByTenantIdOrderByIdDesc(String tenantId, Pageable pageable);

    List<AppPackageInstance> findByTenantIdAndPackageIdOrderByIdDesc(String tenantId, Long packageId);
}
