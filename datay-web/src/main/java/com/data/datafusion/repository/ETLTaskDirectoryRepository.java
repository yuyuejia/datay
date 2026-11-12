package com.data.datafusion.repository;

import com.data.datafusion.domain.ETLTaskDirectory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the {@link ETLTaskDirectory} entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ETLTaskDirectoryRepository extends JpaRepository<ETLTaskDirectory, String> {
    List<ETLTaskDirectory> findByTaskTypeOrderBySortOrderAscNameAsc(String taskType);

    List<ETLTaskDirectory> findByTaskTypeAndParentIdOrderBySortOrderAscNameAsc(String taskType, String parentId);

    List<ETLTaskDirectory> findByTaskTypeAndParentIdIsNullOrderBySortOrderAscNameAsc(String taskType);

    List<ETLTaskDirectory> findByParentIdOrderBySortOrderAsc(String parentId);

    Optional<ETLTaskDirectory> findFirstByTaskTypeAndParentIdAndName(String taskType, String parentId, String name);

    Optional<ETLTaskDirectory> findFirstByTaskTypeAndParentIdIsNullAndName(String taskType, String name);
}
