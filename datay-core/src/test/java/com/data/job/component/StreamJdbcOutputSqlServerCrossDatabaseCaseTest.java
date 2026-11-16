package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.metadata.util.DBUtils;
import java.math.BigDecimal;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * StreamJdbcOutput 涉及 SQL Server 的跨库同步验证：
 * SQL Server→PostgreSQL、SQL Server→Oracle、MySQL→SQL Server、Oracle→SQL Server。
 *
 * <p>每个组合覆盖：目标表自动创建、目标表已存在、上下游字段大小写不一致。</p>
 */
public class StreamJdbcOutputSqlServerCrossDatabaseCaseTest {

    private static final Db MYSQL = new Db(
        "mysql",
        "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai",
        "com.mysql.cj.jdbc.Driver",
        "root",
        "1234qwer",
        "test"
    );
    private static final Db PG = new Db(
        "postgresql",
        "jdbc:postgresql://127.0.0.1:5432/testdb",
        "org.postgresql.Driver",
        "admin",
        "123456",
        "public"
    );
    private static final Db ORACLE = new Db(
        "oracle",
        "jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1",
        "oracle.jdbc.OracleDriver",
        "datay",
        "datay123",
        "DATAY"
    );
    private static final Db SQLSERVER = new Db(
        "sqlserver",
        "jdbc:sqlserver://127.0.0.1:1433;databaseName=datay_verify;encrypt=false;trustServerCertificate=true",
        "com.microsoft.sqlserver.jdbc.SQLServerDriver",
        "sa",
        "Datay@12345",
        "dbo"
    );

    private static final String SQLSERVER_TO_PG_SOURCE = "sjdo_s2p_src";
    private static final String SQLSERVER_TO_PG_AUTO_TARGET = "SJDO_S2P_AUTO_TGT";
    private static final String SQLSERVER_TO_PG_EXIST_TARGET = "sjdo_s2p_exist_tgt";

    private static final String SQLSERVER_TO_ORACLE_SOURCE = "sjdo_s2o_src";
    private static final String SQLSERVER_TO_ORACLE_AUTO_TARGET = "SJDO_S2O_AUTO_TGT";
    private static final String SQLSERVER_TO_ORACLE_EXIST_TARGET = "SJDO_S2O_EXIST_TGT";

    private static final String MYSQL_TO_SQLSERVER_SOURCE = "sjdo_m2s_src";
    private static final String MYSQL_TO_SQLSERVER_AUTO_TARGET = "sjdo_m2s_auto_tgt";
    private static final String MYSQL_TO_SQLSERVER_EXIST_TARGET = "sjdo_m2s_exist_tgt";

    private static final String ORACLE_TO_SQLSERVER_SOURCE = "SJDO_O2S_SRC";
    private static final String ORACLE_TO_SQLSERVER_AUTO_TARGET = "sjdo_o2s_auto_tgt";
    private static final String ORACLE_TO_SQLSERVER_EXIST_TARGET = "sjdo_o2s_exist_tgt";

    /** SQL Server 驱动位于仓库根目录 drivers/sqlserver/default，运行期复制到当前模块工作目录。 */
    private static final Path LOCAL_DRIVER_DIR = Path.of("drivers", "sqlserver", "default");
    private static boolean localDriverDirCreated;

