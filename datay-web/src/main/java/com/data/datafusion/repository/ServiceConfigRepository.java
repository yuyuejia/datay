package com.data.datafusion.repository;

import com.data.datafusion.domain.ServiceConfig;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@SuppressWarnings("unused")
@Repository
public interface ServiceConfigRepository extends JpaRepository<ServiceConfig, Long> {

    Optional<ServiceConfig> findByDfGroupAndDfKey(String dfGroup, String dfKey);

    List<ServiceConfig> findByDfGroup(String dfGroup);

    void deleteByDfGroupAndDfKey(String dfGroup, String dfKey);
}