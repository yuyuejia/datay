package com.data.metadata.impl;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import com.data.metadata.util.JdbcValueNormalizer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClickHouseConverterTest {

    @Test
    public void testClickHouseSourceTypesBecomePortable() {
        ColumnMeta uint64 = DatabaseConverter.convert("clickhouse", "duckdb", new ColumnMeta("u64", "UInt64"));
        assertEquals("DECIMAL", uint64.getType());
        assertEquals(20, uint64.getPrecision());
        assertEquals(0, uint64.getScale());

        ColumnMeta array = DatabaseConverter.convert("clickhouse", "duckdb", new ColumnMeta("arr", "Array(Int32)"));
        assertEquals("TEXT", array.getType());

        ColumnMeta mysqlArray = DatabaseConverter.convert("clickhouse", "mysql", new ColumnMeta("arr", "Array(Int32)"));
        assertEquals("LONGTEXT", mysqlArray.getType());

        ColumnMeta float32 = DatabaseConverter.convert("clickhouse", "duckdb", new ColumnMeta("f32", "Float32"));
        assertEquals("FLOAT", float32.getType());

        ColumnMeta dateTime64 = DatabaseConverter.convert("clickhouse", "duckdb", new ColumnMeta("dt", "DateTime64(9)"));
        assertEquals("TIMESTAMP", dateTime64.getType());
        assertEquals(6, dateTime64.getScale());

        ColumnMeta fixed = DatabaseConverter.convert("clickhouse", "mysql", new ColumnMeta("fixed", "FixedString(4)"));
        assertEquals("VARCHAR", fixed.getType());
        assertEquals(4, fixed.getLength());

        ColumnMeta uuid = DatabaseConverter.convert("clickhouse", "duckdb", new ColumnMeta("uid", "UUID"));
        assertEquals("UUID", uuid.getType());
    }

    @Test
    public void testClickHouseTargetDDLUsesPrimaryKeyAsOrderBy() {
        TableMeta source = new TableMeta("t1");
        ColumnMeta id = new ColumnMeta("id", "BIGINT");
        id.setPrimaryKey(true);
        id.setNullable(false);
        source.addColumn(id);
        ColumnMeta name = new ColumnMeta("name", "VARCHAR");
        name.setLength(32);
        name.setNullable(true);
        source.addColumn(name);

        TableMeta target = DatabaseConverter.convert("duckdb", "clickhouse", source);
        String ddl = DatabaseConverter.generateTableDDL("clickhouse", target);

        assertTrue(ddl.contains("ENGINE = MergeTree"));
        assertTrue(ddl.contains("ORDER BY (`id`)"));
        assertTrue(ddl.contains("`id` Int64"));
        assertTrue(ddl.contains("`name` Nullable(String)"));
    }

    @Test
    public void testClickHouseUpsertIsRejected() {
        TableMeta table = new TableMeta("t1");
        table.addColumn(new ColumnMeta("id", "Int64"));
        assertThrows(UnsupportedOperationException.class, () -> new ClickHouseConverter().generateUpsertSQL(table));
    }

    @Test
    public void testValueNormalizerProducesJsonFriendlyValues() throws Exception {
        JSONArray array = assertInstanceOf(JSONArray.class, JdbcValueNormalizer.normalize(new int[] { 1, 2, 3 }));
        assertEquals(List.of(1, 2, 3), array);

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("k1", 10);
        map.put("k2", 20);
        JSONObject object = assertInstanceOf(JSONObject.class, JdbcValueNormalizer.normalize(map));
        assertEquals(10, object.getIntValue("k1"));

        assertEquals("192.168.1.1", JdbcValueNormalizer.normalize(InetAddress.getByName("192.168.1.1")));
        assertEquals("ab", JdbcValueNormalizer.normalize("ab\u0000\u0000"));
    }

    @Test
    public void testBigIntegerParameterNormalization() {
        BigInteger maxUInt64 = new BigInteger("18446744073709551615");

        assertEquals("18446744073709551615", DBUtils.normalizeParameter("VARCHAR(64)", maxUInt64));
        assertEquals(new BigDecimal(maxUInt64), DBUtils.normalizeParameter("DECIMAL(20,0)", maxUInt64));
        assertEquals(42L, DBUtils.normalizeParameter("BIGINT", new BigInteger("42")));
        assertFalse(DBUtils.normalizeParameter("BIGINT", maxUInt64) instanceof Long);
    }
}
