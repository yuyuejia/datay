package com.data.metadata.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
