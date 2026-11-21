package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.Component;
import com.data.job.ComponentFactory;
import com.data.job.Connection;
import com.data.job.DatasourceInfo;
import com.data.job.FlowFile;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JDBCLookupTest extends ComponentTestBase {

    private static final String TARGET_ID = "lookup_sink";

    @TempDir
    Path tempDir;

    private String jdbcUrl;
    private LinkedBlockingQueue<Object> outputQueue;

    @AfterEach
    public void tearDown() {
        if (jdbcUrl != null) {
            DBUtils.closePool(jdbcUrl, null);
        }
    }

    @Test
    public void testCanonicalKeyNormalizesNumberTypes() {
        assertEquals("1", JDBCLookup.canonicalKey(1));
        assertEquals("1", JDBCLookup.canonicalKey(1L));
        assertEquals("1", JDBCLookup.canonicalKey(1.0d));
        assertEquals("1", JDBCLookup.canonicalKey(new BigDecimal("1.00")));
        assertEquals("001", JDBCLookup.canonicalKey("001"));
        assertNull(JDBCLookup.canonicalKey(null));
    }

    @Test
    public void testBuildLookupSqlWithConfiguredColumns() {
        JDBCLookup lookup = new JDBCLookup();
        lookup.setTable("users");
        lookup.setLocalField("user_id");
        lookup.setLookupField("id");
        lookup.setSelectColumns("name, dept");
        lookup.setWhere("status = 1");

        assertEquals(
            "SELECT id, name, dept FROM users WHERE id IN (?, ?, ?) AND (status = 1)",
            lookup.buildLookupSql(3)
        );
    }

    @Test
    public void testBuildLookupSqlSelectAll() {
        JDBCLookup lookup = new JDBCLookup();
        lookup.setSchema("demo");
        lookup.setTable("users");
        lookup.setLocalField("user_id");
        lookup.setLookupField("id");
        lookup.setSelectColumns("");

        assertEquals("SELECT * FROM demo.users WHERE id IN (?)", lookup.buildLookupSql(1));
    }

    @Test
    public void testFactoryBindsLookupConfig() {
        Map<String, Object> params = new HashMap<>();
        params.put(".id", "lookup1");
        params.put(".name", "JDBCLookup");
        Map<String, Object> sourceId = new HashMap<>();
        sourceId.put("type", "mysql");
        sourceId.put("url", "jdbc:mysql://localhost:3306/demo");
        sourceId.put("username", "root");
        sourceId.put("password", "123456");
        params.put("sourceId", sourceId);
        params.put("schema", "demo");
        params.put("table", "users");
        params.put("localField", "user_id");
        params.put("lookupField", "id");
        params.put("selectColumns", "name");
        params.put("outputPrefix", "u_");
        params.put("joinType", "INNER");
        params.put("batchSize", 100);
        params.put("cache", false);

        Component created = ComponentFactory.create("JDBCLookup", params);

        assertTrue(created instanceof JDBCLookup);
        JDBCLookup lookup = (JDBCLookup) created;
        assertEquals("demo", lookup.getSchema());
        assertEquals("users", lookup.getTable());
        assertEquals("user_id", lookup.getLocalField());
        assertEquals("id", lookup.getLookupField());
        assertEquals("name", lookup.getSelectColumns());
        assertEquals("u_", lookup.getOutputPrefix());
        assertEquals("INNER", lookup.getJoinType());
        assertEquals(100, lookup.getBatchSize());
        assertFalse(lookup.isCache());
        assertNotNull(lookup.getSourceId());
        assertEquals("jdbc:mysql://localhost:3306/demo", lookup.getSourceId().getUrl());
    }

    @Test
    public void testLeftJoinEnrichesRecordsAndMetadata() throws Exception {
        createLookupTable();

        JDBCLookup lookup = new JDBCLookup();
        initComponent(lookup, "lookup1", "JDBCLookup");
        lookup.setSourceId(sqliteDatasource());
        lookup.setSchema("main");
        lookup.setTable("users");
        lookup.setLocalField("user_id");
        lookup.setLookupField("id");
        lookup.setSelectColumns("name, dept");
        lookup.setOutputPrefix("u_");
        lookup.setJoinType("LEFT");
        prepareOutput(lookup);

        JSONArray rows = new JSONArray();
        rows.add(orderRow(1, 10));
        rows.add(orderRow(2, 11));
        rows.add(orderRow(3, 99));

        FlowFile input = new FlowFile();
        input.setJsonArray(rows);
        input.setAttribute(FlowFile.ATTRIBUTE_TABLE, "orders");
        input.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, ordersMeta());

        lookup.execute(input);

        FlowFile output = pollOutput();
        assertNotNull(output);
        JSONArray outputRows = output.getJsonArray();
        assertEquals(3, outputRows.size());

        JSONObject first = outputRows.getJSONObject(0);
        assertEquals("alice", first.getString("u_name"));
        assertEquals("finance", first.getString("u_dept"));

        JSONObject missing = outputRows.getJSONObject(2);
        assertTrue(missing.containsKey("u_name"));
        assertNull(missing.get("u_name"));
        assertNull(missing.get("u_dept"));

        TableMeta outputMeta = (TableMeta) output.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        assertNotNull(outputMeta);
        assertEquals("orders", outputMeta.getTable());
        assertNotNull(findColumn(outputMeta, "u_name"));
        assertNotNull(findColumn(outputMeta, "u_dept"));
    }

    @Test
    public void testInnerJoinDropsUnmatchedRecords() throws Exception {
        createLookupTable();

        JDBCLookup lookup = new JDBCLookup();
        initComponent(lookup, "lookup2", "JDBCLookup");
        lookup.setSourceId(sqliteDatasource());
        lookup.setSchema("main");
        lookup.setTable("users");
        lookup.setLocalField("user_id");
        lookup.setLookupField("id");
        lookup.setSelectColumns("name");
        lookup.setJoinType("INNER");
        prepareOutput(lookup);

        JSONArray rows = new JSONArray();
        rows.add(orderRow(1, 10));
        rows.add(orderRow(2, 11));
        rows.add(orderRow(3, 99));

        FlowFile input = new FlowFile();
        input.setJsonArray(rows);

        lookup.execute(input);

        FlowFile output = pollOutput();
        assertNotNull(output);
        assertEquals(2, output.getJsonArray().size());
        assertEquals("alice", output.getJsonArray().getJSONObject(0).getString("name"));
        assertEquals("bob", output.getJsonArray().getJSONObject(1).getString("name"));
    }

    @Test
    public void testMultipleLookupsEnrichRecordsInOrder() throws Exception {
        createUserAndDeptTables();

        JDBCLookup lookup = new JDBCLookup();
        initComponent(lookup, "lookup_multi", "JDBCLookup");
        lookup.setSourceId(sqliteDatasource());
        lookup.setSchema("main");
        lookup.setTables(
            List.of(
                lookupConfig("users", "user_id", "id", "name, dept_id", "u_", "LEFT", null),
                // 第二个关联表引用第一个关联表回填出的 u_dept_id，验证链式 lookup
                lookupConfig("depts", "u_dept_id", "id", "dept_name", "d_", "LEFT", null)
            )
        );
        prepareOutput(lookup);

        JSONArray rows = new JSONArray();
        rows.add(orderRow(1, 10));
        rows.add(orderRow(2, 11));
        rows.add(orderRow(3, 99));

        FlowFile input = new FlowFile();
        input.setJsonArray(rows);
        input.setAttribute(FlowFile.ATTRIBUTE_TABLE, "orders");
        input.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, ordersMeta());

        lookup.execute(input);

        FlowFile output = pollOutput();
        assertNotNull(output);
        JSONArray outputRows = output.getJsonArray();
        assertEquals(3, outputRows.size());

        JSONObject first = outputRows.getJSONObject(0);
        assertEquals("alice", first.getString("u_name"));
        assertEquals(100, first.getIntValue("u_dept_id"));
        assertEquals("finance", first.getString("d_dept_name"));

        JSONObject second = outputRows.getJSONObject(1);
        assertEquals("bob", second.getString("u_name"));
        assertEquals(200, second.getIntValue("u_dept_id"));
        assertEquals("sales", second.getString("d_dept_name"));

        JSONObject missing = outputRows.getJSONObject(2);
        assertTrue(missing.containsKey("u_name"));
        assertNull(missing.get("u_name"));
        assertTrue(missing.containsKey("u_dept_id"));
        assertNull(missing.get("u_dept_id"));
        assertTrue(missing.containsKey("d_dept_name"));
        assertNull(missing.get("d_dept_name"));

        TableMeta outputMeta = (TableMeta) output.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        assertNotNull(outputMeta);
        assertNotNull(findColumn(outputMeta, "u_name"));
        assertNotNull(findColumn(outputMeta, "u_dept_id"));
        assertNotNull(findColumn(outputMeta, "d_dept_name"));
    }

    @Test
    public void testInnerJoinInLaterLookupFiltersRecords() throws Exception {
        createUserAndDeptTables(false);

        JDBCLookup lookup = new JDBCLookup();
        initComponent(lookup, "lookup_multi_inner", "JDBCLookup");
        lookup.setSourceId(sqliteDatasource());
        lookup.setSchema("main");
        lookup.setTables(
            List.of(
                lookupConfig("users", "user_id", "id", "name, dept_id", "u_", "LEFT", null),
                // depts 中只有 100，user 11 的 dept_id=200 在第二个关联表被 INNER 过滤
                lookupConfig("depts", "u_dept_id", "id", "dept_name", "d_", "INNER", null)
            )
        );
        prepareOutput(lookup);

        JSONArray rows = new JSONArray();
        rows.add(orderRow(1, 10));
        rows.add(orderRow(2, 11));

        FlowFile input = new FlowFile();
        input.setJsonArray(rows);
        input.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, ordersMeta());

        lookup.execute(input);

        FlowFile output = pollOutput();
        assertNotNull(output);
        assertEquals(1, output.getJsonArray().size());
        assertEquals("alice", output.getJsonArray().getJSONObject(0).getString("u_name"));
        assertEquals("finance", output.getJsonArray().getJSONObject(0).getString("d_dept_name"));
    }

    @Test
    public void testFactoryBindsMultipleLookupTables() {
        Map<String, Object> params = new HashMap<>();
        params.put(".id", "lookup_multi_factory");
        params.put(".name", "JDBCLookup");
        Map<String, Object> sourceId = new HashMap<>();
        sourceId.put("type", "mysql");
        sourceId.put("url", "jdbc:mysql://localhost:3306/demo");
        params.put("sourceId", sourceId);
        params.put("schema", "demo");
        params.put("batchSize", 100);
        params.put("cache", false);
        params.put(
            "tables",
            List.of(
                lookupConfig("users", "user_id", "id", "name", "u_", "LEFT", "status = 1"),
                lookupConfig("depts", "u_dept_id", "id", "dept_name", "d_", "INNER", null)
            )
        );

        Component created = ComponentFactory.create("JDBCLookup", params);

        assertTrue(created instanceof JDBCLookup);
        JDBCLookup lookup = (JDBCLookup) created;
        List<JDBCLookup.LookupSpec> specs = lookup.resolveLookupSpecs();
        assertEquals(2, specs.size());
        assertEquals("users", specs.get(0).getTable());
        assertEquals("status = 1", specs.get(0).getWhere());
        assertEquals("depts", specs.get(1).getTable());
        assertEquals("INNER", specs.get(1).getJoinType());
        assertEquals(100, lookup.getBatchSize());
        assertFalse(lookup.isCache());
        assertNotNull(lookup.getSourceId());
        assertEquals("jdbc:mysql://localhost:3306/demo", lookup.getSourceId().getUrl());
    }

    @Test
    public void testBuildLookupSqlWithLookupSpec() {
        JDBCLookup lookup = new JDBCLookup();
        lookup.setSchema("demo");

        JDBCLookup.LookupSpec spec = new JDBCLookup.LookupSpec();
        spec.setTable("users");
        spec.setLocalField("user_id");
        spec.setLookupField("id");
        spec.setSelectColumns("name, dept");
        spec.setWhere("status = 1");

        assertEquals(
            "SELECT id, name, dept FROM demo.users WHERE id IN (?, ?, ?) AND (status = 1)",
            lookup.buildLookupSql(spec, 3)
        );
    }

    @Test
    public void testFactoryBindsJsonArrayTables() {
        JSONObject params = new JSONObject();
        params.put(".id", "lookup_json_tables");
        params.put(".name", "JDBCLookup");
        JSONObject sourceId = new JSONObject();
        sourceId.put("type", "sqlite");
        sourceId.put("url", "jdbc:sqlite::memory:");
        params.put("sourceId", sourceId);
        params.put("schema", "main");

        JSONArray tables = new JSONArray();
        JSONObject first = new JSONObject();
        first.put("table", "users");
        first.put("localField", "user_id");
        first.put("lookupField", "id");
        first.put("selectColumns", "name");
        tables.add(first);
        params.put("tables", tables);

        Component created = ComponentFactory.create("JDBCLookup", params);

        assertTrue(created instanceof JDBCLookup);
        List<JDBCLookup.LookupSpec> specs = ((JDBCLookup) created).resolveLookupSpecs();
        assertEquals(1, specs.size());
        assertEquals("users", specs.get(0).getTable());
        assertEquals("name", specs.get(0).getSelectColumns());
    }

    @Test
    public void testLookupSpecListIsAccepted() {
        JDBCLookup lookup = new JDBCLookup();

        JDBCLookup.LookupSpec first = new JDBCLookup.LookupSpec();
        first.setTable("users");
        first.setLocalField("user_id");
        first.setLookupField("id");

        JDBCLookup.LookupSpec second = new JDBCLookup.LookupSpec();
        second.setTable("depts");
        second.setLocalField("u_dept_id");
        second.setLookupField("id");

        lookup.setTables(List.of(first, second));

        List<JDBCLookup.LookupSpec> specs = lookup.resolveLookupSpecs();
        assertEquals(2, specs.size());
        assertEquals("users", specs.get(0).getTable());
        assertEquals("depts", specs.get(1).getTable());
    }

    private Map<String, Object> lookupConfig(String table, String localField, String lookupField,
                                             String selectColumns, String outputPrefix, String joinType, String where) {
        Map<String, Object> config = new HashMap<>();
        config.put("table", table);
        config.put("localField", localField);
        config.put("lookupField", lookupField);
        config.put("selectColumns", selectColumns);
        config.put("outputPrefix", outputPrefix);
        config.put("joinType", joinType);
        config.put("where", where);
        return config;
    }

    private void createUserAndDeptTables() throws Exception {
        createUserAndDeptTables(true);
    }

    private void createUserAndDeptTables(boolean includeSalesDept) throws Exception {
        Path dbFile = tempDir.resolve("lookup_multi.db");
        jdbcUrl = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        try (
            java.sql.Connection conn = DriverManager.getConnection(jdbcUrl);
            Statement statement = conn.createStatement()
        ) {
            statement.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, dept_id INTEGER)");
            statement.execute("INSERT INTO users (id, name, dept_id) VALUES (10, 'alice', 100)");
            statement.execute("INSERT INTO users (id, name, dept_id) VALUES (11, 'bob', 200)");
            statement.execute("CREATE TABLE depts (id INTEGER PRIMARY KEY, dept_name TEXT)");
            statement.execute("INSERT INTO depts (id, dept_name) VALUES (100, 'finance')");
            if (includeSalesDept) {
                statement.execute("INSERT INTO depts (id, dept_name) VALUES (200, 'sales')");
            }
        }
    }

    private void createLookupTable() throws Exception {
        Path dbFile = tempDir.resolve("lookup.db");
        jdbcUrl = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        try (
            java.sql.Connection conn = DriverManager.getConnection(jdbcUrl);
            Statement statement = conn.createStatement()
        ) {
            statement.execute("CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, dept TEXT)");
            statement.execute("INSERT INTO users (id, name, dept) VALUES (10, 'alice', 'finance')");
            statement.execute("INSERT INTO users (id, name, dept) VALUES (11, 'bob', 'sales')");
        }
    }

    private DatasourceInfo sqliteDatasource() {
        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setType("sqlite");
        datasource.setUrl(jdbcUrl);
        datasource.setDbschema("main");
        return datasource;
    }

    private void prepareOutput(JDBCLookup lookup) {
        lookup.getOutput().add(new Connection(lookup.getId(), TARGET_ID, 0));
        outputQueue = new LinkedBlockingQueue<>();
        context.getConnections().put(lookup.getId() + "_" + TARGET_ID, outputQueue);
    }

    private FlowFile pollOutput() {
        Object result = outputQueue == null ? null : outputQueue.poll();
        return result instanceof FlowFile ? (FlowFile) result : null;
    }

    private TableMeta ordersMeta() {
        TableMeta meta = new TableMeta();
        meta.setDbType("sqlite");
        meta.setTable("orders");
        meta.setSchema("main");
        meta.addColumn(new ColumnMeta("id", "INTEGER"));
        meta.addColumn(new ColumnMeta("user_id", "INTEGER"));
        return meta;
    }

    private JSONObject orderRow(int id, int userId) {
        JSONObject row = new JSONObject();
        row.put("id", id);
        row.put("user_id", userId);
        return row;
    }

    private ColumnMeta findColumn(TableMeta meta, String name) {
        List<ColumnMeta> columns = meta.columns();
        for (ColumnMeta column : columns) {
            if (name.equalsIgnoreCase(column.getName())) {
                return column;
            }
        }
        return null;
    }
}
