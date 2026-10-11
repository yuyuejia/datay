package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.metadata.DBType;

/**
 * Greenplum 数据源 Provider。
 *
 * <p>Greenplum 使用 PostgreSQL 协议与驱动，库表元数据行为与 PostgreSQL 一致，因此复用
 * {@link PostgresqlDataSourceProvider}，仅将类型声明为 {@link DBType#GREENPLUM}。
 */
@DataSourceRegister("greenplum")
public class GreenplumDataSourceProvider extends PostgresqlDataSourceProvider {

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.GREENPLUM);
    }
}
