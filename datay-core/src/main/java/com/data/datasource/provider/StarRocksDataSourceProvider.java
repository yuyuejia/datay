package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.metadata.DBType;

/**
 * StarRocks 数据源 Provider。
 *
 * <p>StarRocks 使用 MySQL 协议与驱动，库表元数据行为与 MySQL 一致，因此复用
 * {@link MysqlDataSourceProvider}，仅将类型声明为 {@link DBType#STARROCKS}。
 */
@DataSourceRegister("starrocks")
public class StarRocksDataSourceProvider extends MysqlDataSourceProvider {

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.STARROCKS);
    }
}
