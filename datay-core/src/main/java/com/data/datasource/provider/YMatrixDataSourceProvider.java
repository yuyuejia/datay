package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.metadata.DBType;

/**
 * YMatrix 数据源 Provider。
 *
 * <p>YMatrix 是 Greenplum 系超融合时序数据库，使用 PostgreSQL 协议与驱动，库表元数据行为与
 * PostgreSQL 一致，因此复用 {@link PostgresqlDataSourceProvider}，仅将类型声明为
 * {@link DBType#YMATRIX}。
 *
 * <p>连接串形如 {@code jdbc:postgresql://host:5432/database}，高可用场景可写
 * {@code jdbc:postgresql://master:5432,standby:5432/database}。自动建表与写入 SQL 复用
 * PostgreSQL 方言；其中 {@code ON CONFLICT} UPSERT 需 YMatrix 5.x 及以上版本。
 */
@DataSourceRegister("ymatrix")
public class YMatrixDataSourceProvider extends PostgresqlDataSourceProvider {

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.YMATRIX);
    }
}
