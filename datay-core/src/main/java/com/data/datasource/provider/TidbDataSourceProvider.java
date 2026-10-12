package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.metadata.DBType;

/**
 * TiDB 数据源 Provider。
 *
 * <p>TiDB 与 MySQL 线协议、类型体系及库表元数据完全兼容，因此复用 {@link MysqlDataSourceProvider}
 * 的全部能力（以 catalog 作为「数据库」、MySQL 驱动连接参数等），仅将类型声明为 {@link DBType#TIDB}。
 * 连接 URL 使用 {@code jdbc:mysql://} 模板，默认端口 {@code 4000}。
 */
@DataSourceRegister("tidb")
public class TidbDataSourceProvider extends MysqlDataSourceProvider {

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.TIDB);
    }
}
