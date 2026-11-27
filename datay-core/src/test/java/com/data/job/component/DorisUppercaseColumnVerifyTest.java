package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.data.job.ETLFlowTask;
import com.data.metadata.util.DBUtils;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 复现验证：SQL Server 源表字段为大写时，Doris 自动建表字段被转成小写，写入是否失败。
 *
 * <p>源端用 DuckDB 构造带引号的大写字段，等价于 SQL Server 大小写敏感对象名场景。</p>
 */
public class DorisUppercaseColumnVerifyTest {

    private static final String SOURCE_URL = "jdbc:duckdb:./target/doris-uppercase-source.duckdb";
    private static final String SOURCE_FILE = "./target/doris-uppercase-source.duckdb";
    private static final String DORIS_URL = System.getProperty(
        "datay.test.doris.url",
        "jdbc:mysql://127.0.0.1:9030/datay_upper_case_verify?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
    );
    private static final String DORIS_ADMIN_URL = DORIS_URL.replaceFirst("/[^/?]+\\?", "/?");
    private static final String DORIS_USER = System.getProperty("datay.test.doris.user", "root");
    private static final String DORIS_PASSWORD = System.getProperty("datay.test.doris.password", "");
    private static final String DORIS_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String DORIS_SCHEMA = System.getProperty("datay.test.doris.schema", "datay_upper_case_verify");
    private static final String DORIS_FE_ENDPOINT = System.getProperty("datay.test.doris.feEndpoint", "http://127.0.0.1:8030");

    private static final String SOURCE_TABLE = "src_upper";
    private static final String TARGET_TABLE = "tgt_upper_sync";

    @BeforeEach
    public void checkEnv() throws SQLException {
        assumeTrue(canConnect(SOURCE_URL), "DuckDB 不可用，跳过");
        assumeTrue(canConnect(DORIS_ADMIN_URL), "Doris 不可用，跳过");
        try (Connection conn = DriverManager.getConnection(DORIS_ADMIN_URL, DORIS_USER, DORIS_PASSWORD);
             Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS " + DORIS_SCHEMA);
            st.execute("DROP TABLE IF EXISTS " + DORIS_SCHEMA + "." + TARGET_TABLE);
        }
    }

    @AfterEach
    public void tearDown() {
        DBUtils.closePool(SOURCE_URL, null);
        DBUtils.closePool(DORIS_ADMIN_URL, DORIS_USER);
        DBUtils.closePool(DORIS_URL, DORIS_USER);
        new File(SOURCE_FILE).delete();
        new File(SOURCE_FILE + ".wal").delete();
    }

    /**
     * DorisStreamLoad 场景：目标表已由「自动检表/自动建表」提前创建，Doris 实际列为小写，
     * 上游数据字段为大写。
     */
    @Test
    @Timeout(120000)
    public void streamLoadWritesUppercaseSourceIntoExistingDorisTable() throws Exception {
        prepareSource();
        prepareExistingTarget();

        new ETLFlowTask().runJob(streamLoadJob());
        assertTwoRows();
    }

    @Test
    @Timeout(120000)
    public void streamLoadWritesUppercaseSourceIntoAutoCreatedDorisTable() throws Exception {
        prepareSource();

        new ETLFlowTask().runJob(streamLoadJob());
        assertTwoRows();
    }

    @Test
    @Timeout(120000)
    public void writesUppercaseSourceIntoAutoCreatedDorisTable() throws Exception {
        prepareSource();

        new ETLFlowTask().runJob(job());
        assertTwoRows();
    }

