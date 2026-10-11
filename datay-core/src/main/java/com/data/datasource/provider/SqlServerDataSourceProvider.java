package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.metadata.DBType;

/**
 * Microsoft SQL Server 数据源 Provider，复用 {@link JdbcDataSourceProvider} 的 JDBC 标准能力。
 */
@DataSourceRegister("sqlserver")
public class SqlServerDataSourceProvider extends JdbcDataSourceProvider {

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.SQLSERVER);
    }
}
