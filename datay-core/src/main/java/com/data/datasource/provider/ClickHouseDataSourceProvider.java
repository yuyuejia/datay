package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.metadata.DBType;

/**
 * ClickHouse 数据源 Provider，复用 {@link JdbcDataSourceProvider} 的 JDBC 标准能力。
 */
@DataSourceRegister("clickhouse")
public class ClickHouseDataSourceProvider extends JdbcDataSourceProvider {

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.CLICKHOUSE);
    }
}
