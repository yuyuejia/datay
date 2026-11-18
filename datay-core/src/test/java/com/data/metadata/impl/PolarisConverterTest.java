package com.data.metadata.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.Test;

class PolarisConverterTest {

    private TableMeta sampleTable() {
        TableMeta table = new TableMeta("orders");
        table.setCatalog("polaris");
        table.setSchema("sales");
        ColumnMeta id = new ColumnMeta("id", "INTEGER");
        id.setPrimaryKey(true);
        table.addColumn(id);
        ColumnMeta amount = new ColumnMeta("amount", "DECIMAL");
        amount.setPrecision(18);
        amount.setScale(4);
        table.addColumn(amount);
        ColumnMeta note = new ColumnMeta("note", "VARCHAR");
        table.addColumn(note);
        return table;
    }

    @Test
    void generatesIcebergCompatibleDdl() {
        PolarisConverter converter = new PolarisConverter();
        String ddl = converter.generateTableDDL(sampleTable());

        assertTrue(ddl.startsWith("CREATE TABLE \"polaris\".\"sales\".\"orders\""), ddl);
        assertTrue(ddl.contains("\"id\" INTEGER"), ddl);
        assertTrue(ddl.contains("\"amount\" DECIMAL(18,4)"), ddl);
        assertTrue(ddl.contains("\"note\" VARCHAR"), ddl);
        // Iceberg 不支持主键与 MySQL 风格表注释
        assertFalse(ddl.toUpperCase().contains("PRIMARY KEY"), ddl);
        assertFalse(ddl.toUpperCase().contains("COMMENT="), ddl);
    }

    @Test
    void updateFallsBackToPlainInsert() {
        PolarisConverter converter = new PolarisConverter();
        String upsert = converter.generateUpsertSQL(sampleTable()).toUpperCase();
        assertTrue(upsert.startsWith("INSERT INTO "), upsert);
        assertFalse(upsert.contains("INSERT OR REPLACE"), upsert);
        assertFalse(upsert.contains("ON CONFLICT"), upsert);
    }

    @Test
    void deleteUsesBeforeImageWithNullSafeComparison() {
        PolarisConverter converter = new PolarisConverter();
        // 清除主键标记，模拟从目标 Iceberg 表回读的元数据（Iceberg 无主键）
        TableMeta table = sampleTable();
        for (ColumnMeta column : table.columns()) {
            column.setPrimaryKey(false);
        }
        String delete = converter.generateDeleteSQL(table);
        assertTrue(delete.contains("\"id\" IS NOT DISTINCT FROM ?"), delete);
        assertTrue(delete.contains("\"amount\" IS NOT DISTINCT FROM ?"), delete);
        assertTrue(delete.contains("\"note\" IS NOT DISTINCT FROM ?"), delete);
    }

    @Test
    void normalizesTypesToIcebergSupportedSet() {
        PolarisConverter converter = new PolarisConverter();
        assertEquals("INTEGER", converter.toTargetColumnType(new ColumnMeta("c", "TINYINT")).getType());
        assertEquals("INTEGER", converter.toTargetColumnType(new ColumnMeta("c", "SMALLINT")).getType());
        assertEquals("BIGINT", converter.toTargetColumnType(new ColumnMeta("c", "UINTEGER")).getType());
        assertEquals("VARCHAR", converter.toTargetColumnType(new ColumnMeta("c", "TEXT")).getType());
        assertEquals("VARCHAR", converter.toTargetColumnType(new ColumnMeta("c", "JSON")).getType());
        ColumnMeta bit = new ColumnMeta("c", "BIT");
        bit.setLength(1);
        assertEquals("BOOLEAN", converter.toTargetColumnType(bit).getType());
    }

    @Test
    void clampsDecimalPrecisionToIcebergLimit() {
        PolarisConverter converter = new PolarisConverter();
        ColumnMeta decimal = new ColumnMeta("c", "DECIMAL");
        decimal.setPrecision(50);
        decimal.setScale(10);
        ColumnMeta converted = converter.toTargetColumnType(decimal);
        assertEquals(38, converted.getPrecision());
        assertEquals(10, converted.getScale());
    }

    @Test
    void registeredInDatabaseConverter() {
        assertNotNull(DatabaseConverter.getConverter("polaris"));
        assertEquals("VARCHAR", DatabaseConverter.convert("mysql", "polaris", new ColumnMeta("c", "TEXT")).getType());
    }
}
