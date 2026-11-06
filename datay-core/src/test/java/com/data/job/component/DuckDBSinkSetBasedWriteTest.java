package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.DatasourceInfo;
import com.data.job.FlowFile;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DuckDB 原生表集合式 UPDATE / DELETE 写入验证。
 *
 * <p>覆盖多字段类型、主键变更、复合主键、字段映射等场景，直接驱动
 * {@link StreamJdbcOutput}，不依赖 MySQL binlog。</p>
 */
public class DuckDBSinkSetBasedWriteTest extends ComponentTestBase {

    private static final String DUCK_URL = "jdbc:duckdb:./target/duckdb-setbased-test.db";
    private static final String DUCK_FILE = "./target/duckdb-setbased-test.db";
    private static final String TABLE = "setbased_types";
    private static final String CPK_TABLE = "setbased_cpk";

    private DatasourceInfo datasource;

    @BeforeEach
    public void setUp() throws Exception {
        DBUtils.closePool(DUCK_URL, null);
        deleteDuckFiles();

        try (Connection conn = openDuck(); Statement st = conn.createStatement()) {
            st.execute(
                "CREATE TABLE main." + TABLE + " (" +
                "id BIGINT PRIMARY KEY, " +
                "c_int INTEGER, " +
                "c_bigint BIGINT, " +
                "c_decimal DECIMAL(18,6), " +
                "c_double DOUBLE, " +
                "c_varchar VARCHAR, " +
                "c_date DATE, " +
                "c_time TIME, " +
                "c_ts TIMESTAMP, " +
                "c_bool BOOLEAN, " +
                "c_blob BLOB, " +
                "c_bit BIT)"
            );
            st.execute("INSERT INTO main." + TABLE + " VALUES (1, 1, 1, 1.000000, 1.0, 'one', DATE '2024-01-01', TIME '01:02:03', TIMESTAMP '2024-01-01 01:02:03', false, X'01', '0')");
            st.execute("INSERT INTO main." + TABLE + " VALUES (2, 2, 2, 2.000000, 2.0, 'two', DATE '2024-01-02', TIME '02:03:04', TIMESTAMP '2024-01-02 02:03:04', false, X'02', '0')");
            st.execute("CREATE TABLE main." + CPK_TABLE + " (a BIGINT, b VARCHAR, val VARCHAR, PRIMARY KEY (a, b))");
            st.execute("INSERT INTO main." + CPK_TABLE + " VALUES (1, 'x', 'v1'), (2, 'y', 'v2'), (3, 'z', 'v3')");
        }

        datasource = new DatasourceInfo();
        datasource.setUrl(DUCK_URL);
        datasource.setType("DUCKDB");
    }

    @AfterEach
    public void tearDown() {
        DBUtils.closePool(DUCK_URL, null);
        deleteDuckFiles();
    }

    @Test
    public void testUpdateAllTypes() throws Exception {
        JSONObject record = baseRecord(1L);
        record.put("c_int", 42);
        record.put("c_bigint", 9_000_000_000L);
        record.put("c_decimal", new BigDecimal("12345.678900"));
        record.put("c_double", 3.5d);
        record.put("c_varchar", "updated");
        record.put("c_date", LocalDate.of(2025, 2, 3));
        record.put("c_time", LocalTime.of(4, 5, 6));
        record.put("c_ts", LocalDateTime.of(2025, 2, 3, 4, 5, 6));
        record.put("c_bool", true);
        record.put("c_blob", new byte[] { 1, 2, 3 });
        record.put("c_bit", "1");

        execute("UPDATE", TABLE, null, record);

        Map<String, String> row = readRow(TABLE, "id = 1");
        assertEquals("42", row.get("c_int"));
        assertEquals("9000000000", row.get("c_bigint"));
        assertEquals("12345.678900", row.get("c_decimal"));
        assertEquals("3.5", row.get("c_double"));
        assertEquals("updated", row.get("c_varchar"));
        assertEquals("2025-02-03", row.get("c_date"));
        assertEquals("04:05:06", row.get("c_time"));
        assertEquals("2025-02-03 04:05:06", row.get("c_ts"));
        assertEquals("true", row.get("c_bool"));
        assertEquals("010203", row.get("c_blob"));
        // DuckDB BIT 是位串类型；参数按 TINYINTCAST 后为 8 位，1 对应 00000001。
        assertEquals("00000001", row.get("c_bit"));
    }

