package com.data.datafusion.repository;

import com.data.datafusion.domain.ModelDirectory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

@SuppressWarnings("unused")
@Repository
public interface ModelDirectoryRepository extends JpaRepository<ModelDirectory, String> {
    List<ModelDirectory> findByParentIdOrderBySortOrderAsc(String parentId);
    List<ModelDirectory> findByParentIdIsNullOrderBySortOrderAsc();
    Optional<ModelDirectory> findFirstByNameAndParentId(String name, String parentId);
    Optional<ModelDirectory> findFirstByNameAndParentIdIsNull(String name);
}