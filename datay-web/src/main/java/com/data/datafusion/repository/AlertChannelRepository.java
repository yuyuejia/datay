package com.data.datafusion.repository;

import com.data.datafusion.domain.AlertChannel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertChannelRepository extends JpaRepository<AlertChannel, String> {
    List<AlertChannel> findByEnabledTrue();

    Optional<AlertChannel> findByCode(String code);

    boolean existsByCode(String code);
}
