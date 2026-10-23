package com.data.datafusion.repository;

import com.data.datafusion.domain.RoleDatasetScope;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link RoleDatasetScope} entity.
 */
@SuppressWarnings("unused")
@Repository
public interface RoleDatasetScopeRepository extends JpaRepository<RoleDatasetScope, String> {
    List<RoleDatasetScope> findByRoleName(String roleName);

    List<RoleDatasetScope> findByRoleNameInAndDatasetIdAndEnabledTrue(Collection<String> roleNames, String datasetId);

    void deleteByDatasetId(String datasetId);
}
