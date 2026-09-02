package com.data.job.component;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.job.ETLFlowTask;

/**
 * 端到端验证：飞书多维表格 -> MySQL 同步
 * <p>
 * 依赖本地 MySQL（127.0.0.1:3306），可通过系统属性覆盖：
 *   -Dmysql.url=... -Dmysql.username=... -Dmysql.password=... -Dmysql.dbschema=...
 */
public class FeishuToMysqlSyncTest {

    private static final String APP_ID = System.getProperty("feishu.appId", "cli_aa1c93b88bf8dcb0");
    private static final String APP_SECRET = System.getProperty("feishu.appSecret", "");
    private static final String APP_TOKEN = System.getProperty("feishu.appToken", "EEmSbfNF8aD2RcsP26CcU3Bvnee");
    private static final String TABLE_ID = System.getProperty("feishu.tableId", "tblpjHsRMt8moPj8");

    private static final String MYSQL_URL = System.getProperty("mysql.url", "jdbc:mysql://127.0.0.1:3306/test_db");
    private static final String MYSQL_USER = System.getProperty("mysql.username", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("mysql.password", "1234qwer");
    private static final String DBSCHEMA = System.getProperty("mysql.dbschema", "test_db");

    @Test
    @Timeout(120000)
    public void testFeishuToMysqlSync() throws Exception {
        prepareTable();

        String job = "{\n" +
            "  \"units\": [\n" +
            "    {\n" +
            "      \".id\": \"FeishuBitableInput\",\n" +
            "      \".name\": \"FeishuBitableInput\",\n" +
            "      \"appId\": \"" + APP_ID + "\",\n" +
            "      \"appSecret\": \"" + APP_SECRET + "\",\n" +
            "      \"appToken\": \"" + APP_TOKEN + "\",\n" +
            "      \"tableId\": \"" + TABLE_ID + "\",\n" +
            "      \"pageSize\": \"100\"\n" +
            "    },\n" +
            "    {\n" +
            "      \".id\": \"StreamJdbcOutput\",\n" +
            "      \".name\": \"StreamJdbcOutput\",\n" +
            "      \"datasource\": {\n" +
            "        \"url\": \"" + MYSQL_URL + "\",\n" +
            "        \"driver\": \"com.mysql.cj.jdbc.Driver\",\n" +
            "        \"username\": \"" + MYSQL_USER + "\",\n" +
            "        \"password\": \"" + MYSQL_PASSWORD + "\",\n" +
            "        \"dbschema\": \"" + DBSCHEMA + "\"\n" +
            "      },\n" +
            "      \"table\": \"feishu_user\",\n" +
            "      \"model\": \"append\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"connections\": [\n" +
            "    {\n" +
            "      \"sourceId\": \"FeishuBitableInput\",\n" +
            "      \"targetId\": \"StreamJdbcOutput\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";

        ETLFlowTask runner = new ETLFlowTask();
        runner.runJob(job);

        verifyData();
    }

    private void prepareTable() throws Exception {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS feishu_user");
            stmt.execute("CREATE TABLE feishu_user (" +
                "record_id VARCHAR(64) PRIMARY KEY, " +
                "`文本` VARCHAR(255), " +
                "`单选` VARCHAR(255), " +
                "`日期` BIGINT, " +
                "`附件` VARCHAR(1024), " +
                "created_time BIGINT, " +
                "last_modified_time BIGINT" +
                ")");
        }
    }

    private void verifyData() throws Exception {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM feishu_user")) {
                rs.next();
                assertTrue(rs.getInt("cnt") >= 10);
            }

            try (ResultSet rs = stmt.executeQuery(
                "SELECT `文本`, `单选`, `日期`, created_time, last_modified_time FROM feishu_user WHERE record_id = 'recNtT11b1'")) {
                assertNotNull(rs);
                rs.next();
                assertEquals("1", rs.getString("文本"));
                assertEquals("11", rs.getString("单选"));
                assertEquals(1788192000000L, rs.getLong("日期"));
                assertTrue(rs.getLong("created_time") > 0);
                assertTrue(rs.getLong("last_modified_time") > 0);
            }
        }
    }

    @Test
    @Timeout(120000)
    public void testFeishuToMysqlAutoCreate() throws Exception {
        String table = "feishu_" + TABLE_ID;
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + table);
        }

        String job = "{\n" +
            "  \"units\": [\n" +
            "    {\n" +
            "      \".id\": \"FeishuBitableInput\",\n" +
            "      \".name\": \"FeishuBitableInput\",\n" +
            "      \"appId\": \"" + APP_ID + "\",\n" +
            "      \"appSecret\": \"" + APP_SECRET + "\",\n" +
            "      \"appToken\": \"" + APP_TOKEN + "\",\n" +
            "      \"tableId\": \"" + TABLE_ID + "\",\n" +
            "      \"pageSize\": \"100\"\n" +
            "    },\n" +
            "    {\n" +
            "      \".id\": \"StreamJdbcOutput\",\n" +
            "      \".name\": \"StreamJdbcOutput\",\n" +
            "      \"datasource\": {\n" +
            "        \"url\": \"" + MYSQL_URL + "\",\n" +
            "        \"driver\": \"com.mysql.cj.jdbc.Driver\",\n" +
            "        \"username\": \"" + MYSQL_USER + "\",\n" +
            "        \"password\": \"" + MYSQL_PASSWORD + "\",\n" +
            "        \"dbschema\": \"" + DBSCHEMA + "\"\n" +
            "      },\n" +
            "      \"schema\": \"" + DBSCHEMA + "\",\n" +
            "      \"model\": \"append\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"connections\": [\n" +
            "    {\n" +
            "      \"sourceId\": \"FeishuBitableInput\",\n" +
            "      \"targetId\": \"StreamJdbcOutput\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";

        ETLFlowTask runner = new ETLFlowTask();
        runner.runJob(job);

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM " + table)) {
                rs.next();
                assertTrue(rs.getInt("cnt") >= 10);
            }
            try (ResultSet rs = stmt.executeQuery(
                "SELECT `文本`, `单选`, `日期`, created_time, last_modified_time FROM " + table + " WHERE record_id = 'recNtT11b1'")) {
                rs.next();
                assertEquals("1", rs.getString("文本"));
                assertEquals("11", rs.getString("单选"));
                assertEquals(1788192000000L, rs.getLong("日期"));
                assertTrue(rs.getLong("created_time") > 0);
                assertTrue(rs.getLong("last_modified_time") > 0);
            }
        }
    }

    private Connection getConnection() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
    }
}
