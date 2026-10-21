package com.data.datafusion.repository;

import com.data.datafusion.domain.AlertDelivery;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertDeliveryRepository extends JpaRepository<AlertDelivery, String> {
    List<AlertDelivery> findByEventIdOrderByCreateTimeDesc(String eventId);
}
