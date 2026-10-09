package com.data.datafusion.repository;

import com.data.datafusion.domain.DataSource;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for the DataSource entity.
 */
@SuppressWarnings("unused")
@Repository
public interface DataSourceRepository extends JpaRepository<DataSource, String>, JpaSpecificationExecutor<DataSource> {
    /** 按「名称 + 类型 + 连接地址」定位同租户内已存在的数据源，用于资产包初始化的数据源复用。 */
    Optional<DataSource> findFirstByNameAndTypeAndUrl(String name, String type, String url);
}
