package com.data.metadata.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.ColumnMeta;
import org.junit.jupiter.api.Test;

class MysqlConverterTest {

    private final MysqlConverter converter = new MysqlConverter();

    @Test
    void mapsSqlServerPhysicalTypes() {
        assertEquals("DECIMAL", converter.toTargetColumnType(new ColumnMeta("c", "smallmoney")).getType());
        assertEquals("DATETIME", converter.toTargetColumnType(new ColumnMeta("c", "datetime2")).getType());
        assertEquals("DATETIME", converter.toTargetColumnType(new ColumnMeta("c", "smalldatetime")).getType());
        assertEquals("VARCHAR", converter.toTargetColumnType(new ColumnMeta("c", "datetimeoffset")).getType());
        assertEquals("CHAR", converter.toTargetColumnType(new ColumnMeta("c", "uniqueidentifier")).getType());
        assertEquals("LONGTEXT", converter.toTargetColumnType(new ColumnMeta("c", "ntext")).getType());
        assertEquals("LONGBLOB", converter.toTargetColumnType(new ColumnMeta("c", "image")).getType());
        assertEquals("FLOAT", converter.toTargetColumnType(new ColumnMeta("c", "real")).getType());
        assertEquals("DOUBLE", converter.toTargetColumnType(new ColumnMeta("c", "float")).getType());
        assertEquals("TINYINT", converter.toTargetColumnType(new ColumnMeta("c", "bit")).getType());
    }

    @Test
    void longStringAndBinaryColumnsBecomeLongTypes() {
        ColumnMeta varcharMax = new ColumnMeta("c", "VARCHAR");
        varcharMax.setLength(2147483647);
        assertEquals("LONGTEXT", converter.toTargetColumnType(varcharMax).getType());

        ColumnMeta varbinaryMax = new ColumnMeta("c", "VARBINARY");
        varbinaryMax.setLength(2147483647);
        assertEquals("LONGBLOB", converter.toTargetColumnType(varbinaryMax).getType());
    }

    @Test
    void generatesDatetimePrecisionAndAvoidsFloatDisplayWidth() {
        ColumnMeta datetime2 = new ColumnMeta("created_at", "DATETIME");
        datetime2.setScale(7);
        String datetimeDdl = converter.generateColumnDDL(datetime2);
        assertTrue(datetimeDdl.endsWith("DATETIME(6)"), datetimeDdl);

        ColumnMeta time = new ColumnMeta("c_time", "TIME");
        time.setScale(6);
        assertTrue(converter.generateColumnDDL(time).endsWith("TIME(6)"));

        // FLOAT/DOUBLE 不能带 (M,D)，否则 MySQL 会按小数位舍入（如 DOUBLE(53,0) 把 1.25 变成 1）
        ColumnMeta doubleCol = new ColumnMeta("c_float", "DOUBLE");
        doubleCol.setPrecision(53);
        doubleCol.setScale(0);
        assertEquals("`c_float` DOUBLE", converter.generateColumnDDL(doubleCol));
    }
}
