package com.data.metadata.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.Test;

class SqlServerConverterTest {

    private TableMeta sampleTable() {
        TableMeta table = new TableMeta("customers");
        table.setSchema("dbo");
        ColumnMeta id = new ColumnMeta("id", "INT");
        id.setPrimaryKey(true);
        id.setNullable(false);
        ColumnMeta name = new ColumnMeta("name", "NVARCHAR");
        name.setLength(100);
        name.setNullable(false);
        ColumnMeta amount = new ColumnMeta("amount", "DECIMAL");
        amount.setPrecision(12);
        amount.setScale(2);
        table.addColumn(id);
        table.addColumn(name);
        table.addColumn(amount);
        return table;
    }

    @Test
    void typeConversionHandlesSqlServerTypeNames() {
        SqlServerConverter converter = new SqlServerConverter();
        assertEquals("INT", converter.toTargetColumnType(new ColumnMeta("id", "int identity")).getType());
        assertEquals("DATETIME2", converter.toTargetColumnType(new ColumnMeta("created_at", "datetime2")).getType());
        assertEquals("NVARCHAR(MAX)", converter.toTargetColumnType(new ColumnMeta("note", "ntext")).getType());
    }

    @Test
    void generatesInsertAndDdl() {
        SqlServerConverter converter = new SqlServerConverter();
        assertEquals(
            "INSERT INTO [dbo].[customers] ([id], [name], [amount]) VALUES (?, ?, ?)",
            converter.generateInsertSQL(sampleTable())
        );
        String ddl = converter.generateTableDDL(sampleTable());
        assertTrue(ddl.contains("CREATE TABLE [dbo].[customers]"));
        assertTrue(ddl.contains("[id] INT NOT NULL"));
        assertTrue(ddl.contains("[name] NVARCHAR(100) NOT NULL"));
        assertTrue(ddl.contains("[amount] DECIMAL(12,2)"));
        assertTrue(ddl.contains("PRIMARY KEY ([id])"));
    }

    @Test
    void generatesMergeAndDelete() {
        SqlServerConverter converter = new SqlServerConverter();
        String merge = converter.generateUpsertSQL(sampleTable());
        assertTrue(merge.startsWith("MERGE INTO [dbo].[customers] AS t"));
        assertTrue(merge.contains("WHEN MATCHED THEN UPDATE SET t.[name] = s.[name]"));
        assertTrue(merge.contains("WHEN NOT MATCHED THEN INSERT"));
        assertEquals("DELETE FROM [dbo].[customers] WHERE [id] = ?", converter.generateDeleteSQL(sampleTable()));
    }

    @Test
    void registersInDatabaseConverter() {
        assertEquals("INT", DatabaseConverter.convert("duckdb", "sqlserver", new ColumnMeta("id", "INTEGER")).getType());
    }
}
