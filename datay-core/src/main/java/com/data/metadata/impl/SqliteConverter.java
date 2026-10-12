package com.data.metadata.impl;

import com.data.metadata.ColumnMeta;
import com.data.metadata.DBType;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite 类型/DDL 转换器。
 *
 * <p>SQLite 类型体系宽松（类型亲和性），常用类型映射与 MySQL 一致，因此继承 {@link MysqlConverter}
 * 复用字段类型映射与写 SQL 生成；仅覆写建表 DDL —— SQLite 不支持列/表 {@code COMMENT} 语法。
 */
public class SqliteConverter extends MysqlConverter {

    static {
        DatabaseConverter.register(DBType.SQLITE.name(), new SqliteConverter());
    }

    @Override
    public String generateTableDDL(TableMeta table) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE ");
        if (table.getSchema() != null && !table.getSchema().trim().isEmpty()) {
            ddl.append(table.getSchema()).append(".");
        }
        ddl.append(table.getTable().toLowerCase()).append(" (\n");

        List<String> parts = new ArrayList<>();
        List<String> primaryKeys = new ArrayList<>();
        for (ColumnMeta column : table.columns()) {
            parts.add("  " + generateColumnDDL(column));
            if (column.isPrimaryKey()) {
                primaryKeys.add("`" + column.getName() + "`");
            }
        }
        if (!primaryKeys.isEmpty()) {
            parts.add("  PRIMARY KEY (" + String.join(", ", primaryKeys) + ")");
        }
        ddl.append(String.join(",\n", parts));
        ddl.append("\n)");
        return ddl.toString();
    }
}
