package com.datay.plugin.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentFactory;
import com.data.job.ComponentFieldDescriptor;
import com.data.job.ComponentMetadataExtractor;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.metadata.util.ConnectionPoolManager;
import com.data.metadata.util.DBUtils;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 自定义 JDBC 单表输入 -> StreamJdbcOutput 的真实链路验证。
 * <p>
 * 测试依赖本地 MySQL（默认 127.0.0.1:3307，root 空密码）和一个临时 DuckDB 文件。
 * MySQL 不可用时自动跳过；测试会自行创建源库、源表和 5 行样例数据。
 * 可通过 {@code -Ddatay.test.mysql.host/port/user/password} 指定其他 MySQL。
 */
class ExampleJdbcSingleTableInputTest {

    private static final String MYSQL_PARAMS = "useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String MYSQL_HOST = System.getProperty("datay.test.mysql.host", "127.0.0.1");
    private static final String MYSQL_PORT = System.getProperty("datay.test.mysql.port", "3307");
    private static final String MYSQL_USER = System.getProperty("datay.test.mysql.user", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("datay.test.mysql.password", "");
    private static final String MYSQL_BASE_URL = "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT + "/?" + MYSQL_PARAMS;
    private static final String MYSQL_DB_URL =
        "jdbc:mysql://" + MYSQL_HOST + ":" + MYSQL_PORT + "/quickstart_plugin?" + MYSQL_PARAMS;

    @Test
    void exposesDatasourceFieldsAndBindsCustomDatasourceConfig() {
        List<ComponentFieldDescriptor> fields = ComponentMetadataExtractor.extract(ExampleJdbcSingleTableInput.class);
        assertTrue(
            fields.stream().anyMatch(
                field -> "sourceDatasource".equals(field.getKey())
                    && "DATASOURCE".equals(field.getType())
                    && field.isRequired()
                    && "sourceSchema".equals(field.getSchemaField())
            )
        );
        assertTrue(
            fields.stream().anyMatch(
                field -> "sourceSchema".equals(field.getKey())
                    && "SCHEMA".equals(field.getType())
                    && "sourceDatasource".equals(field.getDataSourceField())
            )
        );
        assertTrue(
            fields.stream().anyMatch(
                field -> "sourceTable".equals(field.getKey())
                    && "TABLE".equals(field.getType())
                    && "sourceDatasource".equals(field.getDataSourceField())
                    && "sourceSchema".equals(field.getSchemaField())
            )
        );

        registerCustomComponent();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put(
            "sourceDatasource",
            Map.of(
                "type",
                "MYSQL",
                "url",
                "jdbc:mysql://127.0.0.1:3307/quickstart_plugin",
                "username",
                "root",
                "password",
                "",
                "dbschema",
                "quickstart_plugin"
            )
        );
        params.put("sourceSchema", "quickstart_plugin");
        params.put("sourceTable", "plugin_order");

        ExampleJdbcSingleTableInput input = (ExampleJdbcSingleTableInput) ComponentFactory.create(
            "ExampleJdbcSingleTableInput",
            params
        );
        assertNotNull(input.getSourceDatasource());
        assertEquals("MYSQL", input.getSourceDatasource().getType());
        assertEquals("quickstart_plugin", input.getSourceDatasource().getDbschema());
        assertEquals("quickstart_plugin", input.getSourceSchema());
        assertEquals("plugin_order", input.getSourceTable());
    }

    @Test
    void syncsMysqlSingleTableToDuckdbThroughCustomInput() throws Exception {
        assumeTrue(isMysqlAvailable(), "本地 MySQL 127.0.0.1:3307 不可用，跳过集成验证");

        prepareMysqlSource();

        Path targetDb = Path.of("target", "plugin-jdbc-single-table.duckdb").toAbsolutePath();
        Path targetWal = Path.of(targetDb + ".wal");
        Files.deleteIfExists(targetDb);
        Files.deleteIfExists(targetWal);

        try {
            registerCustomComponent();

            JSONObject job = new JSONObject();
            JSONArray units = new JSONArray();
            units.add(buildInputUnit());
            units.add(buildOutputUnit(targetDb));
            job.put("units", units);

            JSONArray connections = new JSONArray();
            JSONObject connection = new JSONObject();
            connection.put("sourceId", "mysql_input");
            connection.put("targetId", "duckdb_output");
            connection.put("sourcePort", 0);
            connections.add(connection);
            job.put("connections", connections);
            job.put("version", "1.0.0");

            new ETLFlowTask().runJob(job.toJSONString());

            DatasourceInfo targetInfo = duckdbDatasourceInfo(targetDb);
            try (
                Connection conn = DBUtils.getConnection(targetInfo);
                Statement statement = conn.createStatement();
                ResultSet rs = statement.executeQuery(
                    "SELECT COUNT(*) AS cnt, SUM(amount) AS total_amount FROM main.plugin_order_copy"
                )
            ) {
                assertTrue(rs.next(), "目标表应存在数据");
                assertEquals(5, rs.getInt("cnt"));
                assertEquals(0, rs.getBigDecimal("total_amount").compareTo(new BigDecimal("827.15")));
            }

            try (
                Connection conn = DBUtils.getConnection(targetInfo);
                Statement statement = conn.createStatement();
                ResultSet rs = statement.executeQuery(
                    "SELECT order_no, customer_name, amount, status FROM main.plugin_order_copy WHERE id = 1"
                )
            ) {
                assertTrue(rs.next(), "应能查询到 id=1 的数据");
                assertEquals("SO-1001", rs.getString("order_no"));
                assertEquals("Alice", rs.getString("customer_name"));
                assertEquals(0, rs.getBigDecimal("amount").compareTo(new BigDecimal("128.50")));
                assertEquals(1, rs.getInt("status"));
            }
        } finally {
            ConnectionPoolManager.closeAllPools();
            Files.deleteIfExists(targetWal);
            Files.deleteIfExists(targetDb);
        }
    }

    private boolean isMysqlAvailable() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection ignored = DriverManager.getConnection(MYSQL_BASE_URL, MYSQL_USER, MYSQL_PASSWORD)) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void prepareMysqlSource() throws Exception {
        try (
            Connection conn = DriverManager.getConnection(MYSQL_BASE_URL, MYSQL_USER, MYSQL_PASSWORD);
            Statement statement = conn.createStatement()
        ) {
            statement.execute("CREATE DATABASE IF NOT EXISTS quickstart_plugin");
            statement.execute("USE quickstart_plugin");
            statement.execute("DROP TABLE IF EXISTS plugin_order");
            statement.execute(
                """
                    CREATE TABLE plugin_order (
                      id BIGINT PRIMARY KEY,
                      order_no VARCHAR(32) NOT NULL,
                      customer_name VARCHAR(64),
                      amount DECIMAL(12,2),
                      status TINYINT,
                      created_at DATETIME
                    )
                    """
            );
            statement.execute(
                """
                    INSERT INTO plugin_order (id, order_no, customer_name, amount, status, created_at) VALUES
                      (1, 'SO-1001', 'Alice', 128.50, 1, '2026-11-01 10:00:00'),
                      (2, 'SO-1002', 'Bob', 89.90, 1, '2026-11-01 11:00:00'),
                      (3, 'SO-1003', 'Carol', 256.00, 0, '2026-11-02 09:30:00'),
                      (4, 'SO-1004', 'David', 42.00, 1, '2026-11-02 15:20:00'),
                      (5, 'SO-1005', 'Eve', 310.75, 0, '2026-11-03 08:10:00')
                    """
            );
        }
    }

