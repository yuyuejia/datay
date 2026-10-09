package com.data.datafusion.repository;

import com.data.datafusion.domain.ETLEdge;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the ETLEdge entity.
 */
@SuppressWarnings("unused")
@Repository
public interface ETLEdgeRepository extends JpaRepository<ETLEdge, String> {
    Optional<List<ETLEdge>> findAllByTaskId(String taskId);

    void deleteAllByTaskId(String taskId);
}
