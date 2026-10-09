package com.data.datafusion.repository;

import com.data.datafusion.domain.ModelField;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

@SuppressWarnings("unused")
@Repository
public interface ModelFieldRepository extends JpaRepository<ModelField, String> {
    List<ModelField> findByModelIdOrderBySortOrderAsc(String modelId);
    void deleteByModelId(String modelId);
}