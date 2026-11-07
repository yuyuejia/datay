package com.data.datafusion.repository;

import com.data.datafusion.domain.DataQualityRule;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface DataQualityRuleRepository extends JpaRepository<DataQualityRule, String>, JpaSpecificationExecutor<DataQualityRule> {
    Optional<DataQualityRule> findByRuleCode(String ruleCode);
}