    private void assertTwoRows() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DORIS_URL, DORIS_USER, DORIS_PASSWORD);
             Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery("SHOW CREATE TABLE " + DORIS_SCHEMA + "." + TARGET_TABLE)) {
                assertTrue(rs.next());
                System.out.println("=== Doris DDL ===\n" + rs.getString(2));
            }
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + DORIS_SCHEMA + "." + TARGET_TABLE)) {
                assertTrue(rs.next());
                System.out.println("=== row count === " + rs.getInt(1));
                assertEquals(2, rs.getInt(1));
            }
            try (ResultSet rs = st.executeQuery(
                "SELECT id, user_name FROM " + DORIS_SCHEMA + "." + TARGET_TABLE + " ORDER BY id")) {
                assertTrue(rs.next());
                assertEquals(1L, rs.getLong(1));
                assertEquals("alice", rs.getString(2));
                assertTrue(rs.next());
                assertEquals(2L, rs.getLong(1));
                assertEquals("bob", rs.getString(2));
                assertFalse(rs.next());
            }
        }
    }

    private void prepareExistingTarget() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DORIS_URL, DORIS_USER, DORIS_PASSWORD);
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE " + DORIS_SCHEMA + "." + TARGET_TABLE
                + " (`id` BIGINT, `user_name` VARCHAR(100)) UNIQUE KEY(`id`)"
                + " DISTRIBUTED BY HASH(`id`) BUCKETS 1 PROPERTIES('replication_num'='1')");
        }
    }

    private void prepareSource() throws SQLException {
        DBUtils.closePool(SOURCE_URL, null);
        new File(SOURCE_FILE).delete();
        try (Connection conn = DriverManager.getConnection(SOURCE_URL); Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE main." + SOURCE_TABLE + " (\"ID\" BIGINT PRIMARY KEY, \"USER_NAME\" VARCHAR(100))");
            st.execute("INSERT INTO main." + SOURCE_TABLE + " VALUES (1, 'alice'), (2, 'bob')");
        }
    }

    private String job() {
        return """
            {
              "units": [
                {
                  ".id": "StreamJdbcInput",
                  ".name": "StreamJdbcInput",
                  "sourceId": {
                    "type": "duckdb",
                    "url": "%s",
                    "driver": "org.duckdb.DuckDBDriver",
                    "dbschema": "main"
                  },
                  "schema": "main",
                  "table": "%s"
                },
                {
                  ".id": "StreamJdbcOutput",
                  ".name": "StreamJdbcOutput",
                  "sourceId": {
                    "type": "doris",
                    "url": "%s",
                    "driver": "%s",
                    "username": "%s",
                    "password": "%s",
                    "dbschema": "%s"
                  },
                  "schema": "%s",
                  "table": "%s",
                  "model": "overwrite",
                  "dropIfTableExists": true
                }
              ],
              "connections": [
                { "sourceId": "StreamJdbcInput", "targetId": "StreamJdbcOutput", "sourcePort": 0 }
              ],
              "version": "1.0.0"
            }
            """.formatted(
            SOURCE_URL, SOURCE_TABLE,
            DORIS_URL, DORIS_DRIVER, DORIS_USER, DORIS_PASSWORD, DORIS_SCHEMA,
            DORIS_SCHEMA, TARGET_TABLE
        );
    }

    private String streamLoadJob() {
        return """
            {
              "units": [
                {
                  ".id": "StreamJdbcInput",
                  ".name": "StreamJdbcInput",
                  "sourceId": {
                    "type": "duckdb",
                    "url": "%s",
                    "driver": "org.duckdb.DuckDBDriver",
                    "dbschema": "main"
                  },
                  "schema": "main",
                  "table": "%s"
                },
                {
                  ".id": "DorisStreamLoad",
                  ".name": "DorisStreamLoad",
                  "sourceId": {
                    "type": "doris",
                    "url": "%s",
                    "driver": "%s",
                    "username": "%s",
                    "password": "%s",
                    "dbschema": "%s",
                    "extraParams": { "fe_endpoint": "%s" }
                  },
                  "schema": "%s",
                  "table": "%s",
                  "model": "insert"
                }
              ],
              "connections": [
                { "sourceId": "StreamJdbcInput", "targetId": "DorisStreamLoad", "sourcePort": 0 }
              ],
              "version": "1.0.0"
            }
            """.formatted(
            SOURCE_URL, SOURCE_TABLE,
            DORIS_URL, DORIS_DRIVER, DORIS_USER, DORIS_PASSWORD, DORIS_SCHEMA, DORIS_FE_ENDPOINT,
            DORIS_SCHEMA, TARGET_TABLE
        );
    }

    private boolean canConnect(String url) {
        try (Connection ignored = DriverManager.getConnection(url, url.startsWith("jdbc:mysql") ? DORIS_USER : "", url.startsWith("jdbc:mysql") ? DORIS_PASSWORD : "")) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
