package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.data.job.ETLFlowTask;
import com.data.metadata.util.DBUtils;
import java.math.BigDecimal;
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
 * StreamJdbcOutput 跨库同步验证：MySQL→Oracle、MySQL→PostgreSQL、Oracle→PostgreSQL。
 *
 * <p>每个组合覆盖：目标表自动创建、目标表已存在、上下游字段大小写不一致。</p>
 */
public class StreamJdbcOutputCrossDatabaseCaseTest {

    private static final String MYSQL_URL =
        "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "1234qwer";
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";
    private static final String MYSQL_SCHEMA = "test";

    private static final String PG_URL = "jdbc:postgresql://127.0.0.1:5432/testdb";
    private static final String PG_USER = "admin";
    private static final String PG_PASSWORD = "123456";
    private static final String PG_DRIVER = "org.postgresql.Driver";
    private static final String PG_SCHEMA = "public";

    private static final String ORACLE_URL = "jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1";
    private static final String ORACLE_USER = "datay";
    private static final String ORACLE_PASSWORD = "datay123";
    private static final String ORACLE_DRIVER = "oracle.jdbc.OracleDriver";
    private static final String ORACLE_SCHEMA = "DATAY";

    private static final String MYSQL_TO_ORACLE_SOURCE = "sjdo_m2o_src";
    private static final String MYSQL_TO_ORACLE_AUTO_TARGET = "SJDO_M2O_AUTO_TGT";
    private static final String MYSQL_TO_ORACLE_EXIST_TARGET = "SJDO_M2O_EXIST_TGT";

    private static final String MYSQL_TO_PG_AUTO_SOURCE = "sjdo_m2p_auto_src";
    private static final String MYSQL_TO_PG_AUTO_TARGET = "SJDO_M2P_AUTO_TGT";
    private static final String MYSQL_TO_PG_EXIST_SOURCE = "sjdo_m2p_exist_src";
    private static final String MYSQL_TO_PG_EXIST_TARGET = "sjdo_m2p_exist_tgt";

    private static final String MYSQL_TO_PG_LOWER_SOURCE = "sjdo_m2p_lower_src";
    private static final String MYSQL_TO_PG_LOWER_AUTO_TARGET = "SJDO_M2P_LOWER_AUTO_TGT";
    private static final String MYSQL_TO_PG_LOWER_EXIST_TARGET = "sjdo_m2p_lower_exist_tgt";

    private static final String ORACLE_TO_PG_SOURCE = "SJDO_O2P_SRC";
    private static final String ORACLE_TO_PG_AUTO_TARGET = "SJDO_O2P_AUTO_TGT";
    private static final String ORACLE_TO_PG_EXIST_TARGET = "sjdo_o2p_exist_tgt";

    @BeforeEach
    public void checkDatabases() {
        assumeTrue(canConnect(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD), "本地 MySQL 不可用，跳过跨库验证");
        assumeTrue(canConnect(PG_URL, PG_USER, PG_PASSWORD), "本地 PostgreSQL 不可用，跳过跨库验证");
        assumeTrue(canConnect(ORACLE_URL, ORACLE_USER, ORACLE_PASSWORD), "本地 Oracle 不可用，跳过跨库验证");
    }

