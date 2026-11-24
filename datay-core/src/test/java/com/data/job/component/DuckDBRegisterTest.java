package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.data.job.DatasourceInfo;
import com.data.job.DuckDBEngine;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link DuckDBRegister} 回归测试：覆盖 DuckDB 文件、MySQL、PostgreSQL、SQLite 的挂载，
 * 以及 DuckDB 不支持的数据库类型。
 *
 * <p>MySQL / PostgreSQL 用例在本地实例不可用时自动跳过。
 */
class DuckDBRegisterTest {

    private static final String MYSQL_URL =
        "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai";
    private static final String MYSQL_USER = System.getProperty("mysql.username", "root");
    private static final String MYSQL_PASSWORD = System.getProperty("mysql.password", "1234qwer");
    private static final String PG_URL = "jdbc:postgresql://127.0.0.1:5432/testdb";
    private static final String PG_USER = System.getProperty("pg.username", "admin");
    private static final String PG_PASSWORD = System.getProperty("pg.password", "123456");

    private String jobCode;
    private final List<Path> tempFiles = new ArrayList<>();
    private final List<Path> tempDirs = new ArrayList<>();

    @AfterEach
    void tearDown() {
        if (jobCode != null) {
            DuckDBEngine.close(jobCode);
            deleteQuietly(new File(DuckDBEngine.getDBFile(jobCode)));
            deleteQuietly(new File(DuckDBEngine.getDBFile(jobCode) + ".wal"));
        }
        for (Path path : tempFiles) {
            deleteQuietly(path.toFile());
            deleteQuietly(new File(path + ".wal"));
        }
        for (Path dir : tempDirs) {
            deleteRecursively(dir);
        }
    }

