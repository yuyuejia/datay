package com.data.datasource.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.datasource.DataSourceConnection;
import com.data.datasource.DataSourceProvider;
import com.data.datasource.DataSourceProviderFactory;
import com.data.datasource.QueryResult;
import com.data.job.DatasourceInfo;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import com.data.job.component.StreamJdbcOutput;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DBType;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import com.data.metadata.util.DriverStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 人大金仓 KingbaseES 端到端集成测试，默认跳过。
 *
 * <p>先启动本地 Kingbase 实例（见 {@code datay-core/src/test/docker/kingbase/README.md}），
 * 并上传 kingbase8 JDBC 驱动到 {@code drivers/kingbase/default/}，再通过环境变量开启：
 * <pre>
 * KINGBASE_IT_URL=jdbc:kingbase8://127.0.0.1:54321/test \
 * KINGBASE_IT_USER=system \
 * KINGBASE_IT_PASSWORD=Kingbase123 \
 * mvn -pl datay-core test -Dtest=KingbaseIntegrationTest
 * </pre>
 */
class KingbaseIntegrationTest {

    private static final String SCHEMA = "public";
    private static final String CRUD_TABLE = "datay_kingbase_it";
    private static final String ETL_TABLE = "datay_kingbase_etl_it";

    private DatasourceInfo info;

    @BeforeEach
    void setUp() throws Exception {
        String url = System.getenv("KINGBASE_IT_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "未设置 KINGBASE_IT_URL，跳过 Kingbase 集成测试");
        Assumptions.assumeTrue(
            DriverStore.isDriverInstalled(DBType.KINGBASE),
            "Kingbase JDBC 驱动未安装，请先上传 kingbase8 jar 到 drivers/kingbase/default/"
        );

        info = new DatasourceInfo();
        info.setType(DBType.KINGBASE.name());
        info.setUrl(url);
        info.setUsername(envOrDefault("KINGBASE_IT_USER", "system"));
        info.setPassword(envOrDefault("KINGBASE_IT_PASSWORD", "Kingbase123"));
        info.setDbschema(SCHEMA);

        cleanup();
    }

    @AfterEach
    void tearDown() {
        if (info != null) {
            cleanup();
        }
    }

    @Test
    void connectsBrowsesMetadataAndExecutesCrud() throws Exception {
        DataSourceProvider provider = DataSourceProviderFactory.provider("KINGBASE");
        assertNotNull(provider, "KINGBASE Provider 应已注册");
        assertSame(provider, DataSourceProviderFactory.providerForUrl(info.getUrl()), "应按 kingbase8 URL 识别 Provider");

        try (DataSourceConnection connection = DataSourceProviderFactory.connect(info)) {
            assertTrue(connection.testConnection(), "连通性测试应通过");
            List<String> schemas = connection.getSchemas();
            // Kingbase R3 在 case-insensitive 模式下把默认 schema 折叠为 PUBLIC，R6/V9 通常为 public
            String actualSchema = schemas
                .stream()
                .filter(schema -> SCHEMA.equalsIgnoreCase(schema))
                .findFirst()
                .orElse(null);
            assertNotNull(actualSchema, "schema 列表应包含 public（忽略大小写），实际：" + schemas);

            TableMeta table = crudTable();
            connection.executeUpdate(DatabaseConverter.generateTableDDL(DBType.KINGBASE.name(), table), List.of());

            verifyTableMetadata(connection, actualSchema, table);
            verifyInsert(connection, table);
            verifyUpsert(connection, table);
            verifySelectWithParameters(connection, table);
            verifyDelete(connection, table);
        }
    }

    @Test
    void streamJdbcOutputCreatesTableAndWritesRecords() throws Exception {
        TableMeta sourceMeta = sourceTableMeta();

        // append：目标表不存在时自动建表并写入
        runSink(sourceMeta, "append", records(
            record(1, "alpha", "12.50", true, "2026-11-20 10:00:00"),
            record(2, "beta", "20.00", false, "2026-11-20 11:00:00")
        ));
        assertEquals(2, countRows(ETL_TABLE), "append 后应写入 2 行");

        // update：按主键 UPSERT，不新增行
        runSink(sourceMeta, "update", records(
            record(1, "alpha-updated", "99.90", false, "2026-11-21 09:30:00"),
            record(3, "gamma", "5.00", true, "2026-11-21 10:00:00")
        ));
        assertEquals(3, countRows(ETL_TABLE), "UPSERT 后应有 3 行");
        assertEquals("alpha-updated", queryString("SELECT name FROM " + qualified(ETL_TABLE) + " WHERE id = 1"));
        assertEquals("99.90", queryString("SELECT amount FROM " + qualified(ETL_TABLE) + " WHERE id = 1"));

        // auto + DELETE 事件：按主键删除
        FlowFile deleteFlow = new FlowFile();
        deleteFlow.setJsonArray(records(record(2, "beta", "20.00", false, "2026-11-20 11:00:00")));
        deleteFlow.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, sourceMeta);
        deleteFlow.setAttribute(FlowFile.ATTRIBUTE_TABLE, sourceMeta.getTable());
        deleteFlow.setAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE, "DELETE");
        StreamJdbcOutput deleteSink = newStreamJdbcOutput("auto");
        deleteSink.execute(deleteFlow);