    @AfterEach
    public void tearDown() {
        try {
            execute(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD, "DROP TABLE IF EXISTS test." + MYSQL_TO_ORACLE_SOURCE);
            execute(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD, "DROP TABLE IF EXISTS test." + MYSQL_TO_PG_AUTO_SOURCE);
            execute(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD, "DROP TABLE IF EXISTS test." + MYSQL_TO_PG_EXIST_SOURCE);
            execute(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD, "DROP TABLE IF EXISTS test." + MYSQL_TO_PG_LOWER_SOURCE);
        } catch (Exception ignored) {
            // 清理失败不影响验证结果
        }
        try {
            execute(PG_URL, PG_USER, PG_PASSWORD, "DROP TABLE IF EXISTS public." + MYSQL_TO_PG_AUTO_TARGET);
            execute(PG_URL, PG_USER, PG_PASSWORD, "DROP TABLE IF EXISTS public." + MYSQL_TO_PG_EXIST_TARGET);
            execute(PG_URL, PG_USER, PG_PASSWORD, "DROP TABLE IF EXISTS public." + MYSQL_TO_PG_LOWER_AUTO_TARGET);
            execute(PG_URL, PG_USER, PG_PASSWORD, "DROP TABLE IF EXISTS public." + MYSQL_TO_PG_LOWER_EXIST_TARGET);
            execute(PG_URL, PG_USER, PG_PASSWORD, "DROP TABLE IF EXISTS public." + ORACLE_TO_PG_AUTO_TARGET);
            execute(PG_URL, PG_USER, PG_PASSWORD, "DROP TABLE IF EXISTS public." + ORACLE_TO_PG_EXIST_TARGET);
        } catch (Exception ignored) {
            // 清理失败不影响验证结果
        }
        try {
            execute(ORACLE_URL, ORACLE_USER, ORACLE_PASSWORD, "DROP TABLE " + ORACLE_SCHEMA + "." + MYSQL_TO_ORACLE_AUTO_TARGET);
        } catch (Exception ignored) {
            // 表可能不存在
        }
        try {
            execute(ORACLE_URL, ORACLE_USER, ORACLE_PASSWORD, "DROP TABLE " + ORACLE_SCHEMA + "." + MYSQL_TO_ORACLE_EXIST_TARGET);
        } catch (Exception ignored) {
            // 表可能不存在
        }
        try {
            execute(ORACLE_URL, ORACLE_USER, ORACLE_PASSWORD, "DROP TABLE " + ORACLE_SCHEMA + "." + ORACLE_TO_PG_SOURCE);
        } catch (Exception ignored) {
            // 表可能不存在
        }
        DBUtils.closePool(MYSQL_URL, MYSQL_USER);
        DBUtils.closePool(PG_URL, PG_USER);
        DBUtils.closePool(ORACLE_URL, ORACLE_USER);
    }

    @Test
    @Timeout(180000)
    public void mysqlToOracleAutoCreateAndExistingTable() throws Exception {
        prepareMysqlSource(MYSQL_TO_ORACLE_SOURCE, false);
        dropOracleTable(MYSQL_TO_ORACLE_AUTO_TARGET);
        dropOracleTable(MYSQL_TO_ORACLE_EXIST_TARGET);

        runJob(job(
            MYSQL_SCHEMA, MYSQL_TO_ORACLE_SOURCE,
            ORACLE_SCHEMA, MYSQL_TO_ORACLE_AUTO_TARGET
        ));
        verifyOracle(MYSQL_TO_ORACLE_AUTO_TARGET);

        // 已存在表：Oracle 未加引号建表，字段名自然大写；源 MySQL 字段名为小写
        execute(
            ORACLE_URL,
            ORACLE_USER,
            ORACLE_PASSWORD,
            "CREATE TABLE " + ORACLE_SCHEMA + "." + MYSQL_TO_ORACLE_EXIST_TARGET
                + " (ID NUMBER(19) PRIMARY KEY, USER_NAME VARCHAR2(50), AMOUNT NUMBER(10,2))"
        );

        runJob(job(
            MYSQL_SCHEMA, MYSQL_TO_ORACLE_SOURCE,
            ORACLE_SCHEMA, MYSQL_TO_ORACLE_EXIST_TARGET
        ));
        verifyOracle(MYSQL_TO_ORACLE_EXIST_TARGET);
    }

    @Test
    @Timeout(180000)
    public void mysqlToPostgresAutoCreateAndExistingTable() throws Exception {
        // 自动建表场景：MySQL 源字段使用大写，PostgreSQL 自动建表后实际字段为小写
        prepareMysqlSource(MYSQL_TO_PG_AUTO_SOURCE, true);
        execute(PG_URL, PG_USER, PG_PASSWORD, "DROP TABLE IF EXISTS public." + MYSQL_TO_PG_AUTO_TARGET);
        runJob(job(
            MYSQL_SCHEMA, MYSQL_TO_PG_AUTO_SOURCE,
            PG_SCHEMA, MYSQL_TO_PG_AUTO_TARGET
        ));
        verifyPostgres(MYSQL_TO_PG_AUTO_TARGET);
        verifyPostgresIdentifiersLowerCase(MYSQL_TO_PG_AUTO_TARGET);

        // 已存在表：目标字段使用带引号的大写，源 MySQL 字段为小写
        prepareMysqlSource(MYSQL_TO_PG_EXIST_SOURCE, false);
        execute(
            PG_URL,
            PG_USER,
            PG_PASSWORD,
            "CREATE TABLE public." + MYSQL_TO_PG_EXIST_TARGET
                + " (\"ID\" BIGINT PRIMARY KEY, \"USER_NAME\" VARCHAR(50), \"AMOUNT\" NUMERIC(10,2))"
        );
        runJob(job(
            MYSQL_SCHEMA, MYSQL_TO_PG_EXIST_SOURCE,
            PG_SCHEMA, MYSQL_TO_PG_EXIST_TARGET
        ));
        verifyPostgres(MYSQL_TO_PG_EXIST_TARGET);
    }

