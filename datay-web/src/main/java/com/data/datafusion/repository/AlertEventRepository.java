package com.data.datafusion.repository;

import com.data.datafusion.domain.AlertEvent;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertEventRepository extends JpaRepository<AlertEvent, String>, JpaSpecificationExecutor<AlertEvent> {
    Optional<AlertEvent> findFirstByFingerprintAndStatusInOrderByLastSeenDesc(String fingerprint, Collection<String> statuses);

    List<AlertEvent> findByJobCodeAndStatusIn(String jobCode, Collection<String> statuses);

    long countByStatusIn(Collection<String> statuses);
}