        assertEquals(2, countRows(ETL_TABLE), "DELETE 后应剩 2 行");
        assertFalse(rowExists(2), "id=2 应已被删除");
    }

    @Test
    void createsWideTableForCommonTypes() throws Exception {
        TableMeta table = new TableMeta("datay_kingbase_types_it", new ArrayList<>());
        table.setSchema(SCHEMA);
        table.addColumn(column("id", "INTEGER", false, true));
        table.addColumn(column("c_smallint", "SMALLINT", true, false));
        table.addColumn(column("c_bigint", "BIGINT", true, false));
        table.addColumn(column("c_numeric", "NUMERIC", true, false));
        table.columns().get(table.columns().size() - 1).setPrecision(18);
        table.columns().get(table.columns().size() - 1).setScale(4);
        table.addColumn(column("c_double", "DOUBLE PRECISION", true, false));
        table.addColumn(column("c_varchar", "VARCHAR", true, false));
        table.columns().get(table.columns().size() - 1).setLength(128);
        table.addColumn(column("c_text", "TEXT", true, false));
        table.addColumn(column("c_boolean", "BOOLEAN", true, false));
        table.addColumn(column("c_date", "DATE", true, false));
        table.addColumn(column("c_timestamp", "TIMESTAMP", true, false));

        try (
            DataSourceConnection connection = DataSourceProviderFactory.connect(info);
            Connection jdbc = jdbc()
        ) {
            connection.executeUpdate(DatabaseConverter.generateTableDDL(DBType.KINGBASE.name(), table), List.of());

            String insertSql = DatabaseConverter.generateWriteSQL(DBType.KINGBASE.name(), table, "append");
            try (PreparedStatement statement = jdbc.prepareStatement(insertSql)) {
                for (int i = 0; i < table.columns().size(); i++) {
                    ColumnMeta column = table.columns().get(i);
                    Object value = switch (column.getName()) {
                        case "id" -> 1;
                        case "c_smallint" -> (short) 7;
                        case "c_bigint" -> 1234567890123L;
                        case "c_numeric" -> new java.math.BigDecimal("12345.6789");
                        case "c_double" -> 3.1415926d;
                        case "c_varchar" -> "金仓 Kingbase";
                        case "c_text" -> "长文本内容";
                        case "c_boolean" -> true;
                        case "c_date" -> java.time.LocalDate.of(2026, 11, 20);
                        case "c_timestamp" -> LocalDateTime.of(2026, 11, 20, 12, 34, 56);
                        default -> null;
                    };
                    DatabaseConverter.bindParameter(DBType.KINGBASE.name(), statement, i + 1, column, value);
                }
                statement.executeUpdate();
            }

            try (
                Statement statement = jdbc.createStatement();
                ResultSet rs = statement.executeQuery(
                    "SELECT c_smallint, c_bigint, c_numeric, c_double, c_varchar, c_boolean, c_date, c_timestamp FROM " +
                    qualified(table.getTable()) +
                    " WHERE id = 1"
                )
            ) {
                assertTrue(rs.next(), "宽表应写入 1 行");
                assertEquals(7, rs.getInt("c_smallint"));
                assertEquals(1234567890123L, rs.getLong("c_bigint"));
                assertEquals("12345.6789", rs.getBigDecimal("c_numeric").toPlainString());
                assertEquals(3.1415926d, rs.getDouble("c_double"), 1e-9);
                assertEquals("金仓 Kingbase", rs.getString("c_varchar"));
                assertTrue(rs.getBoolean("c_boolean"));
                assertEquals("2026-11-20", rs.getDate("c_date").toString());
                assertTrue(rs.getTimestamp("c_timestamp").toString().startsWith("2026-11-20 12:34:56"));
            }
        }
    }

    // ------------------------------------------------------------------ 元数据与 CRUD 校验

    private void verifyTableMetadata(DataSourceConnection connection, String schema, TableMeta table) throws Exception {
        List<TableMeta> tables = connection.getTables(schema, 200, table.getTable());
        List<String> names = tables.stream().map(TableMeta::getTable).toList();
        assertTrue(
            names.stream().anyMatch(name -> table.getTable().equalsIgnoreCase(name)),
            "表清单应包含 " + table.getTable() + "，实际：" + names
        );

        TableMeta meta = connection.getTableMeta(schema, table.getTable());
        assertEquals(table.getTable(), meta.getTable(), "表名应一致");
        ColumnMeta id = findColumn(meta, "id");
        assertTrue(id.isPrimaryKey(), "id 应被识别为主键");
        ColumnMeta amount = findColumn(meta, "amount");
        assertEquals(12, amount.getPrecision(), "NUMERIC 精度应回读为 12");
        assertEquals(2, amount.getScale(), "NUMERIC 小数位应回读为 2");

        TableMeta detail = connection.getTableDetail(schema, table.getTable());
        assertEquals("Kingbase 集成测试表", detail.getComment(), "表注释应回读");
        assertFalse(detail.getIndexes().isEmpty(), "主键应产生索引元数据");
    }

    private void verifyInsert(DataSourceConnection connection, TableMeta table) throws Exception {
        executeAppend(connection, table, 1, "alpha", new java.math.BigDecimal("10.25"), true, LocalDateTime.of(2026, 11, 20, 8, 0, 0));
        executeAppend(connection, table, 2, "beta", new java.math.BigDecimal("20.50"), false, LocalDateTime.of(2026, 11, 20, 9, 0, 0));
        assertEquals(2, countRows(CRUD_TABLE), "append 后应写入 2 行");
    }

    private void verifyUpsert(DataSourceConnection connection, TableMeta table) throws Exception {
        String upsertSql = DatabaseConverter.generateWriteSQL(DBType.KINGBASE.name(), table, "update");
        try (Connection jdbc = jdbc(); PreparedStatement statement = jdbc.prepareStatement(upsertSql)) {
            bindCrudValues(statement, 1, "alpha-updated", new java.math.BigDecimal("88.88"), true, LocalDateTime.of(2026, 11, 21, 8, 0, 0));
            statement.executeUpdate();

            bindCrudValues(statement, 3, "gamma", new java.math.BigDecimal("30.00"), false, LocalDateTime.of(2026, 11, 21, 9, 0, 0));
            statement.executeUpdate();
        }
        assertEquals(3, countRows(CRUD_TABLE), "UPSERT 后应保留并新增共 3 行");
        assertEquals("alpha-updated", queryString("SELECT name FROM " + qualified(CRUD_TABLE) + " WHERE id = 1"));
        assertEquals("88.88", queryString("SELECT amount FROM " + qualified(CRUD_TABLE) + " WHERE id = 1"));
    }

    private void verifySelectWithParameters(DataSourceConnection connection, TableMeta table) throws Exception {
        QueryResult result = connection.executeQuery(
            "SELECT id, name, amount FROM " + qualified(table.getTable()) + " WHERE id = ?",
            List.of(1),
            10
        );
        assertEquals(1, result.getRows().size(), "按主键查询应返回 1 行");
        Map<String, Object> row = result.getRows().get(0);
        assertEquals("alpha-updated", row.get("name"));
    }

    private void verifyDelete(DataSourceConnection connection, TableMeta table) throws Exception {
        String deleteSql = DatabaseConverter.generateWriteSQL(DBType.KINGBASE.name(), table, "delete");
        try (Connection jdbc = jdbc(); PreparedStatement statement = jdbc.prepareStatement(deleteSql)) {
            DatabaseConverter.bindParameter(DBType.KINGBASE.name(), statement, 1, findColumn(table, "id"), 2);
            assertEquals(1, statement.executeUpdate(), "DELETE 应影响 1 行");
        }
        assertEquals(2, countRows(CRUD_TABLE), "DELETE 后应剩 2 行");
    }

    // ------------------------------------------------------------------ ETL Sink

    private void runSink(TableMeta sourceMeta, String model, JSONArray records) {
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(records);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, sourceMeta);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, sourceMeta.getTable());
        flowFile.setAttribute(FlowFile.ATTRIBUTE_DATABASE, SCHEMA);

        StreamJdbcOutput sink = newStreamJdbcOutput(model);
        sink.execute(flowFile);
    }

    private StreamJdbcOutput newStreamJdbcOutput(String model) {
        StreamJdbcOutput sink = new StreamJdbcOutput();
        sink.setId("kingbase-it-sink");
        sink.setName("KingbaseIT");
        sink.setActiveThreads(new AtomicInteger(1));
        sink.setTaskLogger(new TaskLogger("kingbase-it", "kingbase-it-instance"));
        sink.setDatasource(info);
        sink.setSchema(SCHEMA);
        sink.setTable(ETL_TABLE);
        sink.setModel(model);
        return sink;
    }

    private TableMeta sourceTableMeta() {
        TableMeta table = new TableMeta(ETL_TABLE);
        table.setDbType(DBType.DUCKDB.name());
        table.setSchema(SCHEMA);
        ColumnMeta id = column("id", "INTEGER", false, true);
        ColumnMeta name = column("name", "VARCHAR", false, false);
        name.setLength(100);
        ColumnMeta amount = column("amount", "DECIMAL", true, false);
        amount.setPrecision(12);
        amount.setScale(2);
        ColumnMeta active = column("active", "BOOLEAN", true, false);
        ColumnMeta createdAt = column("created_at", "TIMESTAMP", true, false);
        table.addColumn(id);
        table.addColumn(name);
        table.addColumn(amount);
        table.addColumn(active);
        table.addColumn(createdAt);
        return table;
    }

    private static JSONObject record(int id, String name, String amount, boolean active, String createdAt) {
        JSONObject record = new JSONObject();
        record.put("id", id);
        record.put("name", name);
        record.put("amount", amount);
        record.put("active", active);
        record.put("created_at", createdAt);
        return record;
    }

    private static JSONArray records(JSONObject... records) {
        JSONArray array = new JSONArray();
        for (JSONObject record : records) {
            array.add(record);
        }
        return array;
    }

    // ------------------------------------------------------------------ 表与数据工具

    private TableMeta crudTable() {
        TableMeta table = new TableMeta(CRUD_TABLE);
        table.setSchema(SCHEMA);
        table.setComment("Kingbase 集成测试表");
        ColumnMeta id = column("id", "INTEGER", false, true);
        id.setComment("主键");
        ColumnMeta name = column("name", "VARCHAR", false, false);
        name.setLength(100);
        name.setComment("名称");
        ColumnMeta amount = column("amount", "NUMERIC", true, false);
        amount.setPrecision(12);
        amount.setScale(2);
        ColumnMeta active = column("active", "BOOLEAN", true, false);
        ColumnMeta createdAt = column("created_at", "TIMESTAMP", true, false);
        table.addColumn(id);
        table.addColumn(name);
        table.addColumn(amount);
        table.addColumn(active);
        table.addColumn(createdAt);
        return table;
    }

    private static ColumnMeta column(String name, String type, boolean nullable, boolean primaryKey) {
        ColumnMeta column = new ColumnMeta(name, type);
        column.setNullable(nullable);
        column.setPrimaryKey(primaryKey);
        return column;
    }

    private void executeAppend(
        DataSourceConnection connection,
        TableMeta table,
        int id,
        String name,
        java.math.BigDecimal amount,
        boolean active,
        LocalDateTime createdAt
    ) throws Exception {
        String insertSql = DatabaseConverter.generateWriteSQL(DBType.KINGBASE.name(), table, "append");
        try (Connection jdbc = jdbc(); PreparedStatement statement = jdbc.prepareStatement(insertSql)) {
            bindCrudValues(statement, id, name, amount, active, createdAt);
            statement.executeUpdate();
        }
    }

    private Connection jdbc() throws SQLException {
        return DBUtils.getConnection(info);
    }

    private static void bindCrudValues(
        PreparedStatement statement,
        int id,
        String name,
        java.math.BigDecimal amount,
        boolean active,
        LocalDateTime createdAt
    ) throws SQLException {
        DatabaseConverter.bindParameter(DBType.KINGBASE.name(), statement, 1, new ColumnMeta("id", "INTEGER"), id);
        DatabaseConverter.bindParameter(DBType.KINGBASE.name(), statement, 2, new ColumnMeta("name", "VARCHAR"), name);
        DatabaseConverter.bindParameter(DBType.KINGBASE.name(), statement, 3, new ColumnMeta("amount", "NUMERIC"), amount);
        DatabaseConverter.bindParameter(DBType.KINGBASE.name(), statement, 4, new ColumnMeta("active", "BOOLEAN"), active);
        DatabaseConverter.bindParameter(
            DBType.KINGBASE.name(),
            statement,
            5,
            new ColumnMeta("created_at", "TIMESTAMP"),
            createdAt
        );
    }

    private int countRows(String table) throws SQLException {
        try (
            Connection connection = DBUtils.getConnection(info);
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + qualified(table))
        ) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private boolean rowExists(int id) throws SQLException {
        try (
            Connection connection = DBUtils.getConnection(info);
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery("SELECT 1 FROM " + qualified(ETL_TABLE) + " WHERE id = " + id)
        ) {
            return rs.next();
        }
    }

    private String queryString(String sql) throws SQLException {
        try (
            Connection connection = DBUtils.getConnection(info);
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery(sql)
        ) {
            assertTrue(rs.next(), "查询应有结果：" + sql);
            return rs.getString(1);
        }
    }

    private void cleanup() {
        if (info == null) {
            return;
        }
        try (Connection connection = DBUtils.getConnection(info); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + qualified(CRUD_TABLE));
            statement.execute("DROP TABLE IF EXISTS " + qualified(ETL_TABLE));
            statement.execute("DROP TABLE IF EXISTS " + qualified("datay_kingbase_types_it"));
        } catch (Exception ignored) {
            // 容器未启动时不阻断测试跳过逻辑
        }
    }

    private static String qualified(String table) {
        return SCHEMA + "." + table;
    }

    private static ColumnMeta findColumn(TableMeta table, String name) {
        return table
            .columns()
            .stream()
            .filter(column -> name.equalsIgnoreCase(column.getName()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("未找到字段：" + name + "，实际：" + table.columns()));
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
