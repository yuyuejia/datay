package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.data.job.DatasourceInfo;
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
 * 验证上下游字段大小写不一致时，StreamJdbcOutput 未配置 header_map 也能自动匹配并写入。
 */
public class StreamJdbcOutputCaseInsensitiveMappingTest {

    private static final String SOURCE_URL = "jdbc:duckdb:./target/stream-jdbc-output-case-source.duckdb";
    private static final String SOURCE_FILE = "./target/stream-jdbc-output-case-source.duckdb";
    private static final String TARGET_URL = "jdbc:duckdb:./target/stream-jdbc-output-case-target.duckdb";
    private static final String TARGET_FILE = "./target/stream-jdbc-output-case-target.duckdb";

    private static final String SOURCE_TABLE = "src_case_table";
    private static final String TARGET_TABLE = "tgt_case_table";

    @BeforeEach
    public void checkDuckdb() {
        assumeTrue(canConnect(SOURCE_URL), "DuckDB 不可用，跳过大小写字段映射验证");
    }

    @AfterEach
    public void tearDown() {
        DBUtils.closePool(SOURCE_URL, null);
        DBUtils.closePool(TARGET_URL, null);
        deleteDatabase(SOURCE_FILE);
        deleteDatabase(TARGET_FILE);
    }

    @Test
    @Timeout(120000)
    public void writesWhenSourceAndTargetColumnCaseDiffers() throws Exception {
        prepareSource();
        prepareTarget();

        new ETLFlowTask().runJob(job());

        try (
            Connection conn = targetConnection();
            Statement statement = conn.createStatement();
            ResultSet rs = statement.executeQuery(
                "select id, user_name from main." + TARGET_TABLE + " order by id"
            )
        ) {
            assertTrue(rs.next(), "目标表应有数据");
            assertEquals(1L, rs.getLong("id"));
            assertEquals("alice", rs.getString("user_name"));

            assertTrue(rs.next(), "目标表应有第二行数据");
            assertEquals(2L, rs.getLong("id"));
            assertEquals("bob", rs.getString("user_name"));

            assertFalse(rs.next(), "目标表应只有两行数据");
        }
    }

    private void prepareSource() throws SQLException {
        DBUtils.closePool(SOURCE_URL, null);
        deleteDatabase(SOURCE_FILE);
        try (Connection conn = DriverManager.getConnection(SOURCE_URL); Statement statement = conn.createStatement()) {
            statement.execute("CREATE TABLE main." + SOURCE_TABLE + " (\"ID\" BIGINT PRIMARY KEY, \"USER_NAME\" VARCHAR(100))");
            statement.execute("INSERT INTO main." + SOURCE_TABLE + " VALUES (1, 'alice'), (2, 'bob')");
        }
    }

    private void prepareTarget() throws SQLException {
        DBUtils.closePool(TARGET_URL, null);
        deleteDatabase(TARGET_FILE);
        try (Connection conn = DriverManager.getConnection(TARGET_URL); Statement statement = conn.createStatement()) {
            statement.execute("CREATE TABLE main." + TARGET_TABLE + " (id BIGINT PRIMARY KEY, user_name VARCHAR(100))");
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
                    "url": "%s",
                    "driver": "org.duckdb.DuckDBDriver",
                    "dbschema": "main"
                  },
                  "schema": "main",
                  "table": "%s",
                  "model": "overwrite"
                }
              ],
              "connections": [
                { "sourceId": "StreamJdbcInput", "targetId": "StreamJdbcOutput", "sourcePort": 0 }
              ],
              "version": "1.0.0"
            }
            """.formatted(SOURCE_URL, SOURCE_TABLE, TARGET_URL, TARGET_TABLE);
    }

    private boolean canConnect(String url) {
        try (Connection ignored = DriverManager.getConnection(url)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Connection targetConnection() throws SQLException {
        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setUrl(TARGET_URL);
        return DBUtils.getConnection(datasource);
    }

    private void deleteDatabase(String path) {
        new File(path).delete();
        new File(path + ".wal").delete();
    }
}
