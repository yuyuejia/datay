package com.data.metadata.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.Test;

class DuckLakeConverterTest {

    @Test
    void mapsSqlServerPhysicalTypesWithoutDegradingToVarchar() {
        DuckLakeConverter converter = new DuckLakeConverter();
        assertEquals("FLOAT", converter.toTargetColumnType(new ColumnMeta("c", "real")).getType());
        assertEquals("TIMESTAMP", converter.toTargetColumnType(new ColumnMeta("c", "datetime2")).getType());
        assertEquals("TIMESTAMP", converter.toTargetColumnType(new ColumnMeta("c", "smalldatetime")).getType());
        assertEquals("TIMESTAMP", converter.toTargetColumnType(new ColumnMeta("c", "datetimeoffset")).getType());
        assertEquals("VARCHAR", converter.toTargetColumnType(new ColumnMeta("c", "ntext")).getType());
        assertEquals("BLOB", converter.toTargetColumnType(new ColumnMeta("c", "image")).getType());
    }

    @Test
    void convertsSqlServerPhysicalTypesThroughDatabaseConverter() {
        assertEquals("FLOAT", DatabaseConverter.convert("sqlserver", "ducklake", new ColumnMeta("c", "real")).getType());
        assertEquals("TIMESTAMP", DatabaseConverter.convert("sqlserver", "ducklake", new ColumnMeta("c", "datetime2")).getType());
        assertEquals(
            "TIMESTAMP",
            DatabaseConverter.convert("sqlserver", "ducklake", new ColumnMeta("c", "smalldatetime")).getType()
        );
        assertEquals(
            "TIMESTAMP",
            DatabaseConverter.convert("sqlserver", "ducklake", new ColumnMeta("c", "datetimeoffset")).getType()
        );
    }

    @Test
    void keepsDecimalPrecisionWhenConvertingSqlServerDecimal() {
        ColumnMeta decimal = new ColumnMeta("amount", "DECIMAL");
        decimal.setPrecision(18);
        decimal.setScale(4);
        ColumnMeta converted = DatabaseConverter.convert("sqlserver", "ducklake", decimal);
        assertEquals("DECIMAL", converted.getType());
        assertEquals(18, converted.getPrecision());
        assertEquals(4, converted.getScale());
    }

    @Test
    void generatesDuckLakeCatalogQualifiedDdl() {
        TableMeta table = new TableMeta("customers");
        table.setCatalog("ducklake");
        table.setSchema("main");
        ColumnMeta id = new ColumnMeta("id", "INT");
        id.setNullable(false);
        table.addColumn(id);
        ColumnMeta amount = new ColumnMeta("amount", "DECIMAL");
        amount.setPrecision(18);
        amount.setScale(4);
        table.addColumn(amount);

        String ddl = new DuckLakeConverter().generateTableDDL(table);
        assertTrue(ddl.startsWith("CREATE TABLE ducklake.main.customers"));
        assertTrue(ddl.contains("amount DECIMAL(18,4)"));
    }
}
