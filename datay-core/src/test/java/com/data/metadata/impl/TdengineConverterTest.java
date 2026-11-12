package com.data.metadata.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.AllDataType;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class TdengineConverterTest {

    @Test
    void mapsCommonTypesToTdengineTypes() {
        TdengineConverter converter = new TdengineConverter();

        assertEquals("TIMESTAMP", converter.toTargetColumnType(new ColumnMeta("ts", AllDataType.DATETIME.name())).getType());
        assertEquals("NCHAR", converter.toTargetColumnType(new ColumnMeta("name", AllDataType.VARCHAR.name())).getType());
        assertEquals("INT", converter.toTargetColumnType(new ColumnMeta("age", AllDataType.INT.name())).getType());
        assertEquals("BIGINT", converter.toTargetColumnType(new ColumnMeta("id", AllDataType.BIGINT.name())).getType());
        assertEquals("DOUBLE", converter.toTargetColumnType(new ColumnMeta("amount", AllDataType.DECIMAL.name())).getType());
        assertEquals("BOOL", converter.toTargetColumnType(new ColumnMeta("flag", AllDataType.BOOLEAN.name())).getType());
        assertEquals("VARBINARY", converter.toTargetColumnType(new ColumnMeta("data", AllDataType.BLOB.name())).getType());
    }

    @Test
    void clearsSourcePrimaryKeyFlag() {
        ColumnMeta source = new ColumnMeta("id", "BIGINT");
        source.setPrimaryKey(true);
        assertFalse(new TdengineConverter().toTargetColumnType(source).isPrimaryKey());
    }

    @Test
    void mapsUnknownTypesToTextByDefault() {
        TdengineConverter converter = new TdengineConverter();
        assertEquals("NCHAR", converter.toTargetColumnType(new ColumnMeta("custom", "GEOMETRY")).getType());
    }

    @Test
    void generatesTableDdlWithTimestampFirst() {
        TableMeta table = new TableMeta("meters", new ArrayList<>());
        table.setSchema("power");

        ColumnMeta name = new ColumnMeta("name", "NCHAR");
        name.setLength(100);
        ColumnMeta value = new ColumnMeta("value", "DOUBLE");
        ColumnMeta ts = new ColumnMeta("ts", "TIMESTAMP");

        table.columns().add(name);
        table.columns().add(value);
        table.columns().add(ts);

        String ddl = new TdengineConverter().generateTableDDL(table);
        assertEquals(
            "CREATE TABLE `power`.`meters` (\n  `ts` TIMESTAMP,\n  `name` NCHAR(100),\n  `value` DOUBLE\n)",
            ddl
        );
    }

    @Test
    void longTextFallsBackToVarchar() {
        ColumnMeta column = new ColumnMeta("content", "NCHAR");
        column.setLength(20000);
        assertEquals("`content` VARCHAR(20000)", new TdengineConverter().generateColumnDDL(column));
    }

    @Test
    void rejectsTableWithoutTimestampColumn() {
        TableMeta table = new TableMeta("meters", new ArrayList<>());
        table.columns().add(new ColumnMeta("value", "DOUBLE"));
        assertThrows(IllegalArgumentException.class, () -> new TdengineConverter().generateTableDDL(table));
    }

    @Test
    void generatesInsertUpsertReplaceAndDeleteSql() {
        TableMeta table = new TableMeta("meters", new ArrayList<>());
        table.setSchema("power");
        ColumnMeta ts = new ColumnMeta("ts", "TIMESTAMP");
        ColumnMeta value = new ColumnMeta("value", "DOUBLE");
        table.columns().add(ts);
        table.columns().add(value);

        TdengineConverter converter = new TdengineConverter();
        String insert = "INSERT INTO `power`.`meters` (`ts`, `value`) VALUES (?, ?)";
        assertEquals(insert, converter.generateInsertSQL(table));
        assertEquals(insert, converter.generateUpsertSQL(table));
        assertEquals(insert, converter.generateReplaceSQL(table));
        assertEquals("DELETE FROM `power`.`meters` WHERE `ts` = ?", converter.generateDeleteSQL(table));
    }

    @Test
    void normalizesTdengineTextByteArray() {
        ColumnMeta column = new ColumnMeta("name", "NCHAR");
        Object normalized = new TdengineConverter().normalizeSourceValue(
            column,
            "温度".getBytes(StandardCharsets.UTF_8)
        );
        assertEquals("温度", normalized);
    }

    @Test
    void reportsSupportedTargetTypes() {
        assertTrue(new TdengineConverter().getSupportedTypes().contains("TIMESTAMP"));
        assertTrue(new TdengineConverter().getSupportedTypes().contains("NCHAR"));
    }
}