    @Test
    @Timeout(180000)
    public void mysqlLowerCaseToPostgresLowerCase() throws Exception {
        // MySQL 源表全小写
        prepareMysqlSource(MYSQL_TO_PG_LOWER_SOURCE, false);
        verifyMysqlLowercaseIdentifiers(MYSQL_TO_PG_LOWER_SOURCE);

        execute(PG_URL, PG_USER, PG_PASSWORD, "DROP TABLE IF EXISTS public." + lower(MYSQL_TO_PG_LOWER_AUTO_TARGET));
        execute(PG_URL, PG_USER, PG_PASSWORD, "DROP TABLE IF EXISTS public." + lower(MYSQL_TO_PG_LOWER_EXIST_TARGET));

        // 自动建表：即使配置大写目标表名，PostgreSQL 实际表名/字段名仍全小写
        runJob(job(
            MYSQL_SCHEMA, MYSQL_TO_PG_LOWER_SOURCE,
            PG_SCHEMA, MYSQL_TO_PG_LOWER_AUTO_TARGET
        ));
        verifyPostgres(MYSQL_TO_PG_LOWER_AUTO_TARGET);
        verifyPostgresIdentifiersLowerCase(MYSQL_TO_PG_LOWER_AUTO_TARGET);

        // 已存在表：目标表名/字段名全小写
        execute(
            PG_URL,
            PG_USER,
            PG_PASSWORD,
            "CREATE TABLE public." + lower(MYSQL_TO_PG_LOWER_EXIST_TARGET)
                + " (id BIGINT PRIMARY KEY, user_name VARCHAR(50), amount NUMERIC(10,2))"
        );
        runJob(job(
            MYSQL_SCHEMA, MYSQL_TO_PG_LOWER_SOURCE,
            PG_SCHEMA, MYSQL_TO_PG_LOWER_EXIST_TARGET
        ));
        verifyPostgres(MYSQL_TO_PG_LOWER_EXIST_TARGET);
        verifyPostgresIdentifiersLowerCase(MYSQL_TO_PG_LOWER_EXIST_TARGET);
    }

    @Test
    @Timeout(180000)
    public void oracleToPostgresAutoCreateAndExistingTable() throws Exception {
        prepareOracleSource();
        execute(PG_URL, PG_USER, PG_PASSWORD, "DROP TABLE IF EXISTS public." + ORACLE_TO_PG_AUTO_TARGET);
        execute(PG_URL, PG_USER, PG_PASSWORD, "DROP TABLE IF EXISTS public." + ORACLE_TO_PG_EXIST_TARGET);

        runJob(job(
            ORACLE_SCHEMA, ORACLE_TO_PG_SOURCE,
            PG_SCHEMA, ORACLE_TO_PG_AUTO_TARGET
        ));
        verifyPostgres(ORACLE_TO_PG_AUTO_TARGET);
        verifyPostgresIdentifiersLowerCase(ORACLE_TO_PG_AUTO_TARGET);

        // 已存在表：Oracle 源字段大写，PostgreSQL 未加引号建表字段为小写
        execute(
            PG_URL,
            PG_USER,
            PG_PASSWORD,
            "CREATE TABLE public." + ORACLE_TO_PG_EXIST_TARGET
                + " (id BIGINT PRIMARY KEY, user_name VARCHAR(50), amount NUMERIC(10,2))"
        );
        runJob(job(
            ORACLE_SCHEMA, ORACLE_TO_PG_SOURCE,
            PG_SCHEMA, ORACLE_TO_PG_EXIST_TARGET
        ));
        verifyPostgres(ORACLE_TO_PG_EXIST_TARGET);
    }

    private void prepareMysqlSource(String table, boolean uppercaseColumns) throws SQLException {
        String id = uppercaseColumns ? "`ID`" : "`id`";
        String userName = uppercaseColumns ? "`USER_NAME`" : "`user_name`";
        String amount = uppercaseColumns ? "`AMOUNT`" : "`amount`";
        execute(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD, "DROP TABLE IF EXISTS test." + table);
        execute(
            MYSQL_URL,
            MYSQL_USER,
            MYSQL_PASSWORD,
            "CREATE TABLE test." + table + " (" + id + " BIGINT PRIMARY KEY, " + userName
                + " VARCHAR(50), " + amount + " DECIMAL(10,2))"
        );
        execute(
            MYSQL_URL,
            MYSQL_USER,
            MYSQL_PASSWORD,
            "INSERT INTO test." + table + " (" + id + ", " + userName + ", " + amount + ") VALUES "
                + "(1, 'alice', 12.34), (2, 'bob', 56.78)"
        );
    }

