package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.metadata.DBType;

/**
 * GaussDB / openGauss 数据源 Provider。
 *
 * <p>GaussDB 使用 PostgreSQL 协议与驱动，库表元数据行为与 PostgreSQL 一致，因此复用
 * {@link PostgresqlDataSourceProvider}，仅将类型声明为 {@link DBType#GAUSSDB}。
 */
@DataSourceRegister("gaussdb")
public class GaussDbDataSourceProvider extends PostgresqlDataSourceProvider {

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.GAUSSDB);
    }
}
