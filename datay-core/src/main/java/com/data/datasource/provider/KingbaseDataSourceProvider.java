package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.metadata.DBType;

/**
 * 人大金仓 KingbaseES 数据源 Provider。
 *
 * <p>KingbaseES 在 PostgreSQL 兼容模式下使用 PostgreSQL 协议语义，库表元数据行为与
 * PostgreSQL 一致，因此复用 {@link PostgresqlDataSourceProvider}，仅将类型声明为
 * {@link DBType#KINGBASE}。
 *
 * <p>连接串形如 {@code jdbc:kingbase8://host:54321/database}，驱动类为
 * {@code com.kingbase8.Driver}。驱动未随平台预置，需由用户上传 kingbase8 jar（见
 * {@code DriverStore}），上传后保存到 {@code drivers/kingbase/default/}。
 *
 * <p>自动建表与写入 SQL 复用 PostgreSQL 方言，默认按未加引号标识符折叠为小写处理；
 * 建议数据库使用 PostgreSQL 兼容模式（而非 Oracle 兼容模式）。
 */
@DataSourceRegister("kingbase")
public class KingbaseDataSourceProvider extends PostgresqlDataSourceProvider {

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.KINGBASE);
    }
}
