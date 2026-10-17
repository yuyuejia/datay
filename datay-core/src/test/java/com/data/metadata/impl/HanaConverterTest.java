package com.data.metadata.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.AllDataType;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.Test;

class HanaConverterTest {

    @Test
    void mapsCommonTypesToHanaTypes() {
        HanaConverter converter = new HanaConverter();

        assertEquals("NVARCHAR", converter.toTargetColumnType(new ColumnMeta("name", AllDataType.VARCHAR.name())).getType());
        assertEquals("INTEGER", converter.toTargetColumnType(new ColumnMeta("id", AllDataType.INT.name())).getType());
        assertEquals("BIGINT", converter.toTargetColumnType(new ColumnMeta("id", AllDataType.BIGINT.name())).getType());
        assertEquals("DECIMAL", converter.toTargetColumnType(new ColumnMeta("amount", AllDataType.DECIMAL.name())).getType());
        assertEquals("TIMESTAMP", converter.toTargetColumnType(new ColumnMeta("created", AllDataType.DATETIME.name())).getType());
        assertEquals("BOOLEAN", converter.toTargetColumnType(new ColumnMeta("flag", AllDataType.BOOLEAN.name())).getType());
        assertEquals("NCLOB", converter.toTargetColumnType(new ColumnMeta("content", AllDataType.LONGTEXT.name())).getType());
    }

    @Test
    void generatesQuotedTableDdlWithPrimaryKey() {
        TableMeta table = new TableMeta("orders", new java.util.ArrayList<>());
        table.setSchema("SALES");

        ColumnMeta id = new ColumnMeta("id", "INTEGER");
        id.setPrimaryKey(true);
        id.setNullable(false);

        ColumnMeta name = new ColumnMeta("name", "NVARCHAR");
        name.setLength(100);

        table.columns().add(id);
        table.columns().add(name);

        String ddl = new HanaConverter().generateTableDDL(table);
        assertTrue(ddl.startsWith("CREATE TABLE \"SALES\".\"orders\""));
        assertTrue(ddl.contains("\"id\" INTEGER NOT NULL"));
        assertTrue(ddl.contains("\"name\" NVARCHAR(100)"));
        assertTrue(ddl.contains("PRIMARY KEY (\"id\")"));
    }

    @Test
    void generatesInsertUpsertAndDeleteSql() {
        TableMeta table = new TableMeta("orders", new java.util.ArrayList<>());
        table.setSchema("SALES");

        ColumnMeta id = new ColumnMeta("id", "INTEGER");
        id.setPrimaryKey(true);
        ColumnMeta name = new ColumnMeta("name", "NVARCHAR");
        table.columns().add(id);
        table.columns().add(name);

        HanaConverter converter = new HanaConverter();

        assertEquals(
            "INSERT INTO \"SALES\".\"orders\" (\"id\", \"name\") VALUES (?, ?)",
            converter.generateInsertSQL(table)
        );
        assertEquals(
            "UPSERT \"SALES\".\"orders\" (\"id\", \"name\") VALUES (?, ?) WITH PRIMARY KEY",
            converter.generateUpsertSQL(table)
        );
        assertEquals("DELETE FROM \"SALES\".\"orders\" WHERE \"id\" = ?", converter.generateDeleteSQL(table));
    }
}
