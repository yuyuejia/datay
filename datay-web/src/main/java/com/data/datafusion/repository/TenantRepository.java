package com.data.datafusion.repository;

import com.data.datafusion.domain.Tenant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
}