package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.metadata.DBType;

/**
 * Apache Avro 数据源 Provider，复用 {@link JdbcDataSourceProvider} 的 JDBC 标准能力。
 */
@DataSourceRegister("avro")
public class AvroDataSourceProvider extends JdbcDataSourceProvider {

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.AVRO);
    }
}