    private void prepareOracleSource() throws SQLException {
        dropOracleTable(ORACLE_TO_PG_SOURCE);
        execute(
            ORACLE_URL,
            ORACLE_USER,
            ORACLE_PASSWORD,
            "CREATE TABLE " + ORACLE_SCHEMA + "." + ORACLE_TO_PG_SOURCE
                + " (ID NUMBER(19) PRIMARY KEY, USER_NAME VARCHAR2(50), AMOUNT NUMBER(10,2))"
        );
        execute(
            ORACLE_URL,
            ORACLE_USER,
            ORACLE_PASSWORD,
            "INSERT INTO " + ORACLE_SCHEMA + "." + ORACLE_TO_PG_SOURCE + " (ID, USER_NAME, AMOUNT) VALUES "
                + "(1, 'alice', 12.34)"
        );
        execute(
            ORACLE_URL,
            ORACLE_USER,
            ORACLE_PASSWORD,
            "INSERT INTO " + ORACLE_SCHEMA + "." + ORACLE_TO_PG_SOURCE + " (ID, USER_NAME, AMOUNT) VALUES "
                + "(2, 'bob', 56.78)"
        );
    }

    private void verifyOracle(String table) throws SQLException {
        try (
            Connection conn = DriverManager.getConnection(ORACLE_URL, ORACLE_USER, ORACLE_PASSWORD);
            Statement statement = conn.createStatement();
            ResultSet rs = statement.executeQuery("SELECT * FROM " + ORACLE_SCHEMA + "." + table + " ORDER BY 1")
        ) {
            assertTrue(rs.next(), "Oracle 目标表应有数据: " + table);
            assertEquals(1L, rs.getLong(1));
            assertEquals("alice", rs.getString(2));
            assertEquals(0, new BigDecimal("12.34").compareTo(rs.getBigDecimal(3)));
            assertTrue(rs.next(), "Oracle 目标表应有第二行数据: " + table);
            assertEquals(2L, rs.getLong(1));
            assertEquals("bob", rs.getString(2));
            assertEquals(0, new BigDecimal("56.78").compareTo(rs.getBigDecimal(3)));
            assertFalse(rs.next(), "Oracle 目标表应只有两行数据: " + table);
        }
    }

