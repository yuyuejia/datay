package com.data.metadata.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.ColumnMeta;
import org.junit.jupiter.api.Test;

class DmConverterTest {

    private final DmConverter converter = new DmConverter();

    private ColumnMeta converted(String type, int length) {
        ColumnMeta column = new ColumnMeta("c", type);
        column.setLength(length);
        return converter.toTargetColumnType(column);
    }

    @Test
    void mapsCommonMysqlTypes() {
        assertEquals("TINYINT", converted("TINYINT", 3).getType());
        assertEquals("SMALLINT", converted("SMALLINT", 5).getType());
        assertEquals("INT", converted("MEDIUMINT", 7).getType());
        assertEquals("INT", converted("INT", 10).getType());
        assertEquals("BIGINT", converted("BIGINT", 19).getType());
        assertEquals("FLOAT", converted("FLOAT", 12).getType());
        assertEquals("DOUBLE", converted("DOUBLE", 22).getType());
        assertEquals("NUMBER", converted("DECIMAL", 20).getType());
        assertEquals("CHAR", converted("CHAR", 10).getType());
        assertEquals("VARCHAR", converted("VARCHAR", 100).getType());
        assertEquals("CLOB", converted("TEXT", 65535).getType());
        assertEquals("CLOB", converted("LONGTEXT", 2147483647).getType());
        assertEquals("VARBINARY", converted("BINARY", 8).getType());
        assertEquals("VARBINARY", converted("VARBINARY", 32).getType());
        assertEquals("BLOB", converted("BLOB", 65535).getType());
        assertEquals("DATE", converted("DATE", 10).getType());
        assertEquals("TIME", converted("TIME", 12).getType());
        assertEquals("TIMESTAMP", converted("DATETIME", 23).getType());
        assertEquals("TIMESTAMP", converted("TIMESTAMP", 23).getType());
        assertEquals("INT", converted("YEAR", 4).getType());
        assertEquals("BIT", converted("BOOLEAN", 1).getType());
        assertEquals("CLOB", converted("JSON", 1073741824).getType());
        assertEquals("VARCHAR", converted("ENUM", 1).getType());
        assertEquals("VARCHAR", converted("SET", 5).getType());
    }

    @Test
    void widensUnsignedMysqlIntegerTypes() {
        assertEquals("SMALLINT", converted("TINYINT UNSIGNED", 3).getType());
        assertEquals("INT", converted("SMALLINT UNSIGNED", 5).getType());
        assertEquals("INT", converted("MEDIUMINT UNSIGNED", 7).getType());
        assertEquals("BIGINT", converted("INT UNSIGNED", 10).getType());
        ColumnMeta bigint = converted("BIGINT UNSIGNED", 20);
        assertEquals("NUMBER", bigint.getType());
        assertEquals(20, bigint.getPrecision());
        assertEquals(0, bigint.getScale());
    }

    @Test
    void mapsMysqlBitWidthsToDmTypes() {
        assertEquals("BIT", converted("BIT", 1).getType());
        assertEquals("TINYINT", converted("BIT", 7).getType());
        assertEquals("SMALLINT", converted("BIT", 8).getType());
        assertEquals("SMALLINT", converted("BIT", 15).getType());
        assertEquals("INT", converted("BIT", 16).getType());
        assertEquals("BIGINT", converted("BIT", 32).getType());
        assertEquals("BIGINT", converted("BIT", 63).getType());
        assertEquals("NUMBER", converted("BIT", 64).getType());
    }

    @Test
    void generatesDmDdlWithPrecisionAndFractionalSeconds() {
        ColumnMeta decimal = new ColumnMeta("amount", "NUMBER");
        decimal.setPrecision(20);
        decimal.setScale(6);
        assertEquals("\"AMOUNT\" NUMBER(20,6)", converter.generateColumnDDL(decimal));

        ColumnMeta time = new ColumnMeta("c_time", "TIME");
        time.setScale(3);
        assertEquals("\"C_TIME\" TIME(3)", converter.generateColumnDDL(time));

        ColumnMeta timestamp = new ColumnMeta("c_datetime", "TIMESTAMP");
        timestamp.setScale(3);
        assertEquals("\"C_DATETIME\" TIMESTAMP(3)", converter.generateColumnDDL(timestamp));

        ColumnMeta bit = new ColumnMeta("c_bit", "BIT");
        assertTrue(converter.generateColumnDDL(bit).endsWith("BIT"));
    }
}
