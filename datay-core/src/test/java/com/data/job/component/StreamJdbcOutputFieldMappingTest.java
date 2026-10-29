package com.data.job.component;

import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 验证 StreamJdbcOutput 的 header_map 字段映射：
 * 源表与目标表字段名不同、字段数量不同时的 MySQL -> DuckDB / MySQL -> PostgreSQL 场景。
 */
public class StreamJdbcOutputFieldMappingTest {

    private static final String MYSQL_URL =
        "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "1234qwer";

    private static final String PG_URL = "jdbc:postgresql://127.0.0.1:5432/testdb";
    private static final String PG_USER = "admin";
    private static final String PG_PASSWORD = "123456";

    private static final String DUCK_URL = "jdbc:duckdb:./target/stream-jdbc-output-mapping.duckdb";
    private static final String DUCK_FILE = "./target/stream-jdbc-output-mapping.duckdb";

    private static final String MYSQL_SOURCE_TABLE = "sjdo_src_duck";
    private static final String MYSQL_PG_SOURCE_TABLE = "sjdo_src_pg";
    private static final String DUCK_TARGET_TABLE = "sjdo_duck";
    private static final String PG_TARGET_TABLE = "sjdo_pg";

    @BeforeEach
    public void checkDatabases() {
        assumeTrue(canConnect(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD), "本地 MySQL 不可用，跳过字段映射验证");
        assumeTrue(canConnect(PG_URL, PG_USER, PG_PASSWORD), "本地 PostgreSQL 不可用，跳过字段映射验证");
    }

    @AfterEach
    public void tearDown() {
        DBUtils.closePool(DUCK_URL, null);
        DBUtils.closePool(PG_URL, PG_USER);
        new File(DUCK_FILE).delete();
        new File(DUCK_FILE + ".wal").delete();
    }

