package com.data.job.component;

import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 达梦 CDC（DBMS_LOGMNR）实时同步到 Apache Doris 的端到端测试。
 *
 * <p>覆盖快照、INSERT、UPDATE、DELETE、NULL 行以及达梦常用字段类型写入 Doris 的
 * UNIQUE KEY 模型表。Doris 侧使用 MySQL 协议，故通过 JDBC {@code StreamJdbcOutput}
 * 写入，主键表由 {@code DorisConverter} 自动建表为 UNIQUE KEY 模型。
 *
 * <p>前置条件：
 * <ul>
 *   <li>本地达梦已开启归档与逻辑日志（可用 {@code src/test/docker/dm} 启动）；</li>
 *   <li>本地 Doris FE 可访问（默认 {@code 127.0.0.1:9030}，root 无密码）。</li>
 * </ul>
 * 任一环境不可用时测试自动跳过。
 */
public class DmCdcToDorisTest {

    private static final String DM_URL = System.getProperty("dm.url", "jdbc:dm://127.0.0.1:5236");
    private static final String DM_USER = System.getProperty("dm.user", "DATAY");
    private static final String DM_PASSWORD = System.getProperty("dm.password", "Datay123456");
    private static final String DM_SCHEMA = System.getProperty("dm.schema", "DATAY");

    private static final String DORIS_HOST = System.getProperty("doris.host", "127.0.0.1");
    private static final String DORIS_PORT = System.getProperty("doris.port", "9030");
    private static final String DORIS_USER = System.getProperty("doris.user", "root");
    private static final String DORIS_PASSWORD = System.getProperty("doris.password", "");
    private static final String DORIS_DB = System.getProperty("doris.database", "datay_dm_cdc");
    private static final String DORIS_DRIVER = "com.mysql.cj.jdbc.Driver";

    private static final String DORIS_QUERY =
        "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8";
    private static final String DORIS_ADMIN_URL =
        "jdbc:mysql://" + DORIS_HOST + ":" + DORIS_PORT + "/" + DORIS_QUERY;
    private static final String DORIS_URL =
        "jdbc:mysql://" + DORIS_HOST + ":" + DORIS_PORT + "/" + DORIS_DB + DORIS_QUERY;

    private static final String TABLE = "CDC_DM_DORIS";
    private static final String DORIS_TABLE = "cdc_dm_doris";