    private void registerCustomComponent() {
        ComponentRegister annotation = ExampleJdbcSingleTableInput.class.getAnnotation(ComponentRegister.class);
        ComponentFactory.registerComponent("ExampleJdbcSingleTableInput", ExampleJdbcSingleTableInput.class, annotation);
    }

    private JSONObject buildInputUnit() {
        JSONObject unit = new JSONObject();
        unit.put(".id", "mysql_input");
        unit.put(".name", "ExampleJdbcSingleTableInput");
        unit.put("sourceDatasource", mysqlDatasource());
        unit.put("sourceSchema", "quickstart_plugin");
        unit.put("sourceTable", "plugin_order");
        return unit;
    }

    private JSONObject buildOutputUnit(Path targetDb) {
        JSONObject unit = new JSONObject();
        unit.put(".id", "duckdb_output");
        unit.put(".name", "StreamJdbcOutput");
        unit.put("sourceId", duckdbDatasource(targetDb));
        unit.put("schema", "main");
        unit.put("table", "plugin_order_copy");
        unit.put("model", "append");
        unit.put("ignoreError", false);
        return unit;
    }

    private Map<String, Object> mysqlDatasource() {
        Map<String, Object> datasource = new LinkedHashMap<>();
        datasource.put("type", "MYSQL");
        datasource.put("url", MYSQL_DB_URL);
        datasource.put("driver", "com.mysql.cj.jdbc.Driver");
        datasource.put("username", MYSQL_USER);
        datasource.put("password", MYSQL_PASSWORD);
        datasource.put("hostname", MYSQL_HOST);
        datasource.put("port", MYSQL_PORT);
        datasource.put("dbschema", "quickstart_plugin");
        return datasource;
    }

    private Map<String, Object> duckdbDatasource(Path targetDb) {
        Map<String, Object> datasource = new LinkedHashMap<>();
        datasource.put("type", "DUCKDB");
        datasource.put("url", "jdbc:duckdb:" + targetDb);
        datasource.put("driver", "org.duckdb.DuckDBDriver");
        datasource.put("username", "");
        datasource.put("password", "");
        datasource.put("hostname", "");
        datasource.put("port", "");
        datasource.put("dbschema", "main");
        return datasource;
    }

    private DatasourceInfo duckdbDatasourceInfo(Path targetDb) {
        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setType("DUCKDB");
        datasource.setUrl("jdbc:duckdb:" + targetDb);
        datasource.setDriver("org.duckdb.DuckDBDriver");
        datasource.setUsername("");
        datasource.setPassword("");
        datasource.setDbschema("main");
        return datasource;
    }
}
