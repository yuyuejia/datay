package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * SQL Server → Doris 同步场景验证。
 *
 * <p>复现并回归「SQL Server 默认约束 {@code ((0))} 被原样带入 Doris 建表语句导致
 * {@code Syntax error ... Encountered: ( Expected}」的问题。</p>
 *
 * <p>覆盖两级验证：</p>
 * <ol>
 *   <li>真实 SQL Server 元数据 → Doris DDL：确认默认值被忽略、类型/长度正确；</li>
 *   <li>StreamJdbcInput → StreamJdbcOutput 全链路：Doris 自动建表并写入数据。</li>
 * </ol>
 *
 * <p>连接信息可通过系统属性覆盖（默认连本机）：</p>
 * <pre>
 * -Ddatay.test.sqlserver.url / user / password
 * -Ddatay.test.doris.url / user / password / schema
 * </pre>
 * 任一环境不可用时对应测试自动跳过。
 */
public class SqlServerToDorisSyncTest {

    private static final String SQLSERVER_ADMIN_URL = System.getProperty(
        "datay.test.sqlserver.adminUrl",
        "jdbc:sqlserver://127.0.0.1:1433;databaseName=master;encrypt=false;trustServerCertificate=true"
    );
    private static final String SQLSERVER_URL = System.getProperty(
        "datay.test.sqlserver.url",
        "jdbc:sqlserver://127.0.0.1:1433;databaseName=datay_verify;encrypt=false;trustServerCertificate=true"
    );
    private static final String SQLSERVER_USER = System.getProperty("datay.test.sqlserver.user", "sa");
    private static final String SQLSERVER_PASSWORD = System.getProperty("datay.test.sqlserver.password", "Datay@12345");
    private static final String SQLSERVER_SCHEMA = System.getProperty("datay.test.sqlserver.schema", "dbo");

    private static final String DORIS_ADMIN_URL = System.getProperty(
        "datay.test.doris.adminUrl",
        "jdbc:mysql://127.0.0.1:9030/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
    );
    private static final String DORIS_URL = System.getProperty(
        "datay.test.doris.url",
        "jdbc:mysql://127.0.0.1:9030/datay_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
    );
    private static final String DORIS_USER = System.getProperty("datay.test.doris.user", "root");
    private static final String DORIS_PASSWORD = System.getProperty("datay.test.doris.password", "");
    private static final String DORIS_SCHEMA = System.getProperty("datay.test.doris.schema", "datay_test");
    private static final String DORIS_DRIVER = "com.mysql.cj.jdbc.Driver";

    private static final String SOURCE_TABLE = "datay_discount_src";
    private static final String TARGET_TABLE = "datay_discount_dst";

    /** 运行期按需把仓库根目录的 SQL Server 驱动复制到当前工作目录（Maven 单模块运行时的 cwd）。 */
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
        } catch (IOException e) {
            return false;
        }
    }

    @AfterAll
    public static void closePools() throws Exception {
        DBUtils.closePool(SQLSERVER_ADMIN_URL, SQLSERVER_USER);
        DBUtils.closePool(SQLSERVER_URL, SQLSERVER_USER);
        DBUtils.closePool(DORIS_ADMIN_URL, DORIS_USER);
        DBUtils.closePool(DORIS_URL, DORIS_USER);
        if (localDriverDirCreated) {
            try (DirectoryStream<Path> jars = Files.newDirectoryStream(LOCAL_DRIVER_DIR, "*.jar")) {
                for (Path jar : jars) {
                    Files.deleteIfExists(jar);
                }
            }
            Files.deleteIfExists(LOCAL_DRIVER_DIR);
            Files.deleteIfExists(LOCAL_DRIVER_DIR.getParent());
            Files.deleteIfExists(LOCAL_DRIVER_DIR.getParent().getParent());
        }
    }

    /**
     * 验证真实 SQL Server 元数据里的默认约束不会进入 Doris DDL。
     *
     * <p>该用例只需要 SQL Server，即使没有 Doris 也能验证本次缺陷的根因。</p>
     */
    @Test
    @Timeout(120000)
    public void testSqlServerDefaultsAreIgnoredInDorisDdl() throws Exception {
        assumeTrue(canConnect(sqlServerAdminInfo()), "SQL Server 不可用，跳过");
        prepareSqlServerTable();

        TableMeta source;
        try (Connection conn = DBUtils.getConnection(sqlServerUrlInfo())) {
            source = DBUtils.getTableMetaData(conn, SQLSERVER_SCHEMA, SOURCE_TABLE);
        }

        // 先确认确实复现了源端默认约束形态：SQL Server 的 COLUMN_DEF 为 ((0))
        ColumnMeta nameColumn = findColumn(source, "ddiscountname");
        assertNotNull(nameColumn, "未读取到 ddiscountname 列");
        assertEquals("((0))", nameColumn.getDefaultValue(), "SQL Server 默认约束应返回 ((0))");

        TableMeta target = DatabaseConverter.convert(source.getDbType(), "doris", source);
        String ddl = DatabaseConverter.generateTableDDL("doris", target);

        assertFalse(ddl.toUpperCase(Locale.ROOT).contains("DEFAULT"), "Doris 建表语句不应包含 DEFAULT:\n" + ddl);
        assertFalse(ddl.contains("(("), "Doris 建表语句不应包含 SQL Server 默认约束括号:\n" + ddl);
        assertTrue(ddl.contains("`ddiscountname` VARCHAR(50) NOT NULL"), ddl);
        assertTrue(ddl.contains("`amount` DECIMAL(18,2) NOT NULL"), ddl);
        assertTrue(ddl.contains("`remark` VARCHAR(100)"), ddl);
        assertTrue(ddl.contains("UNIQUE KEY (`id`)"), ddl);
    }

    /**
     * 验证 StreamJdbcInput(SQL Server) → StreamJdbcOutput(Doris) 自动建表 + 数据写入全链路。
     */
    @Test
    @Timeout(300000)
    public void testSqlServerToDorisSync() throws Exception {
        assumeTrue(canConnect(sqlServerAdminInfo()), "SQL Server 不可用，跳过");
        assumeTrue(canConnect(dorisAdminInfo()), "Doris 不可用，跳过");

        prepareSqlServerTable();
        prepareDorisSchema();
        dropDorisTable();

        // 先确认源端读取正常，避免把驱动解码问题误判为同步问题
        try (Connection conn = DBUtils.getConnection(sqlServerUrlInfo())) {
            assertEquals("第一条", queryString(conn,
                "SELECT remark FROM " + SQLSERVER_SCHEMA + "." + SOURCE_TABLE + " WHERE id = 1"));
            assertEquals("含,逗号与'引号'", queryString(conn,
                "SELECT remark FROM " + SQLSERVER_SCHEMA + "." + SOURCE_TABLE + " WHERE id = 3"));
        }

        String job = buildJob();
        new ETLFlowTask().runJob(job);

        try (Connection conn = DBUtils.getConnection(dorisUrlInfo())) {
            assertEquals(3, queryInt(conn, "SELECT COUNT(*) FROM " + DORIS_SCHEMA + "." + TARGET_TABLE));

            Map<Long, String[]> rows = new LinkedHashMap<>();
            try (
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                    "SELECT id, ddiscountname, amount, remark, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') " +
                    "FROM " + DORIS_SCHEMA + "." + TARGET_TABLE + " ORDER BY id"
                )
            ) {
                while (rs.next()) {
                    rows.put(
                        rs.getLong(1),
                        new String[] {
                            rs.getString(2),
                            rs.getBigDecimal(3) == null ? null : rs.getBigDecimal(3).toPlainString(),
                            rs.getString(4),
                            rs.getString(5),
                        }
                    );
                }
            }

            assertEquals(3, rows.size());
            assertArrayEquals(
                new String[] { "A001", "100.50", "第一条", "2024-03-15 12:34:56" },
                rows.get(1L),
                "id=1 数据不一致"
            );
            // SQL Server 的默认值 ((0)) 只是建表约束，显式写入的空串/0 应原样同步
            assertArrayEquals(new String[] { "", "0.00", null, null }, rows.get(2L), "id=2 数据不一致");
            assertArrayEquals(
                new String[] { "CN-003", "88.88", "含,逗号与'引号'", "2024-01-01 00:00:00" },
                rows.get(3L),
                "id=3 数据不一致"
            );

            // Doris 建表时不应带任何默认值（只看列定义部分，表属性里可能含 default 字样）
            String createSql = showCreateTable(conn, DORIS_SCHEMA + "." + TARGET_TABLE);
            String columnSection = createSql.contains("ENGINE=OLAP")
                ? createSql.substring(0, createSql.indexOf("ENGINE=OLAP"))
                : createSql;
            assertFalse(
                columnSection.toUpperCase(Locale.ROOT).contains("DEFAULT"),
                "Doris 表列定义不应包含 DEFAULT:\n" + createSql
            );
            assertFalse(columnSection.contains("(("), "Doris 表列定义不应包含 SQL Server 默认约束括号:\n" + createSql);

            // information_schema 中所有列默认值应为空
            try (
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                    "SELECT COLUMN_NAME, COLUMN_DEFAULT FROM information_schema.columns " +
                    "WHERE TABLE_SCHEMA = '" + DORIS_SCHEMA + "' AND TABLE_NAME = '" + TARGET_TABLE + "'"
                )
            ) {
                int columnCount = 0;
                while (rs.next()) {
                    columnCount++;
                    String defaultValue = rs.getString(2);
                    assertTrue(
                        defaultValue == null || defaultValue.isEmpty(),
                        "列 " + rs.getString(1) + " 不应有默认值，实际: " + defaultValue
                    );
                }
                assertEquals(5, columnCount, "Doris 目标表列数不正确");
            }
        }
    }

    /**
     * 验证 Doris 数据源连接（Provider 属性）写入中文不会乱码。
     */
    @Test
    @Timeout(120000)
    public void testDorisConnectionWritesChineseVerbatim() throws Exception {
        assumeTrue(canConnect(dorisAdminInfo()), "Doris 不可用，跳过");
        prepareDorisSchema();

        String table = DORIS_SCHEMA + ".datay_encoding_probe";
        try (Connection conn = DBUtils.getConnection(dorisUrlInfo()); Statement st = conn.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS " + table);
            st.executeUpdate(
                "CREATE TABLE " + table + " (id INT, v VARCHAR(100)) DISTRIBUTED BY HASH(id) BUCKETS 1 " +
                "PROPERTIES (\"replication_num\" = \"1\")"
            );
            try (java.sql.PreparedStatement ps = conn.prepareStatement("INSERT INTO " + table + " VALUES (?, ?)")) {
                ps.setInt(1, 1);
                ps.setString(2, "第一条");
                ps.executeUpdate();

                ps.setInt(1, 2);
                ps.setString(2, "含,逗号与'引号'");
                ps.executeUpdate();
            }
            assertEquals("第一条", queryString(conn, "SELECT v FROM " + table + " WHERE id = 1"));
            assertEquals("含,逗号与'引号'", queryString(conn, "SELECT v FROM " + table + " WHERE id = 2"));
        }
    }

    // ------------------------------------------------------------------ 环境准备

    private static DatasourceInfo sqlServerAdminInfo() {
        return datasource("sqlserver", SQLSERVER_ADMIN_URL, SQLSERVER_USER, SQLSERVER_PASSWORD, SQLSERVER_SCHEMA);
    }

    private static DatasourceInfo sqlServerUrlInfo() {
        return datasource("sqlserver", SQLSERVER_URL, SQLSERVER_USER, SQLSERVER_PASSWORD, SQLSERVER_SCHEMA);
    }

    private static DatasourceInfo dorisAdminInfo() {
        return datasource("doris", DORIS_ADMIN_URL, DORIS_USER, DORIS_PASSWORD, DORIS_SCHEMA);
    }

    private static DatasourceInfo dorisUrlInfo() {
        return datasource("doris", DORIS_URL, DORIS_USER, DORIS_PASSWORD, DORIS_SCHEMA);
    }

    private static DatasourceInfo datasource(String type, String url, String user, String password, String schema) {
        DatasourceInfo info = new DatasourceInfo();
        info.setType(type);
        info.setUrl(url);
        info.setDriver("sqlserver".equals(type) ? "com.microsoft.sqlserver.jdbc.SQLServerDriver" : DORIS_DRIVER);
        info.setUsername(user);
        info.setPassword(password);
        info.setDbschema(schema);
        return info;
    }

    private void prepareSqlServerTable() throws Exception {
        try (Connection conn = DBUtils.getConnection(sqlServerAdminInfo()); Statement st = conn.createStatement()) {
            st.executeUpdate(
                "IF DB_ID('datay_verify') IS NULL CREATE DATABASE datay_verify"
            );
        }
        try (Connection conn = DBUtils.getConnection(sqlServerUrlInfo()); Statement st = conn.createStatement()) {
            st.executeUpdate(
                "IF OBJECT_ID('" + SQLSERVER_SCHEMA + "." + SOURCE_TABLE + "', 'U') IS NOT NULL " +
                "DROP TABLE " + SQLSERVER_SCHEMA + "." + SOURCE_TABLE
            );
            // 关键：完全复现问题表的默认约束形态，VARCHAR 列上的 DEFAULT ((0))
            st.executeUpdate(
                "CREATE TABLE " + SQLSERVER_SCHEMA + "." + SOURCE_TABLE + " (\n" +
                "  id BIGINT NOT NULL PRIMARY KEY,\n" +
                "  ddiscountname VARCHAR(50) NOT NULL CONSTRAINT DF_discount_name DEFAULT ((0)),\n" +
                "  amount DECIMAL(18,2) NOT NULL CONSTRAINT DF_discount_amount DEFAULT ((0)),\n" +
                "  remark NVARCHAR(100) NULL CONSTRAINT DF_discount_remark DEFAULT (N''),\n" +
                "  created_at DATETIME NULL CONSTRAINT DF_discount_created DEFAULT (getdate())\n" +
                ")"
            );
            st.executeUpdate(
                "INSERT INTO " + SQLSERVER_SCHEMA + "." + SOURCE_TABLE +
                " (id, ddiscountname, amount, remark, created_at) VALUES " +
                "(1, 'A001', 100.50, N'第一条', '2024-03-15T12:34:56'), " +
                "(2, '', 0, NULL, NULL), " +
                "(3, 'CN-003', 88.88, N'含,逗号与''引号''', '2024-01-01T00:00:00')"
            );
        }
    }

    private void prepareDorisSchema() throws Exception {
        try (Connection conn = DBUtils.getConnection(dorisAdminInfo()); Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS " + DORIS_SCHEMA);
        }
    }

    private void dropDorisTable() throws Exception {
        try (Connection conn = DBUtils.getConnection(dorisUrlInfo()); Statement st = conn.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS " + DORIS_SCHEMA + "." + TARGET_TABLE);
        }
    }

    // ------------------------------------------------------------------ 任务与查询辅助

    private static String buildJob() {
        return "{\n" +
            "  \"units\": [\n" +
            "    {\".id\":\"src\",\".name\":\"StreamJdbcInput\"," +
            "     \"sourceId\":{\"type\":\"sqlserver\",\"url\":\"" + escapeJson(SQLSERVER_URL) + "\"," +
            "       \"driver\":\"com.microsoft.sqlserver.jdbc.SQLServerDriver\"," +
            "       \"username\":\"" + escapeJson(SQLSERVER_USER) + "\",\"password\":\"" + escapeJson(SQLSERVER_PASSWORD) + "\"," +
            "       \"dbschema\":\"" + escapeJson(SQLSERVER_SCHEMA) + "\"}," +
            "     \"schema\":\"" + escapeJson(SQLSERVER_SCHEMA) + "\",\"table\":\"" + SOURCE_TABLE + "\"},\n" +
            "    {\".id\":\"sink\",\".name\":\"StreamJdbcOutput\"," +
            "     \"sourceId\":{\"type\":\"doris\",\"url\":\"" + escapeJson(DORIS_URL) + "\"," +
            "       \"driver\":\"" + DORIS_DRIVER + "\"," +
            "       \"username\":\"" + escapeJson(DORIS_USER) + "\",\"password\":\"" + escapeJson(DORIS_PASSWORD) + "\"," +
            "       \"dbschema\":\"" + escapeJson(DORIS_SCHEMA) + "\"}," +
            "     \"schema\":\"" + escapeJson(DORIS_SCHEMA) + "\",\"table\":\"" + TARGET_TABLE + "\"," +
            "     \"model\":\"overwrite\",\"dropIfTableExists\":true}\n" +
            "  ],\n" +
            "  \"connections\": [{\"sourceId\":\"src\",\"targetId\":\"sink\",\"sourcePort\":0}]\n" +
            "}";
    }

    private static boolean canConnect(DatasourceInfo info) {
        try (Connection conn = DBUtils.getConnection(info)) {
            return conn != null && !conn.isClosed();
        } catch (Throwable e) {
            return false;
        }
    }

    private static ColumnMeta findColumn(TableMeta table, String name) {
        for (ColumnMeta column : table.columns()) {
            if (name.equalsIgnoreCase(column.getName())) {
                return column;
            }
        }
        return null;
    }

    private static int queryInt(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }

    private static String queryString(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static String showCreateTable(Connection conn, String qualifiedTable) throws Exception {
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SHOW CREATE TABLE " + qualifiedTable)) {
            return rs.next() ? rs.getString(2) : "";
        }
    }

    private static String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void assertArrayEquals(String[] expected, String[] actual, String message) {
        org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual, message);
    }
}