    @Test
    public void testUpdatePrimaryKey() throws Exception {
        JSONObject record = baseRecord(100L);
        record.put("c_varchar", "after-pk-change");
        JSONObject before = new JSONObject();
        before.put("id", 1L);
        record.put("__before", before);

        execute("UPDATE", TABLE, null, record);

        assertFalse(rowExists(TABLE, "id = 1"), "旧主键记录应被删除");
        assertEquals("after-pk-change", readRow(TABLE, "id = 100").get("c_varchar"));
        assertEquals(2, countRows(TABLE), "主键变更应保持总行数不变");
    }

    @Test
    public void testDelete() throws Exception {
        JSONObject record = new JSONObject();
        record.put("id", 2L);

        execute("DELETE", TABLE, null, record);

        assertFalse(rowExists(TABLE, "id = 2"));
        assertTrue(rowExists(TABLE, "id = 1"));
    }

    @Test
    public void testCompositePrimaryKeyUpdateAndDelete() throws Exception {
        JSONObject update1 = new JSONObject();
        update1.put("a", 1L);
        update1.put("b", "x");
        update1.put("val", "V1");
        JSONObject update3 = new JSONObject();
        update3.put("a", 3L);
        update3.put("b", "z");
        update3.put("val", "V3");

        JSONArray setBasedUpdates = new JSONArray();
        setBasedUpdates.add(update1);
        setBasedUpdates.add(update3);
        execute("UPDATE", CPK_TABLE, null, setBasedUpdates);

        assertEquals("V1", readRow(CPK_TABLE, "a = 1 AND b = 'x'").get("val"));
        assertEquals("V3", readRow(CPK_TABLE, "a = 3 AND b = 'z'").get("val"));

        // 复合主键变更：走逐行回退路径，确保旧键被删除
        JSONObject updatePk = new JSONObject();
        updatePk.put("a", 2L);
        updatePk.put("b", "z-new");
        updatePk.put("val", "V2-new");
        JSONObject beforePk = new JSONObject();
        beforePk.put("a", 2L);
        beforePk.put("b", "y");
        updatePk.put("__before", beforePk);
        execute("UPDATE", CPK_TABLE, null, updatePk);

        assertFalse(rowExists(CPK_TABLE, "a = 2 AND b = 'y'"), "复合主键旧键应被删除");
        assertEquals("V2-new", readRow(CPK_TABLE, "a = 2 AND b = 'z-new'").get("val"));

        JSONObject delete = new JSONObject();
        delete.put("a", 1L);
        delete.put("b", "x");
        execute("DELETE", CPK_TABLE, null, delete);

        assertFalse(rowExists(CPK_TABLE, "a = 1 AND b = 'x'"));
        assertEquals(2, countRows(CPK_TABLE));
    }

    @Test
    public void testDuplicatePrimaryKeyKeepsLastUpdate() throws Exception {
        JSONObject first = baseRecord(1L);
        first.put("c_varchar", "first");
        JSONObject second = baseRecord(1L);
        second.put("c_varchar", "second");

        JSONArray updates = new JSONArray();
        updates.add(first);
        updates.add(second);
        execute("UPDATE", TABLE, null, updates);

        assertEquals("second", readRow(TABLE, "id = 1").get("c_varchar"));
        assertEquals(2, countRows(TABLE));
    }

    @Test
    public void testChainedPrimaryKeyChange() throws Exception {
        JSONObject first = baseRecord(100L);
        first.put("c_varchar", "b");
        JSONObject firstBefore = new JSONObject();
        firstBefore.put("id", 1L);
        first.put("__before", firstBefore);

        JSONObject second = baseRecord(200L);
        second.put("c_varchar", "c");
        JSONObject secondBefore = new JSONObject();
        secondBefore.put("id", 100L);
        second.put("__before", secondBefore);

        JSONArray updates = new JSONArray();
        updates.add(first);
        updates.add(second);
        execute("UPDATE", TABLE, null, updates);

        assertFalse(rowExists(TABLE, "id = 1"));
        assertFalse(rowExists(TABLE, "id = 100"));
        assertEquals("c", readRow(TABLE, "id = 200").get("c_varchar"));
        assertEquals(2, countRows(TABLE));
    }

