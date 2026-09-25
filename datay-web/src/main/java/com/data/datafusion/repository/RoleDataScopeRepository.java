package com.data.datafusion.repository;

import com.data.datafusion.domain.RoleDataScope;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link RoleDataScope} entity.
 */
@SuppressWarnings("unused")
@Repository
public interface RoleDataScopeRepository extends JpaRepository<RoleDataScope, Long> {
    List<RoleDataScope> findByRoleName(String roleName);

    List<RoleDataScope> findByRoleNameAndEnabledTrue(String roleName);

    List<RoleDataScope> findByRoleNameInAndEnabledTrue(Collection<String> roleNames);
}