    @BeforeAll
    public static void ensureSqlServerDriver() throws Exception {
        if (hasJar(LOCAL_DRIVER_DIR)) {
            return;
        }
        Path repoDriverDir = findRepoDriverDir();
        if (repoDriverDir == null) {
            return;
        }
        Files.createDirectories(LOCAL_DRIVER_DIR);
        localDriverDirCreated = true;
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(repoDriverDir, "*.jar")) {
            for (Path jar : jars) {
                Files.copy(jar, LOCAL_DRIVER_DIR.resolve(jar.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    @AfterAll
    public static void cleanSqlServerDriver() throws Exception {
        if (!localDriverDirCreated) {
            return;
        }
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(LOCAL_DRIVER_DIR, "*.jar")) {
            for (Path jar : jars) {
                Files.deleteIfExists(jar);
            }
        }
        Files.deleteIfExists(LOCAL_DRIVER_DIR);
        Files.deleteIfExists(LOCAL_DRIVER_DIR.getParent());
        Files.deleteIfExists(LOCAL_DRIVER_DIR.getParent().getParent());
    }

    private static Path findRepoDriverDir() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 4 && current != null; i++) {
            Path candidate = current.resolve("drivers").resolve("sqlserver").resolve("default");
            if (hasJar(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        return null;
    }

    private static boolean hasJar(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(dir, "*.jar")) {
            return jars.iterator().hasNext();
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeEach
    public void checkDatabases() {
        assumeTrue(canConnect(MYSQL), "本地 MySQL 不可用，跳过 SQL Server 跨库验证");
        assumeTrue(canConnect(PG), "本地 PostgreSQL 不可用，跳过 SQL Server 跨库验证");
        assumeTrue(canConnect(ORACLE), "本地 Oracle 不可用，跳过 SQL Server 跨库验证");
        assumeTrue(canConnect(SQLSERVER), "本地 SQL Server 不可用，跳过 SQL Server 跨库验证");
    }

    @AfterEach
    public void tearDown() {
        dropQuietly(MYSQL, "test." + MYSQL_TO_SQLSERVER_SOURCE);
        dropQuietly(PG, "public." + lower(SQLSERVER_TO_PG_AUTO_TARGET));
        dropQuietly(PG, "public." + lower(SQLSERVER_TO_PG_EXIST_TARGET));
        dropQuietly(SQLSERVER, "dbo." + MYSQL_TO_SQLSERVER_AUTO_TARGET);
        dropQuietly(SQLSERVER, "dbo." + MYSQL_TO_SQLSERVER_EXIST_TARGET);
        dropQuietly(SQLSERVER, "dbo." + ORACLE_TO_SQLSERVER_AUTO_TARGET);
        dropQuietly(SQLSERVER, "dbo." + ORACLE_TO_SQLSERVER_EXIST_TARGET);
        dropQuietly(SQLSERVER, "dbo." + SQLSERVER_TO_PG_SOURCE);
        dropQuietly(SQLSERVER, "dbo." + SQLSERVER_TO_ORACLE_SOURCE);
        dropQuietly(ORACLE, ORACLE.schema() + "." + SQLSERVER_TO_ORACLE_AUTO_TARGET);
        dropQuietly(ORACLE, ORACLE.schema() + "." + SQLSERVER_TO_ORACLE_EXIST_TARGET);
        dropQuietly(ORACLE, ORACLE.schema() + "." + ORACLE_TO_SQLSERVER_SOURCE);

        DBUtils.closePool(MYSQL.url(), MYSQL.user());
        DBUtils.closePool(PG.url(), PG.user());
        DBUtils.closePool(ORACLE.url(), ORACLE.user());
        DBUtils.closePool(SQLSERVER.url(), SQLSERVER.user());
    }

    @Test
    @Timeout(240000)
    public void sqlServerToPostgresAutoCreateAndExistingTable() throws Exception {
        // 源字段大写，自动建表后 PG 字段为小写
        prepareSqlServerSource(SQLSERVER_TO_PG_SOURCE, true);
        dropQuietly(PG, "public." + lower(SQLSERVER_TO_PG_AUTO_TARGET));
        dropQuietly(PG, "public." + lower(SQLSERVER_TO_PG_EXIST_TARGET));

        runJob(job(SQLSERVER, SQLSERVER_TO_PG_SOURCE, PG, SQLSERVER_TO_PG_AUTO_TARGET));
        verifyPostgres(SQLSERVER_TO_PG_AUTO_TARGET);
        verifyPostgresLowercaseIdentifiers(SQLSERVER_TO_PG_AUTO_TARGET);

        // 已存在表字段小写，源 SQL Server 字段大写
        execute(PG, "CREATE TABLE public." + lower(SQLSERVER_TO_PG_EXIST_TARGET)
            + " (id BIGINT PRIMARY KEY, user_name VARCHAR(50), amount NUMERIC(10,2))");
        runJob(job(SQLSERVER, SQLSERVER_TO_PG_SOURCE, PG, SQLSERVER_TO_PG_EXIST_TARGET));
        verifyPostgres(SQLSERVER_TO_PG_EXIST_TARGET);
    }

    @Test
    @Timeout(240000)
    public void sqlServerToOracleAutoCreateAndExistingTable() throws Exception {
        // 源字段小写，Oracle 建表/已存在表字段为大写
        prepareSqlServerSource(SQLSERVER_TO_ORACLE_SOURCE, false);
        dropQuietly(ORACLE, ORACLE.schema() + "." + SQLSERVER_TO_ORACLE_AUTO_TARGET);
        dropQuietly(ORACLE, ORACLE.schema() + "." + SQLSERVER_TO_ORACLE_EXIST_TARGET);

        runJob(job(SQLSERVER, SQLSERVER_TO_ORACLE_SOURCE, ORACLE, SQLSERVER_TO_ORACLE_AUTO_TARGET));
        verifyOracle(SQLSERVER_TO_ORACLE_AUTO_TARGET);

        execute(ORACLE, "CREATE TABLE " + ORACLE.schema() + "." + SQLSERVER_TO_ORACLE_EXIST_TARGET
            + " (ID NUMBER(19) PRIMARY KEY, USER_NAME VARCHAR2(50), AMOUNT NUMBER(10,2))");
        runJob(job(SQLSERVER, SQLSERVER_TO_ORACLE_SOURCE, ORACLE, SQLSERVER_TO_ORACLE_EXIST_TARGET));
        verifyOracle(SQLSERVER_TO_ORACLE_EXIST_TARGET);
    }

    @Test
    @Timeout(240000)
    public void mysqlToSqlServerAutoCreateAndExistingTable() throws Exception {
        prepareMysqlSource();
        dropQuietly(SQLSERVER, "dbo." + MYSQL_TO_SQLSERVER_AUTO_TARGET);
        dropQuietly(SQLSERVER, "dbo." + MYSQL_TO_SQLSERVER_EXIST_TARGET);

        runJob(job(MYSQL, MYSQL_TO_SQLSERVER_SOURCE, SQLSERVER, MYSQL_TO_SQLSERVER_AUTO_TARGET));
        verifySqlServer(MYSQL_TO_SQLSERVER_AUTO_TARGET);

        // 已存在表字段大写，源 MySQL 字段小写
        execute(SQLSERVER, "CREATE TABLE dbo." + MYSQL_TO_SQLSERVER_EXIST_TARGET
            + " ([ID] BIGINT PRIMARY KEY, [USER_NAME] VARCHAR(50), [AMOUNT] DECIMAL(10,2))");
        runJob(job(MYSQL, MYSQL_TO_SQLSERVER_SOURCE, SQLSERVER, MYSQL_TO_SQLSERVER_EXIST_TARGET));
        verifySqlServer(MYSQL_TO_SQLSERVER_EXIST_TARGET);
    }

    @Test
    @Timeout(240000)
    public void oracleToSqlServerAutoCreateAndExistingTable() throws Exception {
        prepareOracleSource();
        dropQuietly(SQLSERVER, "dbo." + ORACLE_TO_SQLSERVER_AUTO_TARGET);
        dropQuietly(SQLSERVER, "dbo." + ORACLE_TO_SQLSERVER_EXIST_TARGET);

        runJob(job(ORACLE, ORACLE_TO_SQLSERVER_SOURCE, SQLSERVER, ORACLE_TO_SQLSERVER_AUTO_TARGET));
        verifySqlServer(ORACLE_TO_SQLSERVER_AUTO_TARGET);

        // 已存在表字段小写，源 Oracle 字段大写
        execute(SQLSERVER, "CREATE TABLE dbo." + ORACLE_TO_SQLSERVER_EXIST_TARGET
            + " (id BIGINT PRIMARY KEY, user_name VARCHAR(50), amount DECIMAL(10,2))");
        runJob(job(ORACLE, ORACLE_TO_SQLSERVER_SOURCE, SQLSERVER, ORACLE_TO_SQLSERVER_EXIST_TARGET));
        verifySqlServer(ORACLE_TO_SQLSERVER_EXIST_TARGET);
    }

    private void prepareSqlServerSource(String table, boolean uppercaseColumns) throws SQLException {
        String id = uppercaseColumns ? "[ID]" : "[id]";
        String userName = uppercaseColumns ? "[USER_NAME]" : "[user_name]";
        String amount = uppercaseColumns ? "[AMOUNT]" : "[amount]";
        execute(SQLSERVER, "IF OBJECT_ID('dbo." + table + "','U') IS NOT NULL DROP TABLE dbo." + table);
        execute(SQLSERVER, "CREATE TABLE dbo." + table + " (" + id + " BIGINT PRIMARY KEY, " + userName
            + " VARCHAR(50), " + amount + " DECIMAL(10,2))");
        execute(SQLSERVER, "INSERT INTO dbo." + table + " (" + id + ", " + userName + ", " + amount + ") VALUES "
            + "(1, 'alice', 12.34), (2, 'bob', 56.78)");
    }

    private void prepareMysqlSource() throws SQLException {
        execute(MYSQL, "DROP TABLE IF EXISTS test." + MYSQL_TO_SQLSERVER_SOURCE);
        execute(MYSQL, "CREATE TABLE test." + MYSQL_TO_SQLSERVER_SOURCE
            + " (id BIGINT PRIMARY KEY, user_name VARCHAR(50), amount DECIMAL(10,2))");
        execute(MYSQL, "INSERT INTO test." + MYSQL_TO_SQLSERVER_SOURCE
            + " (id, user_name, amount) VALUES (1, 'alice', 12.34), (2, 'bob', 56.78)");
    }

    private void prepareOracleSource() throws SQLException {
        dropQuietly(ORACLE, ORACLE.schema() + "." + ORACLE_TO_SQLSERVER_SOURCE);
        execute(ORACLE, "CREATE TABLE " + ORACLE.schema() + "." + ORACLE_TO_SQLSERVER_SOURCE
            + " (ID NUMBER(19) PRIMARY KEY, USER_NAME VARCHAR2(50), AMOUNT NUMBER(10,2))");
        execute(ORACLE, "INSERT INTO " + ORACLE.schema() + "." + ORACLE_TO_SQLSERVER_SOURCE
            + " (ID, USER_NAME, AMOUNT) VALUES (1, 'alice', 12.34)");
        execute(ORACLE, "INSERT INTO " + ORACLE.schema() + "." + ORACLE_TO_SQLSERVER_SOURCE
            + " (ID, USER_NAME, AMOUNT) VALUES (2, 'bob', 56.78)");
    }

    private void verifyPostgres(String configuredTable) throws SQLException {
        String table = lower(configuredTable);
        try (
            Connection conn = connect(PG);
            Statement statement = conn.createStatement();
            ResultSet rs = statement.executeQuery("SELECT * FROM public." + table + " ORDER BY 1")
        ) {
            verifyRows(rs, "PostgreSQL " + table);
        }
    }

    private void verifyPostgresLowercaseIdentifiers(String configuredTable) throws SQLException {
        String table = lower(configuredTable);
        try (Connection conn = connect(PG);
             Statement statement = conn.createStatement()) {
            try (ResultSet rs = statement.executeQuery(
                "select count(*) from information_schema.tables where table_schema='public' and table_name='" + table + "'"
            )) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "PostgreSQL 自动建表应使用小写表名: " + table);
            }
            try (ResultSet rs = statement.executeQuery(
                "select column_name from information_schema.columns where table_schema='public' and table_name='"
                    + table
                    + "' order by ordinal_position"
            )) {
                assertTrue(rs.next());
                int count = 0;
                do {
                    String columnName = rs.getString(1);
                    assertEquals(lower(columnName), columnName, "PostgreSQL 自动建表字段名应为小写: " + columnName);
                    count++;
                } while (rs.next());
                assertEquals(3, count);
            }
        }
    }

    private void verifyOracle(String table) throws SQLException {
        try (
            Connection conn = connect(ORACLE);
            Statement statement = conn.createStatement();
            ResultSet rs = statement.executeQuery("SELECT * FROM " + ORACLE.schema() + "." + table + " ORDER BY 1")
        ) {
            verifyRows(rs, "Oracle " + table);
        }
    }

    private void verifySqlServer(String table) throws SQLException {
        try (
            Connection conn = connect(SQLSERVER);
            Statement statement = conn.createStatement();
            ResultSet rs = statement.executeQuery("SELECT * FROM dbo." + table + " ORDER BY 1")
        ) {
            verifyRows(rs, "SQL Server " + table);
        }
    }

    private void verifyRows(ResultSet rs, String label) throws SQLException {
        assertTrue(rs.next(), label + " 应有数据");
        assertEquals(1L, rs.getLong(1));
        assertEquals("alice", rs.getString(2));
        assertEquals(0, new BigDecimal("12.34").compareTo(rs.getBigDecimal(3)));
        assertTrue(rs.next(), label + " 应有第二行数据");
        assertEquals(2L, rs.getLong(1));
        assertEquals("bob", rs.getString(2));
        assertEquals(0, new BigDecimal("56.78").compareTo(rs.getBigDecimal(3)));
        assertFalse(rs.next(), label + " 应只有两行数据");
    }

    private void runJob(String job) throws Exception {
        new ETLFlowTask().runJob(job);
    }

    private String job(Db source, String sourceTable, Db target, String targetTable) {
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
            source.type(),
            source.url(),
            source.driver(),
            source.user(),
            source.password(),
            source.schema(),
            source.schema(),
            sourceTable,
            target.type(),
            target.url(),
            target.driver(),
            target.user(),
            target.password(),
            target.schema(),
            target.schema(),
            targetTable
        );
    }

    private boolean canConnect(Db db) {
        try (Connection ignored = connect(db)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Connection connect(Db db) throws SQLException {
        DatasourceInfo info = new DatasourceInfo();
        info.setType(db.type());
        info.setUrl(db.url());
        info.setDriver(db.driver());
        info.setUsername(db.user());
        info.setPassword(db.password());
        info.setDbschema(db.schema());
        return DBUtils.getConnection(info);
    }

    private void execute(Db db, String sql) throws SQLException {
        try (Connection conn = connect(db);
             Statement statement = conn.createStatement()) {
            statement.execute(sql);
        }
    }

    private void dropQuietly(Db db, String qualifiedTable) {
        try {
            execute(db, "DROP TABLE " + qualifiedTable);
        } catch (Exception ignored) {
            // 表可能不存在
        }
    }

    private static String lower(String value) {
        return value.toLowerCase(java.util.Locale.ROOT);
    }

    private record Db(String type, String url, String driver, String user, String password, String schema) {}
}
