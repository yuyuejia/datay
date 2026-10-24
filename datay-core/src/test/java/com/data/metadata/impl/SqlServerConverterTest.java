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
    void mapsAllSqlServerPhysicalTypes() {
        SqlServerConverter converter = new SqlServerConverter();
        assertEquals("TINYINT", converter.toTargetColumnType(new ColumnMeta("c", "tinyint")).getType());
        assertEquals("SMALLINT", converter.toTargetColumnType(new ColumnMeta("c", "smallint")).getType());
        assertEquals("INT", converter.toTargetColumnType(new ColumnMeta("c", "int")).getType());
        assertEquals("BIGINT", converter.toTargetColumnType(new ColumnMeta("c", "bigint")).getType());
        assertEquals("BIT", converter.toTargetColumnType(new ColumnMeta("c", "bit")).getType());
        assertEquals("DECIMAL", converter.toTargetColumnType(new ColumnMeta("c", "decimal")).getType());
        assertEquals("DECIMAL", converter.toTargetColumnType(new ColumnMeta("c", "numeric")).getType());
        assertEquals("DECIMAL", converter.toTargetColumnType(new ColumnMeta("c", "money")).getType());
        assertEquals("DECIMAL", converter.toTargetColumnType(new ColumnMeta("c", "smallmoney")).getType());
        assertEquals("REAL", converter.toTargetColumnType(new ColumnMeta("c", "real")).getType());
        assertEquals("FLOAT", converter.toTargetColumnType(new ColumnMeta("c", "float")).getType());
        assertEquals("NVARCHAR", converter.toTargetColumnType(new ColumnMeta("c", "char")).getType());
        assertEquals("NCHAR", converter.toTargetColumnType(new ColumnMeta("c", "nchar")).getType());
        assertEquals("NVARCHAR", converter.toTargetColumnType(new ColumnMeta("c", "varchar")).getType());
        assertEquals("NVARCHAR", converter.toTargetColumnType(new ColumnMeta("c", "nvarchar")).getType());
        assertEquals("NVARCHAR(MAX)", converter.toTargetColumnType(new ColumnMeta("c", "text")).getType());
        assertEquals("NVARCHAR(MAX)", converter.toTargetColumnType(new ColumnMeta("c", "ntext")).getType());
        assertEquals("DATE", converter.toTargetColumnType(new ColumnMeta("c", "date")).getType());
        assertEquals("TIME", converter.toTargetColumnType(new ColumnMeta("c", "time")).getType());
        assertEquals("DATETIME2", converter.toTargetColumnType(new ColumnMeta("c", "datetime")).getType());
        assertEquals("DATETIME2", converter.toTargetColumnType(new ColumnMeta("c", "datetime2")).getType());
        assertEquals("DATETIME2", converter.toTargetColumnType(new ColumnMeta("c", "smalldatetime")).getType());
        assertEquals("DATETIMEOFFSET", converter.toTargetColumnType(new ColumnMeta("c", "datetimeoffset")).getType());
        assertEquals("VARBINARY", converter.toTargetColumnType(new ColumnMeta("c", "binary")).getType());
        assertEquals("VARBINARY", converter.toTargetColumnType(new ColumnMeta("c", "varbinary")).getType());
        assertEquals("VARBINARY(MAX)", converter.toTargetColumnType(new ColumnMeta("c", "image")).getType());
        assertEquals("UNIQUEIDENTIFIER", converter.toTargetColumnType(new ColumnMeta("c", "uniqueidentifier")).getType());
        assertEquals("XML", converter.toTargetColumnType(new ColumnMeta("c", "xml")).getType());
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

    @Test
    void allLogicalTypesMapToSupportedSqlServerTypes() {
        SqlServerConverter converter = new SqlServerConverter();
        java.util.Set<String> supported = java.util.Set.of(
            "TINYINT",
            "SMALLINT",
            "INT",
            "BIGINT",
            "BIT",
            "REAL",
            "FLOAT",
            "DECIMAL",
            "NCHAR",
            "NVARCHAR",
            "NVARCHAR(MAX)",
            "VARBINARY",
            "VARBINARY(MAX)",
            "DATE",
            "TIME",
            "DATETIME2",
            "DATETIMEOFFSET",
            "UNIQUEIDENTIFIER",
            "XML"
        );
        for (com.data.metadata.AllDataType type : com.data.metadata.AllDataType.values()) {
            String mapped = converter.toTargetColumnType(new ColumnMeta("c", type.name())).getType();
            assertTrue(supported.contains(mapped), type + " -> " + mapped);
        }
    }
}
