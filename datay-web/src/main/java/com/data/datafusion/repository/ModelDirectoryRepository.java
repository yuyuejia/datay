package com.data.datafusion.repository;

import com.data.datafusion.domain.ModelDirectory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

@SuppressWarnings("unused")
@Repository
public interface ModelDirectoryRepository extends JpaRepository<ModelDirectory, Long> {
    List<ModelDirectory> findByParentIdOrderBySortOrderAsc(Long parentId);
    List<ModelDirectory> findByParentIdIsNullOrderBySortOrderAsc();
    Optional<ModelDirectory> findFirstByNameAndParentId(String name, Long parentId);
    Optional<ModelDirectory> findFirstByNameAndParentIdIsNull(String name);
}