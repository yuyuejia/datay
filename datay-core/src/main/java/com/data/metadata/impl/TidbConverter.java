package com.data.metadata.impl;

import com.data.metadata.DBType;
import com.data.metadata.DatabaseConverter;

/**
 * TiDB 类型转换器。
 *
 * <p>TiDB 与 MySQL 的类型体系及 DDL/DML 语法一致，因此直接继承 {@link MysqlConverter}
 * 复用全部映射与语句生成逻辑，仅在 {@link DatabaseConverter} 中以 {@code tidb} 名称注册，
 * 保证按类型名 {@code TIDB} 生成 DDL / 写 SQL 时可用。
 */
public class TidbConverter extends MysqlConverter {

    static {
        DatabaseConverter.register(DBType.TIDB.name(), new TidbConverter());
    }
}
