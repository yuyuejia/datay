package com.data.metadata.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.Test;

/**
 * PostgreSQL 自动建表 DDL 统一使用小写表名和字段名。
 */
class PostgresqlConverterLowerCaseDdlTest {

    @Test
    void generatesLowerCaseTableAndColumnNames() {
        DatabaseConverter.register("postgresql", new PostgresqlConverter());

        TableMeta table = new TableMeta("MyTable");
        table.setSchema("public");
        table.setComment("测试表");

        ColumnMeta id = new ColumnMeta("ID", "BIGINT");
        id.setPrimaryKey(true);
        table.addColumn(id);

        ColumnMeta userName = new ColumnMeta("USER_NAME", "VARCHAR");
        userName.setLength(50);
        userName.setComment("用户名");
        table.addColumn(userName);

        String ddl = DatabaseConverter.generateTableDDL("postgresql", table);

        assertTrue(ddl.contains("CREATE TABLE public.mytable"), ddl);
        assertTrue(ddl.contains("\"id\" BIGINT"), ddl);
        assertTrue(ddl.contains("\"user_name\" VARCHAR(50)"), ddl);
        assertTrue(ddl.contains("CONSTRAINT pk_mytable PRIMARY KEY (\"id\")"), ddl);
        assertTrue(ddl.contains("COMMENT ON TABLE \"mytable\" IS '测试表'"), ddl);
        assertTrue(ddl.contains("COMMENT ON COLUMN \"mytable\".\"user_name\" IS '用户名'"), ddl);
    }
}