    @Test
    public void testHeaderMapping() throws Exception {
        JSONObject record = baseRecord(1L);
        record.put("src_varchar", "mapped-value");

        List<Map<String, Object>> mapping = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("source", "src_varchar");
        item.put("target", "c_varchar");
        mapping.add(item);

        execute("UPDATE", TABLE, mapping, record);

        assertEquals("mapped-value", readRow(TABLE, "id = 1").get("c_varchar"));
    }

    private JSONObject baseRecord(long id) {
        JSONObject record = new JSONObject();
        record.put("id", id);
        record.put("c_int", 1);
        record.put("c_bigint", 1L);
        record.put("c_decimal", new BigDecimal("1.000000"));
        record.put("c_double", 1.0d);
        record.put("c_varchar", "one");
        record.put("c_date", LocalDate.of(2024, 1, 1));
        record.put("c_time", LocalTime.of(1, 2, 3));
        record.put("c_ts", LocalDateTime.of(2024, 1, 1, 1, 2, 3));
        record.put("c_bool", false);
        record.put("c_blob", new byte[] { 1 });
        record.put("c_bit", "0");
        return record;
    }

    private void execute(String eventType, String table, List<Map<String, Object>> mapping, Object data) {
        StreamJdbcOutput sink = new StreamJdbcOutput();
        initComponent(sink, "sink", "sink");
        sink.setDatasource(datasource);
        sink.setSchema("main");
        sink.setTable(table);
        sink.setModel("auto");
        sink.setHeader_map(mapping);

        JSONArray records = data instanceof JSONArray ? (JSONArray) data : new JSONArray();
        if (data instanceof JSONObject) {
            records.add(data);
        }

        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(records);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE, eventType);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, table);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_DATABASE, "main");
        sink.execute(flowFile);
    }

    private Map<String, String> readRow(String table, String where) throws Exception {
        Map<String, String> row = new LinkedHashMap<>();
        List<String> columns = table.equals(CPK_TABLE)
            ? Arrays.asList("a", "b", "val")
            : Arrays.asList("id", "c_int", "c_bigint", "c_decimal", "c_double", "c_varchar", "c_date", "c_time", "c_ts", "c_bool", "c_blob", "c_bit");
        try (Connection conn = openDuck(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM main." + table + " WHERE " + where)) {
            assertTrue(rs.next(), "未找到目标记录: " + table + " " + where);
            for (String column : columns) {
                row.put(column, stringValue(rs.getObject(column)));
            }
        }
        return row;
    }

    private boolean rowExists(String table, String where) throws Exception {
        try (Connection conn = openDuck(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 FROM main." + table + " WHERE " + where)) {
            return rs.next();
        }
    }

    private int countRows(String table) throws Exception {
        try (Connection conn = openDuck(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM main." + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Blob blob) {
            try {
                return bytesToHex(blob.getBytes(1, (int) blob.length()));
            } catch (Exception e) {
                return value.toString();
            }
        }
        if (value instanceof byte[]) {
            return bytesToHex((byte[]) value);
        }
        if (value instanceof java.sql.Date) {
            return value.toString();
        }
        if (value instanceof java.sql.Time) {
            return value.toString();
        }
        if (value instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime().toString().replace('T', ' ');
        }
        return value.toString();
    }

    private Connection openDuck() throws Exception {
        return DBUtils.getConnection(datasource != null ? datasource : duckDatasource());
    }

    private DatasourceInfo duckDatasource() {
        DatasourceInfo ds = new DatasourceInfo();
        ds.setUrl(DUCK_URL);
        ds.setType("DUCKDB");
        return ds;
    }

    private void deleteDuckFiles() {
        for (String suffix : new String[] { "", ".wal" }) {
            File file = new File(DUCK_FILE + suffix);
            if (file.exists()) {
                file.delete();
            }
        }
    }
}