    private void verifyMysqlLowercaseIdentifiers(String table) throws SQLException {
        try (
            Connection conn = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD);
            Statement statement = conn.createStatement();
            ResultSet rs = statement.executeQuery(
                "select column_name from information_schema.columns where table_schema = 'test' and table_name = '"
                    + table
                    + "' order by ordinal_position"
            )
        ) {
            assertTrue(rs.next(), "MySQL 源表应有字段: " + table);
            int count = 0;
            do {
                String columnName = rs.getString(1);
                assertEquals(
                    columnName.toLowerCase(java.util.Locale.ROOT),
                    columnName,
                    "MySQL 源表字段名应为小写: " + columnName
                );
                count++;
            } while (rs.next());
            assertEquals(3, count, "MySQL 源表字段数量应为 3");
        }
    }

    private void verifyPostgres(String table) throws SQLException {
        String actualTable = table.toLowerCase(java.util.Locale.ROOT);
        try (
            Connection conn = DriverManager.getConnection(PG_URL, PG_USER, PG_PASSWORD);
            Statement statement = conn.createStatement();
            ResultSet rs = statement.executeQuery("SELECT * FROM public." + actualTable + " ORDER BY 1")
        ) {
            assertTrue(rs.next(), "PostgreSQL 目标表应有数据: " + actualTable);
            assertEquals(1L, rs.getLong(1));
            assertEquals("alice", rs.getString(2));
            assertEquals(0, new BigDecimal("12.34").compareTo(rs.getBigDecimal(3)));
            assertTrue(rs.next(), "PostgreSQL 目标表应有第二行数据: " + actualTable);
            assertEquals(2L, rs.getLong(1));
            assertEquals("bob", rs.getString(2));
            assertEquals(0, new BigDecimal("56.78").compareTo(rs.getBigDecimal(3)));
            assertFalse(rs.next(), "PostgreSQL 目标表应只有两行数据: " + actualTable);
        }
    }

    /**
     * PostgreSQL 自动建表：表名与实际字段名都应为小写。
     */
    private void verifyPostgresIdentifiersLowerCase(String configuredTable) throws SQLException {
        String expectedTable = configuredTable.toLowerCase(java.util.Locale.ROOT);
        try (
            Connection conn = DriverManager.getConnection(PG_URL, PG_USER, PG_PASSWORD);
            Statement statement = conn.createStatement()
        ) {
            try (ResultSet rs = statement.executeQuery(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name = '"
                    + expectedTable
                    + "'"
            )) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "PostgreSQL 自动建表应使用小写表名: " + expectedTable);
            }

            try (ResultSet rs = statement.executeQuery(
                "select column_name from information_schema.columns where table_schema = 'public' and table_name = '"
                    + expectedTable
                    + "' order by ordinal_position"
            )) {
                assertTrue(rs.next(), "PostgreSQL 目标表应有字段: " + expectedTable);
                int count = 0;
                do {
                    String columnName = rs.getString(1);
                    assertEquals(
                        columnName.toLowerCase(java.util.Locale.ROOT),
                        columnName,
                        "PostgreSQL 自动建表字段名应为小写: " + columnName
                    );
                    count++;
                } while (rs.next());
                assertEquals(3, count, "PostgreSQL 目标表字段数量应为 3");
            }
        }
    }

    private void runJob(String job) throws Exception {
        new ETLFlowTask().runJob(job);
    }

    private String job(String sourceSchema, String sourceTable, String targetSchema, String targetTable) {
        String sourceType;
        String sourceUrl;
        String sourceUser;
        String sourcePassword;
        String sourceDriver;
        if (ORACLE_SCHEMA.equals(sourceSchema)) {
            sourceType = "oracle";
            sourceUrl = ORACLE_URL;
            sourceUser = ORACLE_USER;
            sourcePassword = ORACLE_PASSWORD;
            sourceDriver = ORACLE_DRIVER;
        } else {
            sourceType = "mysql";
            sourceUrl = MYSQL_URL;
            sourceUser = MYSQL_USER;
            sourcePassword = MYSQL_PASSWORD;
            sourceDriver = MYSQL_DRIVER;
        }

        String targetType;
        String targetUrl;
        String targetUser;
        String targetPassword;
        String targetDriver;
        if (PG_SCHEMA.equals(targetSchema)) {
            targetType = "postgresql";
            targetUrl = PG_URL;
            targetUser = PG_USER;
            targetPassword = PG_PASSWORD;
            targetDriver = PG_DRIVER;
        } else {
            targetType = "oracle";
            targetUrl = ORACLE_URL;
            targetUser = ORACLE_USER;
            targetPassword = ORACLE_PASSWORD;
            targetDriver = ORACLE_DRIVER;
        }

        return """
            {
              "units": [
                {
                  ".id": "StreamJdbcInput",
                  ".name": "StreamJdbcInput",
                  "sourceId": {
                    "type": "%s",
                    "url": "%s",
                    "driver": "%s",
                    "username": "%s",
                    "password": "%s",
                    "dbschema": "%s"
                  },
                  "schema": "%s",
                  "table": "%s"
                },
                {
                  ".id": "StreamJdbcOutput",
                  ".name": "StreamJdbcOutput",
                  "sourceId": {
                    "type": "%s",
                    "url": "%s",
                    "driver": "%s",
                    "username": "%s",
                    "password": "%s",
                    "dbschema": "%s"
                  },
                  "schema": "%s",
                  "table": "%s",
                  "model": "overwrite"
                }
              ],
              "connections": [
                { "sourceId": "StreamJdbcInput", "targetId": "StreamJdbcOutput", "sourcePort": 0 }
              ],
              "version": "1.0.0"
            }
            """.formatted(
            sourceType,
            sourceUrl,
            sourceDriver,
            sourceUser,
            sourcePassword,
            sourceSchema,
            sourceSchema,
            sourceTable,
            targetType,
            targetUrl,
            targetDriver,
            targetUser,
            targetPassword,
            targetSchema,
            targetSchema,
            targetTable
        );
    }

    private boolean canConnect(String url, String user, String password) {
        try (Connection ignored = DriverManager.getConnection(url, user, password)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void execute(String url, String user, String password, String sql) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url, user, password); Statement statement = conn.createStatement()) {
            statement.execute(sql);
        }
    }

    private void dropOracleTable(String table) {
        try {
            execute(ORACLE_URL, ORACLE_USER, ORACLE_PASSWORD, "DROP TABLE " + ORACLE_SCHEMA + "." + table);
        } catch (Exception ignored) {
            // 表可能不存在
        }
    }

    private static String lower(String value) {
        return value.toLowerCase(java.util.Locale.ROOT);
    }
}
