package com.data.metadata.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.Test;

class DorisConverterTest {

    private final DorisConverter converter = new DorisConverter();

    @Test
    void ignoresSqlServerDefaultConstraintForStringColumn() {
        // SQL Server 的 COLUMN_DEF 会返回 ((0))，Doris 建表时直接忽略默认值
        ColumnMeta column = new ColumnMeta("ddiscountname", "VARCHAR", 50, -1, -1);
        column.setNullable(false);
        column.setDefaultValue("((0))");

        assertEquals(
            "`ddiscountname` VARCHAR(50) NOT NULL",
            converter.generateColumnDDL(column)
        );
    }

    @Test
    void ignoresAllDefaultValues() {
        ColumnMeta empty = new ColumnMeta("remark", "VARCHAR", 50, -1, -1);
        empty.setDefaultValue("((''))");
        assertEquals("`remark` VARCHAR(50)", converter.generateColumnDDL(empty));

        ColumnMeta unicode = new ColumnMeta("status", "VARCHAR", 20, -1, -1);
        unicode.setDefaultValue("(N'待处理')");
        assertEquals("`status` VARCHAR(20)", converter.generateColumnDDL(unicode));

        ColumnMeta createdAt = new ColumnMeta("created_at", "DATETIME");
        createdAt.setDefaultValue("(getdate())");
        assertEquals("`created_at` DATETIME", converter.generateColumnDDL(createdAt));

        ColumnMeta amount = new ColumnMeta("amount", "DECIMAL", 0, 12, 2);
        amount.setDefaultValue("((0.00))");
        assertEquals("`amount` DECIMAL(12,2)", converter.generateColumnDDL(amount));

        ColumnMeta quantity = new ColumnMeta("quantity", "INT");
        quantity.setDefaultValue("((-1))");
        assertEquals("`quantity` INT", converter.generateColumnDDL(quantity));
    }

    @Test
    void upsertUsesPlainInsertForUniqueKeyTable() {
        // Doris 2.x 不支持 MySQL 的 ON DUPLICATE KEY UPDATE 语法；
        // UNIQUE KEY 表上整行 INSERT 本身就是 UPSERT。
        TableMeta table = new TableMeta("t");
        ColumnMeta id = new ColumnMeta("id", "BIGINT");
        id.setPrimaryKey(true);
        ColumnMeta name = new ColumnMeta("name", "VARCHAR", 50, -1, -1);
        table.addColumn(id);
        table.addColumn(name);

        String sql = converter.generateUpsertSQL(table);

        assertEquals("INSERT INTO t (`id`, `name`) VALUES (?, ?)", sql);
        assertFalse(sql.toUpperCase().contains("ON DUPLICATE KEY"), sql);
        assertFalse(sql.toUpperCase().contains("REPLACE INTO"), sql);
    }

    @Test
    void primaryKeyColumnsAreMovedToSchemaPrefix() {
        // 复现 "Key columns should be a ordered prefix of the schema" 报错场景：
        // 主键列 sType 位于普通列 didtype 之后，UNIQUE KEY 中 sType 不在列定义前缀位置上。
        TableMeta table = new TableMeta("t");
        ColumnMeta id = new ColumnMeta("id", "BIGINT");
        id.setPrimaryKey(true);
        ColumnMeta tenantId = new ColumnMeta("tenant_id", "BIGINT");
        tenantId.setPrimaryKey(true);
        ColumnMeta didType = new ColumnMeta("didtype", "VARCHAR", 50, -1, -1);
        ColumnMeta sType = new ColumnMeta("sType", "VARCHAR", 50, -1, -1);
        sType.setPrimaryKey(true);
        table.addColumn(id);
        table.addColumn(tenantId);
        table.addColumn(didType);
        table.addColumn(sType);

        String ddl = converter.generateTableDDL(table);

        // 主键列必须排在所有普通列之前，且保持原有相对顺序
        assertTrue(ddl.indexOf("`stype`") < ddl.indexOf("`didtype`"), ddl);
        assertTrue(ddl.contains("UNIQUE KEY (`id`, `tenant_id`, `stype`)"), ddl);
        assertTrue(ddl.indexOf("`id`") < ddl.indexOf("`stype`"), ddl);
        assertTrue(ddl.indexOf("`tenant_id`") < ddl.indexOf("`stype`"), ddl);
    }

    @Test
    void generatedTableDdlDoesNotContainDefaults() {
        TableMeta table = new TableMeta("discount");
        ColumnMeta id = new ColumnMeta("id", "BIGINT");
        id.setPrimaryKey(true);
        ColumnMeta name = new ColumnMeta("ddiscountname", "VARCHAR", 50, -1, -1);
        name.setNullable(false);
        name.setDefaultValue("((0))");
        table.addColumn(id);
        table.addColumn(name);

        String ddl = converter.generateTableDDL(table);

        assertFalse(ddl.toUpperCase().contains("DEFAULT"), ddl);
        assertFalse(ddl.contains("(("), ddl);
    }
}