    @Test
    @Timeout(120000)
    public void testMysqlToDuckdbWithDifferentColumnNamesAndCount() throws Exception {
        prepareMysqlSourceForDuck();
        prepareDuckTarget();

        new ETLFlowTask().runJob(duckJob());

        try (
            Connection conn = duckConnection();
            Statement statement = conn.createStatement();
            ResultSet rs = statement.executeQuery(
                "select id, customer_name, total_amount, remark, create_time from main." + DUCK_TARGET_TABLE + " order by id"
            )
        ) {
            assertTrue(rs.next(), "DuckDB 目标表应有数据");
            assertEquals(1L, rs.getLong("id"));
            assertEquals("张三", rs.getString("customer_name"));
            assertEquals(0, new BigDecimal("123.45").compareTo(rs.getBigDecimal("total_amount")));
            assertEquals("首单", rs.getString("remark"));
            assertNotNull(rs.getTimestamp("create_time"));

            assertTrue(rs.next(), "DuckDB 目标表应有第二行数据");
            assertEquals(2L, rs.getLong("id"));
            assertEquals("李四", rs.getString("customer_name"));
            assertEquals(0, new BigDecimal("67.89").compareTo(rs.getBigDecimal("total_amount")));
            assertEquals("加急", rs.getString("remark"));
            assertNotNull(rs.getTimestamp("create_time"));

            assertFalse(rs.next(), "DuckDB 目标表应只有两行数据");
        }

        // 源表 order_no 未出现在目标表中
        try (
            Connection conn = duckConnection();
            Statement statement = conn.createStatement();
            ResultSet rs = statement.executeQuery(
                "select count(*) from information_schema.columns where table_schema = 'main' and table_name = '" + DUCK_TARGET_TABLE
                    + "' and column_name = 'order_no'"
            )
        ) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "目标表不应包含源表多余的 order_no 字段");
        }
    }

    @Test
    @Timeout(120000)
    public void testMysqlToPostgresWithDifferentColumnNamesAndCount() throws Exception {
        prepareMysqlSourceForPostgres();
        preparePostgresTarget();

        new ETLFlowTask().runJob(postgresJob());

        try (
            Connection conn = DriverManager.getConnection(PG_URL, PG_USER, PG_PASSWORD);
            Statement statement = conn.createStatement();
            ResultSet rs = statement.executeQuery(
                "select id, sku, quantity, unit_price, note, extra_col from public." + PG_TARGET_TABLE + " order by id"
            )
        ) {
            assertTrue(rs.next(), "PostgreSQL 目标表应有数据");
            assertEquals(10L, rs.getLong("id"));
            assertEquals("SKU-A", rs.getString("sku"));
            assertEquals(3, rs.getInt("quantity"));
            assertEquals(0, new BigDecimal("99.90").compareTo(rs.getBigDecimal("unit_price")));
            assertEquals("备注A", rs.getString("note"));
            // 目标表独有字段未配置映射，应为空
            assertNull(rs.getString("extra_col"));

            assertTrue(rs.next(), "PostgreSQL 目标表应有第二行数据");
            assertEquals(11L, rs.getLong("id"));
            assertEquals("SKU-B", rs.getString("sku"));
            assertEquals(5, rs.getInt("quantity"));
            assertEquals(0, new BigDecimal("12.50").compareTo(rs.getBigDecimal("unit_price")));
            assertEquals("备注B", rs.getString("note"));
            assertNull(rs.getString("extra_col"));

            assertFalse(rs.next(), "PostgreSQL 目标表应只有两行数据");
        }

        // 源表 sku_code 已映射为 sku，目标表中不应存在 sku_code
        try (
            Connection conn = DriverManager.getConnection(PG_URL, PG_USER, PG_PASSWORD);
            Statement statement = conn.createStatement();
            ResultSet rs = statement.executeQuery(
                "select count(*) from information_schema.columns where table_schema = 'public' and table_name = '" + PG_TARGET_TABLE
                    + "' and column_name = 'sku_code'"
            )
        ) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1), "目标表不应包含源表字段 sku_code");
        }
    }

    private void prepareMysqlSourceForDuck() throws SQLException {
        try (Connection conn = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD); Statement statement = conn.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS test." + MYSQL_SOURCE_TABLE);
            statement.execute(
                "CREATE TABLE test." + MYSQL_SOURCE_TABLE + " ("
                    + "id BIGINT PRIMARY KEY,"
                    + "order_no VARCHAR(50),"
                    + "customer_name VARCHAR(100),"
                    + "amount DECIMAL(12,2),"
                    + "remark VARCHAR(200),"
                    + "created_at DATETIME"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            statement.execute(
                "INSERT INTO test." + MYSQL_SOURCE_TABLE + " (id, order_no, customer_name, amount, remark, created_at) VALUES "
                    + "(1, 'SO-001', '张三', 123.45, '首单', '2024-01-01 10:00:00'),"
                    + "(2, 'SO-002', '李四', 67.89, '加急', '2024-01-02 11:30:00')"
            );
        }
    }

    private void prepareMysqlSourceForPostgres() throws SQLException {
        try (Connection conn = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD); Statement statement = conn.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS test." + MYSQL_PG_SOURCE_TABLE);
            statement.execute(
                "CREATE TABLE test." + MYSQL_PG_SOURCE_TABLE + " ("
                    + "id BIGINT PRIMARY KEY,"
                    + "sku_code VARCHAR(50),"
                    + "qty INT,"
                    + "price DECIMAL(12,2),"
                    + "memo VARCHAR(200)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            statement.execute(
                "INSERT INTO test." + MYSQL_PG_SOURCE_TABLE + " (id, sku_code, qty, price, memo) VALUES "
                    + "(10, 'SKU-A', 3, 99.90, '备注A'),"
                    + "(11, 'SKU-B', 5, 12.50, '备注B')"
            );
        }
    }

    private void prepareDuckTarget() throws SQLException {
        DBUtils.closePool(DUCK_URL, null);
        new File(DUCK_FILE).delete();
        new File(DUCK_FILE + ".wal").delete();
        try (Connection conn = DriverManager.getConnection(DUCK_URL); Statement statement = conn.createStatement()) {
            statement.execute(
                "CREATE TABLE main." + DUCK_TARGET_TABLE + " ("
                    + "customer_name VARCHAR,"
                    + "id BIGINT PRIMARY KEY,"
                    + "create_time TIMESTAMP,"
                    + "total_amount DECIMAL(18,2),"
                    + "remark VARCHAR"
                    + ")"
            );
        }
    }

    private void preparePostgresTarget() throws SQLException {
        try (Connection conn = DriverManager.getConnection(PG_URL, PG_USER, PG_PASSWORD); Statement statement = conn.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS public." + PG_TARGET_TABLE);
            statement.execute(
                "CREATE TABLE public." + PG_TARGET_TABLE + " ("
                    + "sku VARCHAR(50),"
                    + "id BIGINT PRIMARY KEY,"
                    + "note TEXT,"
                    + "quantity INTEGER,"
                    + "extra_col VARCHAR(20),"
                    + "unit_price NUMERIC(12,2)"
                    + ")"
            );
        }
    }

    private String duckJob() {
        return """
            {
              "units": [
                {
                  ".id": "StreamJdbcInput",
                  ".name": "StreamJdbcInput",
                  "sourceId": {
                    "url": "%s",
                    "driver": "com.mysql.cj.jdbc.Driver",
                    "username": "%s",
                    "password": "%s",
                    "dbschema": "test"
                  },
                  "schema": "test",
                  "table": "%s"
                },
                {
                  ".id": "StreamJdbcOutput",
                  ".name": "StreamJdbcOutput",
                  "sourceId": {
                    "url": "%s",
                    "driver": "org.duckdb.DuckDBDriver",
                    "dbschema": "main"
                  },
                  "schema": "main",
                  "table": "%s",
                  "model": "overwrite",
                  "header_map": [
                    { "source": "id", "target": "id" },
                    { "source": "customer_name", "target": "customer_name" },
                    { "source": "amount", "target": "total_amount" },
                    { "source": "remark", "target": "remark" },
                    { "source": "created_at", "target": "create_time" }
                  ]
                }
              ],
              "connections": [
                { "sourceId": "StreamJdbcInput", "targetId": "StreamJdbcOutput", "sourcePort": 0 }
              ],
              "version": "1.0.0"
            }
            """.formatted(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD, MYSQL_SOURCE_TABLE, DUCK_URL, DUCK_TARGET_TABLE);
    }

    private String postgresJob() {
        return """
            {
              "units": [
                {
                  ".id": "StreamJdbcInput",
                  ".name": "StreamJdbcInput",
                  "sourceId": {
                    "url": "%s",
                    "driver": "com.mysql.cj.jdbc.Driver",
                    "username": "%s",
                    "password": "%s",
                    "dbschema": "test"
                  },
                  "schema": "test",
                  "table": "%s"
                },
                {
                  ".id": "StreamJdbcOutput",
                  ".name": "StreamJdbcOutput",
                  "sourceId": {
                    "url": "%s",
                    "driver": "org.postgresql.Driver",
                    "username": "%s",
                    "password": "%s",
                    "dbschema": "public"
                  },
                  "schema": "public",
                  "table": "%s",
                  "model": "overwrite",
                  "header_map": [
                    { "source": "id", "target": "id" },
                    { "source": "sku_code", "target": "sku" },
                    { "source": "qty", "target": "quantity" },
                    { "source": "price", "target": "unit_price" },
                    { "source": "memo", "target": "note" }
                  ]
                }
              ],
              "connections": [
                { "sourceId": "StreamJdbcInput", "targetId": "StreamJdbcOutput", "sourcePort": 0 }
              ],
              "version": "1.0.0"
            }
            """.formatted(
            MYSQL_URL,
            MYSQL_USER,
            MYSQL_PASSWORD,
            MYSQL_PG_SOURCE_TABLE,
            PG_URL,
            PG_USER,
            PG_PASSWORD,
            PG_TARGET_TABLE
        );
    }

    private boolean canConnect(String url, String user, String password) {
        try (Connection ignored = DriverManager.getConnection(url, user, password)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Connection duckConnection() throws SQLException {
        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setUrl(DUCK_URL);
        return DBUtils.getConnection(datasource);
    }
}
