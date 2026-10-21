package com.data.datafusion.repository;

import com.data.datafusion.domain.AlertRule;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, String> {
    List<AlertRule> findByEnabledTrue();

    List<AlertRule> findAllByOrderByCreateTimeDesc();

    Optional<AlertRule> findByCode(String code);

    boolean existsByCode(String code);
}
