package com.data.datafusion.repository;

import com.data.datafusion.domain.AppPackage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link AppPackage} entity.
 *
 * <p>资产包不做 Hibernate 租户过滤：系统预制包（{@code package_type=SYSTEM}）需要跨租户可见，
 * 因此租户隔离通过本接口的查询条件显式表达。
 */
@SuppressWarnings("unused")
@Repository
public interface AppPackageRepository extends JpaRepository<AppPackage, String>, JpaSpecificationExecutor<AppPackage> {
    Optional<AppPackage> findByCodeAndPackageType(String code, String packageType);

    Optional<AppPackage> findByCodeAndPackageTypeAndTenantId(String code, String packageType, String tenantId);

    List<AppPackage> findByPackageTypeOrderByIdAsc(String packageType);

    List<AppPackage> findByTenantIdOrderByIdDesc(String tenantId);
}