    @Test
    public void testDmCdcToDoris() throws Exception {
        assumeTrue(supportsLogMiner(), "本地达梦未开启归档/逻辑日志或账号权限不足，跳过测试");
        assumeTrue(dorisReachable(), "目标 Doris 不可用，跳过测试");
        resetEnvironment();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        ETLFlowTask runner = new ETLFlowTask();
        String job = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get(System.getProperty("user.dir"), "src/test/dmCdcToDoris.json")));

        Future<?> jobFuture = executor.submit(() -> {
            try {
                runner.runJob(job);
            } catch (Throwable e) {
                jobError.set(e);
            }
        });

        try {
            // 快照：启动前已存在的基线行
            awaitTrue(() -> dorisTableExists() && queryDorisCount() == 1, 120000, "等待达梦 CDC 快照创建 Doris 目标表超时");
            assertEquals("baseline", queryDorisString("select C_VARCHAR from " + DORIS_TABLE + " where ID = 1"));

            // INSERT：覆盖常用字段类型
            executeDm(
                "INSERT INTO " +
                DM_SCHEMA +
                "." +
                TABLE +
                " (ID, C_TINYINT, C_SMALLINT, C_INT, C_BIGINT, C_DECIMAL, C_NUMBER, C_FLOAT, C_DOUBLE, " +
                "C_CHAR, C_VARCHAR, C_VARCHAR2, C_NVARCHAR2, C_DATE, C_TIME, C_DATETIME, C_TIMESTAMP, C_BIT) VALUES (" +
                "2, -5, 1234, 123456, 9000000000, 12345.6789, 98765.4321, 1.5, 2.25, " +
                "'abc', 'hello世界', 'varchar2', '中文', DATE '2024-01-01', TIME '12:34:56', " +
                "TIMESTAMP '2024-01-01 10:00:00', TIMESTAMP '2024-01-01 10:00:00', 1)"
            );
            awaitTrue(() -> queryDorisCount() == 2, 90000, "等待达梦 INSERT 同步到 Doris 超时");
            assertDorisValues(2, "-5", "hello世界");
            assertEquals("1234", queryDorisString("select C_SMALLINT from " + DORIS_TABLE + " where ID = 2"));
            assertEquals("123456", queryDorisString("select C_INT from " + DORIS_TABLE + " where ID = 2"));
            assertEquals("9000000000", queryDorisString("select C_BIGINT from " + DORIS_TABLE + " where ID = 2"));
            assertEquals("12345.6789", queryDorisString("select C_DECIMAL from " + DORIS_TABLE + " where ID = 2"));
            assertEquals("98765.4321", queryDorisString("select C_NUMBER from " + DORIS_TABLE + " where ID = 2"));
            assertEquals(1.5d, Double.parseDouble(queryDorisString("select C_FLOAT from " + DORIS_TABLE + " where ID = 2")), 0.0001d);
            assertEquals(2.25d, Double.parseDouble(queryDorisString("select C_DOUBLE from " + DORIS_TABLE + " where ID = 2")), 0.0001d);
            assertEquals("abc", queryDorisString("select trim(C_CHAR) from " + DORIS_TABLE + " where ID = 2"));
            assertEquals("varchar2", queryDorisString("select C_VARCHAR2 from " + DORIS_TABLE + " where ID = 2"));
            assertEquals("中文", queryDorisString("select C_NVARCHAR2 from " + DORIS_TABLE + " where ID = 2"));
            assertEquals("2024-01-01", queryDorisString("select cast(C_DATE as char) from " + DORIS_TABLE + " where ID = 2"));
            assertEquals("12:34:56", queryDorisString("select C_TIME from " + DORIS_TABLE + " where ID = 2"));
            assertEquals(
                "2024-01-01 10:00:00",
                queryDorisString("select date_format(C_DATETIME, '%Y-%m-%d %H:%i:%s') from " + DORIS_TABLE + " where ID = 2")
            );
            assertEquals(
                "2024-01-01 10:00:00",
                queryDorisString("select date_format(C_TIMESTAMP, '%Y-%m-%d %H:%i:%s') from " + DORIS_TABLE + " where ID = 2")
            );
            assertEquals("1", normalizeBit(queryDorisString("select C_BIT from " + DORIS_TABLE + " where ID = 2")));

            // UPDATE：数值、字符、日期时间、BIT
            executeDm(
                "UPDATE " +
                DM_SCHEMA +
                "." +
                TABLE +
                " SET C_TINYINT = -6, C_SMALLINT = 2345, C_INT = 654321, C_BIGINT = 8000000000, " +
                "C_DECIMAL = 99999.1234, C_NUMBER = 11111.2222, C_FLOAT = 2.25, C_DOUBLE = 3.5, " +
                "C_CHAR = 'xyz', C_VARCHAR = 'updated值', C_VARCHAR2 = 'v2', C_NVARCHAR2 = '更新', " +
                "C_DATE = DATE '2025-01-02', C_TIME = TIME '01:02:03', " +
                "C_DATETIME = TIMESTAMP '2025-01-02 01:02:03', C_TIMESTAMP = TIMESTAMP '2025-01-02 01:02:03', " +
                "C_BIT = 0 WHERE ID = 2"
            );
            awaitTrue(
                () -> "updated值".equals(queryDorisString("select C_VARCHAR from " + DORIS_TABLE + " where ID = 2")),
                90000,
                "等待达梦 UPDATE 同步到 Doris 超时"
            );
            assertEquals("-6", queryDorisString("select C_TINYINT from " + DORIS_TABLE + " where ID = 2"));
            assertEquals("2345", queryDorisString("select C_SMALLINT from " + DORIS_TABLE + " where ID = 2"));
            assertEquals("654321", queryDorisString("select C_INT from " + DORIS_TABLE + " where ID = 2"));
            assertEquals("8000000000", queryDorisString("select C_BIGINT from " + DORIS_TABLE + " where ID = 2"));
            assertEquals("99999.1234", queryDorisString("select C_DECIMAL from " + DORIS_TABLE + " where ID = 2"));
            assertEquals("11111.2222", queryDorisString("select C_NUMBER from " + DORIS_TABLE + " where ID = 2"));
            assertEquals(2.25d, Double.parseDouble(queryDorisString("select C_FLOAT from " + DORIS_TABLE + " where ID = 2")), 0.0001d);
            assertEquals(3.5d, Double.parseDouble(queryDorisString("select C_DOUBLE from " + DORIS_TABLE + " where ID = 2")), 0.0001d);
            assertEquals("xyz", queryDorisString("select trim(C_CHAR) from " + DORIS_TABLE + " where ID = 2"));
            assertEquals("v2", queryDorisString("select C_VARCHAR2 from " + DORIS_TABLE + " where ID = 2"));
            assertEquals("更新", queryDorisString("select C_NVARCHAR2 from " + DORIS_TABLE + " where ID = 2"));
            assertEquals("2025-01-02", queryDorisString("select cast(C_DATE as char) from " + DORIS_TABLE + " where ID = 2"));
            assertEquals("01:02:03", queryDorisString("select C_TIME from " + DORIS_TABLE + " where ID = 2"));
            assertEquals(
                "2025-01-02 01:02:03",
                queryDorisString("select date_format(C_DATETIME, '%Y-%m-%d %H:%i:%s') from " + DORIS_TABLE + " where ID = 2")
            );
            assertEquals("0", normalizeBit(queryDorisString("select C_BIT from " + DORIS_TABLE + " where ID = 2")));

            // NULL 行
            executeDm("INSERT INTO " + DM_SCHEMA + "." + TABLE + " (ID, C_VARCHAR) VALUES (3, 'only-string')");
            awaitTrue(() -> queryDorisCount() == 3, 90000, "等待达梦 NULL 行同步到 Doris 超时");
            assertNull(queryDorisString("select C_DECIMAL from " + DORIS_TABLE + " where ID = 3"));
            assertNull(queryDorisString("select C_DATE from " + DORIS_TABLE + " where ID = 3"));
            assertEquals("only-string", queryDorisString("select C_VARCHAR from " + DORIS_TABLE + " where ID = 3"));

            // UPDATE 主键：after 镜像落到新主键，旧主键必须被删除，避免 Doris 残留旧行
            executeDm("UPDATE " + DM_SCHEMA + "." + TABLE + " SET ID = 4, C_VARCHAR = 'pk-moved' WHERE ID = 3");
            awaitTrue(
                () -> queryDorisCount() == 3 && "pk-moved".equals(queryDorisString("select C_VARCHAR from " + DORIS_TABLE + " where ID = 4")),
                90000,
                "等待达梦主键 UPDATE 同步到 Doris 超时"
            );
            assertNull(queryDorisString("select C_VARCHAR from " + DORIS_TABLE + " where ID = 3"));

            // DELETE
            executeDm("DELETE FROM " + DM_SCHEMA + "." + TABLE + " WHERE ID = 1");
            awaitTrue(() -> queryDorisCount() == 2, 90000, "等待达梦 DELETE 同步到 Doris 超时");

            // 目标表应为 UNIQUE KEY 模型，才能用 INSERT 完成 UPSERT/DELETE
            String createSql = showCreateDorisTable();
            assertTrue(createSql.toUpperCase().contains("UNIQUE KEY"), "Doris 目标表应为 UNIQUE KEY 模型:\n" + createSql);
            // Doris 的 MySQL 协议不返回主键元数据，Provider 应从 UNIQUE KEY 中解析出主键，
            // 否则任务重启后（目标表已存在）DELETE / 主键变更会退化为按全部字段匹配。
            com.data.metadata.TableMeta dorisMeta;
            try (Connection conn = dorisConnection()) {
                dorisMeta = DBUtils.getTableMetaData(dorisInfo(DORIS_URL, DORIS_DB), conn, DORIS_DB, DORIS_TABLE);
            }
            assertTrue(
                dorisMeta.columns().stream().anyMatch(c -> "id".equalsIgnoreCase(c.getName()) && c.isPrimaryKey()),
                "Doris 表元数据应识别出 id 为主键"
            );
            printDorisTypes();
        } finally {
            runner.cancel();
            try {
                jobFuture.get(15, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
            executor.shutdownNow();
        }

        Throwable error = jobError.get();
        if (error != null && !isCancellation(error)) {
            throw new AssertionError("达梦 CDC 到 Doris 同步任务异常结束: " + error.getMessage(), error);
        }
    }

    private void assertDorisValues(int id, String tinyint, String varchar) {
        assertEquals(tinyint, queryDorisString("select C_TINYINT from " + DORIS_TABLE + " where ID = " + id));
        assertEquals(varchar, queryDorisString("select C_VARCHAR from " + DORIS_TABLE + " where ID = " + id));
    }

    private String normalizeBit(String value) {
        if (value == null) {
            return null;
        }
        String v = value.trim();
        if ("true".equalsIgnoreCase(v) || "1".equals(v)) {
            return "1";
        }
        if ("false".equalsIgnoreCase(v) || "0".equals(v)) {
            return "0";
        }
        return v;
    }

    private String showCreateDorisTable() throws Exception {
        try (
            Connection conn = dorisConnection();
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SHOW CREATE TABLE " + DORIS_DB + "." + DORIS_TABLE)
        ) {
            return rs.next() ? rs.getString(2) : "";
        }
    }

    private void printDorisTypes() throws Exception {
        try (
            Connection conn = dorisConnection();
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(
                "select COLUMN_NAME, DATA_TYPE from information_schema.columns " +
                "where TABLE_SCHEMA = '" + DORIS_DB + "' and TABLE_NAME = '" + DORIS_TABLE + "' order by ORDINAL_POSITION"
            )
        ) {
            StringBuilder types = new StringBuilder();
            while (rs.next()) {
                if (types.length() > 0) {
                    types.append(", ");
                }
                types.append(rs.getString(1)).append("=").append(rs.getString(2));
            }
            System.out.println("[DM-DORIS-TYPES] " + types);
        }
    }

    private boolean supportsLogMiner() {
        try (Connection conn = dmConnection()) {
            String archMode;
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT ARCH_MODE FROM V$DATABASE")) {
                if (!rs.next()) {
                    return false;
                }
                archMode = rs.getString(1);
            }
            if (!("Y".equalsIgnoreCase(archMode) || "1".equals(archMode))) {
                return false;
            }
            if (paraInt(conn, "RLOG_APPEND_LOGIC") < 2) {
                return false;
            }
            if (paraInt(conn, "RLOG_IGNORE_TABLE_SET") != 1) {
                return false;
            }
            if (paraInt(conn, "LOGMNR_DELAY_ANALYZE") == 1) {
                return false;
            }
            try (Statement st = conn.createStatement()) {
                st.execute("ALTER SYSTEM ARCHIVE LOG CURRENT");
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private int paraInt(Connection conn, String name) throws Exception {
        try (
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(
                "SELECT PARA_VALUE FROM V$DM_INI WHERE PARA_NAME = '" + name.replace("'", "''") + "'"
            )
        ) {
            if (!rs.next()) {
                return -1;
            }
            String value = rs.getString(1);
            if (value == null) {
                return -1;
            }
            String trimmed = value.trim();
            int space = trimmed.indexOf(' ');
            if (space > 0) {
                trimmed = trimmed.substring(0, space);
            }
            return Integer.parseInt(trimmed);
        }
    }

    private boolean dorisReachable() {
        try (Connection conn = dorisAdminConnection()) {
            return conn != null && !conn.isClosed();
        } catch (Exception e) {
            return false;
        }
    }

    private void resetEnvironment() throws Exception {
        closeDorisPools();
        File statusFile = new File("./log/etl_status.json");
        if (statusFile.exists()) {
            statusFile.delete();
        }

        try (Connection conn = dorisAdminConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS " + DORIS_DB);
        }
        try (Connection conn = dorisConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS " + DORIS_DB + "." + DORIS_TABLE);
        }

        try (Connection conn = dmConnection(); Statement st = conn.createStatement()) {
            try {
                st.execute("DROP TABLE " + DM_SCHEMA + "." + TABLE);
            } catch (Exception ignored) {
            }
            st.execute(
                "CREATE TABLE " +
                DM_SCHEMA +
                "." +
                TABLE +
                " (" +
                "ID INT PRIMARY KEY, " +
                "C_TINYINT TINYINT, " +
                "C_SMALLINT SMALLINT, " +
                "C_INT INT, " +
                "C_BIGINT BIGINT, " +
                "C_DECIMAL DECIMAL(12,4), " +
                "C_NUMBER NUMBER(12,4), " +
                "C_FLOAT FLOAT, " +
                "C_DOUBLE DOUBLE, " +
                "C_CHAR CHAR(10), " +
                "C_VARCHAR VARCHAR(100), " +
                "C_VARCHAR2 VARCHAR2(100), " +
                "C_NVARCHAR2 NVARCHAR2(50), " +
                "C_DATE DATE, " +
                "C_TIME TIME, " +
                "C_DATETIME DATETIME, " +
                "C_TIMESTAMP TIMESTAMP, " +
                "C_BIT BIT)"
            );
            st.execute("INSERT INTO " + DM_SCHEMA + "." + TABLE + " (ID, C_TINYINT, C_VARCHAR) VALUES (1, 1, 'baseline')");
        }
    }

    private void executeDm(String sql) throws Exception {
        try (Connection conn = dmConnection(); Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private boolean dorisTableExists() {
        try (Connection conn = dorisConnection(); Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                "select count(*) from information_schema.tables where TABLE_SCHEMA = '" +
                DORIS_DB +
                "' and TABLE_NAME = '" +
                DORIS_TABLE +
                "'"
            )) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private int queryDorisCount() {
        Integer count = queryDorisInt("select count(*) from " + DORIS_TABLE);
        return count == null ? -1 : count;
    }

    private Integer queryDorisInt(String sql) {
        try (Connection conn = dorisConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String queryDorisString(String sql) {
        try (Connection conn = dorisConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Connection dorisAdminConnection() throws Exception {
        return DBUtils.getConnection(dorisInfo(DORIS_ADMIN_URL, null));
    }

    private Connection dorisConnection() throws Exception {
        return DBUtils.getConnection(dorisInfo(DORIS_URL, DORIS_DB));
    }

    private DatasourceInfo dorisInfo(String url, String schema) {
        DatasourceInfo doris = new DatasourceInfo();
        doris.setType("doris");
        doris.setUrl(url);
        doris.setDriver(DORIS_DRIVER);
        doris.setUsername(DORIS_USER);
        doris.setPassword(DORIS_PASSWORD);
        doris.setDbschema(schema);
        return doris;
    }

    private Connection dmConnection() throws Exception {
        DatasourceInfo dm = new DatasourceInfo();
        dm.setType("dm");
        dm.setUrl(DM_URL);
        dm.setUsername(DM_USER);
        dm.setPassword(DM_PASSWORD);
        dm.setDbschema(DM_SCHEMA);
        return DBUtils.getConnection(dm);
    }

    private void closeDorisPools() {
        try {
            DBUtils.closePool(DORIS_URL, DORIS_USER);
        } catch (Exception ignored) {
        }
        try {
            DBUtils.closePool(DORIS_ADMIN_URL, DORIS_USER);
        } catch (Exception ignored) {
        }
    }

    private boolean isCancellation(Throwable throwable) {
        while (throwable != null) {
            if (throwable instanceof InterruptedException) {
                return true;
            }
            String message = throwable.getMessage();
            if (message != null && message.contains("InterruptedException")) {
                return true;
            }
            throwable = throwable.getCause();
        }
        return false;
    }

    private void awaitTrue(BooleanSupplier condition, long timeoutMillis, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(500);
        }
        assertTrue(false, message);
    }
}
