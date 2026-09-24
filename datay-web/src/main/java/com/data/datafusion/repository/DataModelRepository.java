package com.data.datafusion.repository;

import com.data.datafusion.domain.DataModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

@SuppressWarnings("unused")
@Repository
public interface DataModelRepository extends JpaRepository<DataModel, Long> {
    List<DataModel> findByDirectoryId(Long directoryId);
    List<DataModel> findByModelType(String modelType);
    Optional<DataModel> findFirstByCode(String code);
}