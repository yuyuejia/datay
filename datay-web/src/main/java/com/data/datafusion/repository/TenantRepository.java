package com.data.datafusion.repository;

import com.data.datafusion.domain.Tenant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    Optional<Tenant> findByCode(String code);

    @Query("SELECT t FROM Tenant t JOIN t.users u WHERE u.id = :userId ORDER BY t.id ASC")
    List<Tenant> findByUserId(@Param("userId") Long userId, Pageable pageable);

    default Optional<Tenant> findFirstTenantByUserId(Long userId) {
        return findByUserId(userId, Pageable.ofSize(1)).stream().findFirst();
    }

    @Query("SELECT t FROM Tenant t JOIN t.users u WHERE u.id = :userId")
    List<Tenant> findAllByUserId(@Param("userId") Long userId);

    @Query(value = """
        SELECT t.* FROM jhi_tenant t
        INNER JOIN jhi_user_tenant ut ON ut.tenant_id = t.id
        WHERE ut.user_id = :userId
        ORDER BY ut.is_default DESC, t.id ASC
        LIMIT 1
        """, nativeQuery = true)
    Optional<Tenant> findDefaultTenantByUserId(@Param("userId") Long userId);

    @Transactional
    @Modifying
    @Query(value = "UPDATE jhi_user_tenant SET is_default = false WHERE user_id = :userId", nativeQuery = true)
    void resetDefaultForUser(@Param("userId") Long userId);

    @Transactional
    @Modifying
    @Query(value = "UPDATE jhi_user_tenant SET is_default = true WHERE user_id = :userId AND tenant_id = :tenantId", nativeQuery = true)
    void setDefaultTenantForUser(@Param("userId") Long userId, @Param("tenantId") Long tenantId);
}