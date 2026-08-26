package com.data.datafusion.repository;

import com.data.datafusion.domain.ETLNode;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the ETLNode entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ETLNodeRepository extends JpaRepository<ETLNode, Long> {
    Optional<List<ETLNode>> findAllByTaskId(String taskId);

    void deleteAllByTaskId(String taskId);
}