    @Test
    void attachesDuckDbFileWithoutInstallingDuckdbExtension() throws Exception {
        Path source = Files.createTempFile("register-source-", ".duckdb");
        Files.delete(source);
        tempFiles.add(source);
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + source);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE t(id INTEGER)");
            stmt.execute("INSERT INTO t VALUES (1), (2), (3)");
        }

        jobCode = uniqueCode("duckdb-register-duckdb");
        DuckDBRegister register = newRegister(
            jobCode, "jdbc:duckdb:" + source, null, null, null, "src_duck");
        register.execute((FlowFile) null);

        assertEquals(3, queryLong(jobCode, "SELECT count(*) FROM src_duck.t"));
    }

    @Test
    void attachesMySqlWithSecret() throws Exception {
        assumeTrue(canConnect(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD), "本地 MySQL 不可用，跳过");
        String table = "reg_mysql_" + uniqueSuffix();
        executeJdbc(
            MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD,
            "CREATE TABLE test." + table + " (id INT)",
            "INSERT INTO test." + table + " VALUES (1), (2)");
        try {
            jobCode = uniqueCode("duckdb-register-mysql");
            DuckDBRegister register = newRegister(
                jobCode, MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD, "test", "src_mysql");
            register.execute((FlowFile) null);

            assertEquals(2, queryLong(jobCode, "SELECT count(*) FROM src_mysql.test." + table));
        } finally {
            executeJdbc(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD, "DROP TABLE IF EXISTS test." + table);
        }
    }

    @Test
    void attachesPostgresWithDbname() throws Exception {
        assumeTrue(canConnect(PG_URL, PG_USER, PG_PASSWORD), "本地 PostgreSQL 不可用，跳过");
        String table = "reg_pg_" + uniqueSuffix();
        executeJdbc(
            PG_URL, PG_USER, PG_PASSWORD,
            "CREATE TABLE public." + table + " (id INTEGER)",
            "INSERT INTO public." + table + " VALUES (1), (2), (3), (4)");
        try {
            jobCode = uniqueCode("duckdb-register-pg");
            DuckDBRegister register = newRegister(
                jobCode, PG_URL, PG_USER, PG_PASSWORD, "testdb", "src_pg");
            register.execute((FlowFile) null);

            assertEquals(4, queryLong(jobCode, "SELECT count(*) FROM src_pg.public." + table));
        } finally {
            executeJdbc(PG_URL, PG_USER, PG_PASSWORD, "DROP TABLE IF EXISTS public." + table);
        }
    }

    @Test
    void attachesSqliteFile() throws Exception {
        Path sqlite = Files.createTempFile("register-sqlite-", ".db");
        Files.deleteIfExists(sqlite);
        tempFiles.add(sqlite);
        assumeTrue(createSqliteFixture(sqlite), "DuckDB sqlite 扩展不可用，跳过");

        jobCode = uniqueCode("duckdb-register-sqlite");
        DuckDBRegister register = newRegister(
            jobCode, "jdbc:sqlite:" + sqlite, null, null, null, "src_sqlite");
        register.execute((FlowFile) null);

        assertEquals(2, queryLong(jobCode, "SELECT count(*) FROM src_sqlite.t"));
    }

    @Test
    void attachesDuckLakeMetadataEvenIfPathContainsDuckdb() throws Exception {
        Path base = Files.createTempDirectory("register-duckdb-ducklake-");
        tempDirs.add(base);
        Path meta = base.resolve("meta.ducklake");
        Path data = base.resolve("data");
        assumeTrue(createDuckLakeFixture(meta, data), "DuckLake 扩展不可用，跳过");

        jobCode = uniqueCode("duckdb-register-ducklake");
        DuckDBRegister register = newRegister(
            jobCode, "ducklake:" + meta, null, null, null, "src_lake", Map.of("s3.data_path", data.toString()));
        register.execute((FlowFile) null);

        assertEquals(2, queryLong(jobCode, "SELECT count(*) FROM src_lake.main.orders"));
    }

    @Test
    void rejectsTypesWithoutDuckDbExtension() {
        jobCode = uniqueCode("duckdb-register-unsupported");

        DuckDBRegister sqlServer = newRegister(
            jobCode, "jdbc:sqlserver://127.0.0.1:1433;databaseName=test", null, null, null, "src_sqlserver");
        RuntimeException sqlServerError = assertThrows(RuntimeException.class, () -> sqlServer.execute((FlowFile) null));
        assertTrue(sqlServerError.getMessage().contains("sqlserver"), sqlServerError.getMessage());
        assertTrue(sqlServerError.getMessage().contains("odbc"), sqlServerError.getMessage());

        DuckDBRegister oracle = newRegister(
            jobCode, "jdbc:oracle:thin:@127.0.0.1:1521:orcl", null, null, null, "src_oracle");
        RuntimeException oracleError = assertThrows(RuntimeException.class, () -> oracle.execute((FlowFile) null));
        assertTrue(oracleError.getMessage().contains("oracle"), oracleError.getMessage());
        assertTrue(oracleError.getMessage().contains("odbc"), oracleError.getMessage());
    }

    private DuckDBRegister newRegister(
        String code, String url, String username, String password, String dbschema, String alias) {
        return newRegister(code, url, username, password, dbschema, alias, null);
    }

    private DuckDBRegister newRegister(
        String code, String url, String username, String password, String dbschema, String alias,
        Map<String, String> extraParams) {
        ExecutionContext context = new ExecutionContext();
        context.setJobInstanceCode(code);

        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setUrl(url);
        datasource.setUsername(username);
        datasource.setPassword(password);
        datasource.setDbschema(dbschema);
        datasource.setExtraParams(extraParams);

        DuckDBRegister register = new DuckDBRegister();
        register.setContext(context);
        register.setTaskLogger(new TaskLogger("duckdb-register-test", code));
        register.setDatasource(datasource);
        register.setAlias(alias);
        return register;
    }

    private static long queryLong(String code, String sql) throws SQLException {
        try (Connection conn = DuckDBEngine.getInstance().getConnection(code);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            assertTrue(rs.next(), "查询没有返回结果: " + sql);
            return rs.getLong(1);
        }
    }

    private static boolean createSqliteFixture(Path sqlite) {
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:");
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSTALL sqlite");
            stmt.execute("LOAD sqlite");
            stmt.execute("ATTACH '" + sqlite.toAbsolutePath() + "' AS fixture (TYPE sqlite)");
            stmt.execute("CREATE TABLE fixture.t(id INTEGER)");
            stmt.execute("INSERT INTO fixture.t VALUES (1), (2)");
            stmt.execute("DETACH fixture");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean createDuckLakeFixture(Path meta, Path data) {
        Path creator = meta.resolveSibling("creator.duckdb");
        try (Connection conn = DriverManager.getConnection("jdbc:duckdb:" + creator);
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSTALL ducklake");
            stmt.execute("LOAD ducklake");
            stmt.execute("ATTACH 'ducklake:" + meta + "' AS fixture (data_path '" + data + "')");
            stmt.execute("CREATE TABLE fixture.main.orders(id INTEGER)");
            stmt.execute("INSERT INTO fixture.main.orders VALUES (1), (2)");
            stmt.execute("DETACH fixture");
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean canConnect(String url, String username, String password) {
        try (Connection ignored = DriverManager.getConnection(url, username, password)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void executeJdbc(String url, String username, String password, String... sqls) throws SQLException {
        try (Connection conn = DriverManager.getConnection(url, username, password);
             Statement stmt = conn.createStatement()) {
            for (String sql : sqls) {
                stmt.execute(sql);
            }
        }
    }

    private static String uniqueCode(String prefix) {
        return prefix + "-" + uniqueSuffix();
    }

    private static String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static void deleteQuietly(File file) {
        if (file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static void deleteRecursively(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException ignored) {
                    // 测试清理失败不影响断言
                }
            });
        } catch (IOException ignored) {
            // 测试清理失败不影响断言
        }
    }
}
