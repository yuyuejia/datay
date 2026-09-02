package com.data.job.component;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import com.data.job.ETLFlowTask;

/**
 * 端到端验证：MySQL -> 飞书多维表格 写入
 * <p>
 * 依赖本地 MySQL（127.0.0.1:3306），可通过系统属性覆盖。
 * 注意：需要飞书应用具备多维表格编辑权限（bitable:app:edit），否则写入返回 91403 Forbidden。
 */
public class MysqlToFeishuSyncTest {

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
    public void testMysqlToFeishuWrite() throws Exception {
        prepareSourceTable();

        String job = "{\n" +
            "  \"units\": [\n" +
            "    {\n" +
            "      \".id\": \"StreamJdbcInput\",\n" +
            "      \".name\": \"StreamJdbcInput\",\n" +
            "      \"datasource\": {\n" +
            "        \"url\": \"" + MYSQL_URL + "\",\n" +
            "        \"driver\": \"com.mysql.cj.jdbc.Driver\",\n" +
            "        \"username\": \"" + MYSQL_USER + "\",\n" +
            "        \"password\": \"" + MYSQL_PASSWORD + "\",\n" +
            "        \"dbschema\": \"" + DBSCHEMA + "\"\n" +
            "      },\n" +
            "      \"table\": \"feishu_write_source\"\n" +
            "    },\n" +
            "    {\n" +
            "      \".id\": \"FeishuBitableOutput\",\n" +
            "      \".name\": \"FeishuBitableOutput\",\n" +
            "      \"appId\": \"" + APP_ID + "\",\n" +
            "      \"appSecret\": \"" + APP_SECRET + "\",\n" +
            "      \"appToken\": \"" + APP_TOKEN + "\",\n" +
            "      \"tableId\": \"" + TABLE_ID + "\",\n" +
            "      \"batchSize\": \"100\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"connections\": [\n" +
            "    {\n" +
            "      \"sourceId\": \"StreamJdbcInput\",\n" +
            "      \"targetId\": \"FeishuBitableOutput\"\n" +
            "    }\n" +
            "  ]\n" +
            "}";

        ETLFlowTask runner = new ETLFlowTask();
        runner.runJob(job);
    }

    private void prepareSourceTable() throws Exception {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS feishu_write_source");
            stmt.execute("CREATE TABLE feishu_write_source (" +
                "`文本` VARCHAR(255), " +
                "`单选` VARCHAR(255), " +
                "`日期` BIGINT" +
                ")");
            stmt.execute("INSERT INTO feishu_write_source VALUES ('mysql写入1', '11', 1788192000000)");
            stmt.execute("INSERT INTO feishu_write_source VALUES ('mysql写入2', '11', 1788192000000)");
        }
    }

    private Connection getConnection() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        return DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
    }
}
