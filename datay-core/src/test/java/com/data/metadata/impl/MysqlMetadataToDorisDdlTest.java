package com.data.metadata.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.data.job.DatasourceInfo;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 用真实 MySQL 元数据验证 MySQL → Doris 的建表 DDL。
 *
 * <p>覆盖 JDBC 元数据到 DDL 的完整链路：{@code DBUtils.getTableMetaData} 读取真实
 * {@code TYPE_NAME}/{@code COLUMN_SIZE}/{@code PRIMARY KEY}，再经
 * {@code DatabaseConverter} 转换为 Doris 方言并生成建表语句，最后校验 Doris 的
 * 「KEY 列必须是有序前缀」等约束。</p>
 *
 * <p>连接信息可通过系统属性覆盖，默认连本地 MySQL 容器（127.0.0.1:3307）：</p>
 * <pre>
 * -Ddatay.test.mysql.url / user / password / schema
 * </pre>
 * MySQL 不可用时测试自动跳过。
 */
class MysqlMetadataToDorisDdlTest {

    private static final String ADMIN_URL = System.getProperty(
        "datay.test.mysql.adminUrl",
        "jdbc:mysql://127.0.0.1:3307/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
    );
    private static final String URL = System.getProperty(
        "datay.test.mysql.url",
        "jdbc:mysql://127.0.0.1:3307/datay_m2d_verify?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
    );
    private static final String USER = System.getProperty("datay.test.mysql.user", "root");
    private static final String PASSWORD = System.getProperty("datay.test.mysql.password", "password");
    private static final String SCHEMA = System.getProperty("datay.test.mysql.schema", "datay_m2d_verify");

    private static final String DORIS_ADMIN_URL = System.getProperty(
        "datay.test.doris.adminUrl",
        "jdbc:mysql://127.0.0.1:9030/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
    );
    private static final String DORIS_URL = System.getProperty(
        "datay.test.doris.url",
        "jdbc:mysql://127.0.0.1:9030/datay_m2d_doris?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8"
    );
    private static final String DORIS_USER = System.getProperty("datay.test.doris.user", "root");
    private static final String DORIS_PASSWORD = System.getProperty("datay.test.doris.password", "");
    private static final String DORIS_SCHEMA = System.getProperty("datay.test.doris.schema", "datay_m2d_doris");

    private static final Path DDL_DUMP = Path.of("target", "mysql-to-doris-ddl.txt");

    @BeforeAll
    static void prepareMysql() throws Exception {
        assumeTrue(canConnect(adminInfo()), "MySQL 不可用，跳过真实元数据验证");

        try (Connection conn = DBUtils.getConnection(adminInfo()); Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS " + SCHEMA + " DEFAULT CHARACTER SET utf8mb4");
        }
        try (Connection conn = DBUtils.getConnection(urlInfo()); Statement st = conn.createStatement()) {
            createScenarioTables(st);
        }
    }

    @AfterAll
    static void closePools() {
        DBUtils.closePool(ADMIN_URL, USER);
        DBUtils.closePool(URL, USER);
        DBUtils.closePool(DORIS_ADMIN_URL, DORIS_USER);
        DBUtils.closePool(DORIS_URL, DORIS_USER);
    }

    @Test
    @Timeout(120000)
    void realMysqlMetadataGeneratesValidDorisDdl() throws Exception {
        Map<String, String> ddls = new LinkedHashMap<>();
        for (String table : scenarioTables()) {
            TableMeta source = readMysqlTable(table);
            String ddl = MysqlToDorisDdlTest.toDorisDdl(source, SCHEMA);
            MysqlToDorisDdlTest.assertValidDorisDdl(ddl);
            ddls.put(table, ddl);
        }

        Files.createDirectories(DDL_DUMP.getParent());
        StringBuilder dump = new StringBuilder();
        ddls.forEach((table, ddl) -> dump.append("-- ").append(table).append('\n').append(ddl).append("\n\n"));
        Files.writeString(DDL_DUMP, dump.toString(), StandardCharsets.UTF_8);

        assertEquals(scenarioTables().size(), ddls.size());
    }

    @Test
    @Timeout(120000)
    void realScatteredPrimaryKeyIsReordered() throws Exception {
        TableMeta source = readMysqlTable("t_scattered_pk");
        String ddl = MysqlToDorisDdlTest.toDorisDdl(source, SCHEMA);

        assertEquals(List.of("id", "stype", "tenant_id", "didtype", "amount"), MysqlToDorisDdlTest.columnNames(ddl));
        assertEquals(List.of("id", "stype", "tenant_id"), MysqlToDorisDdlTest.uniqueKeys(ddl));
        assertTrue(ddl.indexOf("`stype`") < ddl.indexOf("`didtype`"), ddl);
    }

    @Test
    @Timeout(120000)
    void realUnsignedTypesAreWidened() throws Exception {
        TableMeta source = readMysqlTable("t_unsigned");

        // 先确认 Connector/J 确实返回带 UNSIGNED 的 TYPE_NAME
        assertTrue(findColumn(source, "id").getType().toUpperCase(Locale.ROOT).contains("UNSIGNED"),
            "MySQL 元数据中的无符号类型应带 UNSIGNED 后缀: " + findColumn(source, "id").getType());

        String ddl = MysqlToDorisDdlTest.toDorisDdl(source, SCHEMA);
        assertTrue(ddl.contains("`id` LARGEINT"), ddl);
        assertTrue(ddl.contains("`c_tiny` SMALLINT"), ddl);
        assertTrue(ddl.contains("`c_small` INT"), ddl);
        assertTrue(ddl.contains("`c_medium` INT"), ddl);
        assertTrue(ddl.contains("`c_int` BIGINT"), ddl);
        assertTrue(ddl.contains("`c_big` LARGEINT"), ddl);
    }

    @Test
    @Timeout(120000)
    void realMysqlTypesAreMapped() throws Exception {
        TableMeta source = readMysqlTable("t_types");
        String ddl = MysqlToDorisDdlTest.toDorisDdl(source, SCHEMA);

        assertTrue(ddl.contains("`c_decimal` DECIMAL(18,4)"), ddl);
        assertTrue(ddl.contains("`c_text` STRING"), ddl);
        assertTrue(ddl.contains("`c_json` JSON"), ddl);
        assertTrue(ddl.contains("`c_enum` VARCHAR"), ddl);
        assertTrue(ddl.contains("`c_bit` VARCHAR"), ddl);
        assertTrue(ddl.contains("`c_time` VARCHAR"), ddl);
        assertFalse(ddl.toUpperCase(Locale.ROOT).contains(" DEFAULT "), ddl);
    }

    @Test
    @Timeout(120000)
    void realWideDecimalIsClamped() throws Exception {
        TableMeta source = readMysqlTable("t_decimal_wide");
        String ddl = MysqlToDorisDdlTest.toDorisDdl(source, SCHEMA);

        assertTrue(ddl.contains("`c_dec` DECIMAL(38,30)"), ddl);
    }

    /**
     * 把生成的所有 DDL 真正交给 Doris 执行，验证语法与 KEY 前缀约束。
     *
     * <p>没有可用 Doris 时自动跳过；设置 {@code -Ddatay.test.doris.url} 等属性即可启用。</p>
     */
    @Test
    @Timeout(240000)
    void generatedDdlIsAcceptedByRealDoris() throws Exception {
        assumeTrue(canConnect(dorisAdminInfo()), "Doris 不可用，跳过真实建表验证");

        try (Connection conn = DBUtils.getConnection(dorisAdminInfo())) {
            execute(conn, "CREATE DATABASE IF NOT EXISTS " + DORIS_SCHEMA);
        }

        try (Connection conn = DBUtils.getConnection(dorisUrlInfo())) {
            for (String table : scenarioTables()) {
                execute(conn, "DROP TABLE IF EXISTS " + DORIS_SCHEMA + "." + table);
            }
            try {
                for (String table : scenarioTables()) {
                    TableMeta source = readMysqlTable(table);
                    execute(conn, MysqlToDorisDdlTest.toDorisDdl(source, DORIS_SCHEMA));
                }

                for (String table : scenarioTables()) {
                    String createTable = showCreateTable(conn, DORIS_SCHEMA + "." + table);
                    assertTrue(createTable.contains("CREATE TABLE"), "Doris 中未找到表 " + table + ":\n" + createTable);
                }

                String createTable = showCreateTable(conn, DORIS_SCHEMA + ".t_scattered_pk");
                assertTrue(createTable.indexOf("`stype`") < createTable.indexOf("`didtype`"), createTable);
                assertTrue(createTable.contains("UNIQUE KEY(`id`, `stype`, `tenant_id`)"), createTable);
            } finally {
                for (String table : scenarioTables()) {
                    try {
                        execute(conn, "DROP TABLE IF EXISTS " + DORIS_SCHEMA + "." + table);
                    } catch (Exception ignored) {
                        // 清理失败不影响用例结果
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 表结构与元数据读取

    private static List<String> scenarioTables() {
        return List.of(
            "t_pk_first",
            "t_scattered_pk",
            "t_no_pk",
            "t_all_pk",
            "t_upper_case",
            "t_comment",
            "t_types",
            "t_unsigned",
            "t_decimal_wide"
        );
    }

    private static void createScenarioTables(Statement st) throws Exception {
        for (String table : scenarioTables()) {
            st.executeUpdate("DROP TABLE IF EXISTS " + table);
        }

        st.executeUpdate(
            "CREATE TABLE t_pk_first (" +
            " id BIGINT NOT NULL," +
            " name VARCHAR(64) NOT NULL COMMENT '名称'," +
            " amount DECIMAL(12,2)," +
            " created_at DATETIME," +
            " remark TEXT," +
            " PRIMARY KEY (id)" +
            ")"
        );
        // 复现线上报错：主键 sType / tenant_id 分布在普通列 didtype 之后
        st.executeUpdate(
            "CREATE TABLE t_scattered_pk (" +
            " id BIGINT NOT NULL," +
            " didtype VARCHAR(50)," +
            " sType VARCHAR(50) NOT NULL," +
            " tenant_id BIGINT NOT NULL," +
            " amount DECIMAL(10,2)," +
            " PRIMARY KEY (tenant_id, sType, id)" +
            ")"
        );
        st.executeUpdate(
            "CREATE TABLE t_no_pk (" +
            " id BIGINT NOT NULL," +
            " name VARCHAR(64)," +
            " amount DECIMAL(10,2)" +
            ")"
        );
        st.executeUpdate(
            "CREATE TABLE t_all_pk (" +
            " a BIGINT NOT NULL," +
            " b VARCHAR(20) NOT NULL," +
            " c DATE NOT NULL," +
            " PRIMARY KEY (a, b, c)" +
            ")"
        );
        st.executeUpdate(
            "CREATE TABLE t_upper_case (" +
            " ID BIGINT NOT NULL," +
            " dIdType VARCHAR(50)," +
            " SType VARCHAR(50)," +
            " PRIMARY KEY (ID, SType)" +
            ")"
        );
        st.executeUpdate(
            "CREATE TABLE t_comment (" +
            " id BIGINT NOT NULL," +
            " name VARCHAR(50) COMMENT 'it''s name, with comma'," +
            " PRIMARY KEY (id)" +
            ")"
        );
        st.executeUpdate(
            "CREATE TABLE t_types (" +
            " id BIGINT NOT NULL," +
            " c_tinyint TINYINT," +
            " c_smallint SMALLINT," +
            " c_mediumint MEDIUMINT," +
            " c_int INT," +
            " c_bigint BIGINT," +
            " c_decimal DECIMAL(18,4)," +
            " c_float FLOAT," +
            " c_double DOUBLE," +
            " c_char CHAR(10)," +
            " c_varchar VARCHAR(100)," +
            " c_tinytext TINYTEXT," +
            " c_text TEXT," +
            " c_mediumtext MEDIUMTEXT," +
            " c_longtext LONGTEXT," +
            " c_blob BLOB," +
            " c_date DATE," +
            " c_datetime DATETIME," +
            " c_datetime3 DATETIME(3)," +
            " c_timestamp TIMESTAMP NULL," +
            " c_time TIME," +
            " c_year YEAR," +
            " c_json JSON," +
            " c_enum ENUM('a','b')," +
            " c_set SET('x','y')," +
            " c_bit BIT(1)," +
            " c_binary BINARY(8)," +
            " c_varbinary VARBINARY(32)," +
            " PRIMARY KEY (id)" +
            ")"
        );
        st.executeUpdate(
            "CREATE TABLE t_unsigned (" +
            " id BIGINT UNSIGNED NOT NULL," +
            " c_tiny TINYINT UNSIGNED," +
            " c_small SMALLINT UNSIGNED," +
            " c_medium MEDIUMINT UNSIGNED," +
            " c_int INT UNSIGNED," +
            " c_big BIGINT UNSIGNED," +
            " PRIMARY KEY (id)" +
            ")"
        );
        st.executeUpdate(
            "CREATE TABLE t_decimal_wide (" +
            " id BIGINT NOT NULL," +
            " c_dec DECIMAL(65,30)," +
            " PRIMARY KEY (id)" +
            ")"
        );
    }

    private static TableMeta readMysqlTable(String table) throws Exception {
        try (Connection conn = DBUtils.getConnection(urlInfo())) {
            TableMeta tableMeta = DBUtils.getTableMetaData(conn, SCHEMA, table);
            tableMeta.setDbType("mysql");
            return tableMeta;
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

    private static boolean canConnect(DatasourceInfo info) {
        try (Connection conn = DBUtils.getConnection(info)) {
            return conn != null && !conn.isClosed();
        } catch (Throwable e) {
            return false;
        }
    }

    private static DatasourceInfo adminInfo() {
        return datasource(ADMIN_URL, "");
    }

    private static DatasourceInfo urlInfo() {
        return datasource(URL, SCHEMA);
    }

    private static DatasourceInfo dorisAdminInfo() {
        return dorisDatasource(DORIS_ADMIN_URL, "");
    }

    private static DatasourceInfo dorisUrlInfo() {
        return dorisDatasource(DORIS_URL, DORIS_SCHEMA);
    }

    private static DatasourceInfo dorisDatasource(String url, String schema) {
        DatasourceInfo info = new DatasourceInfo();
        info.setType("doris");
        info.setUrl(url);
        info.setDriver("com.mysql.cj.jdbc.Driver");
        info.setUsername(DORIS_USER);
        info.setPassword(DORIS_PASSWORD);
        info.setDbschema(schema);
        return info;
    }

    private static void execute(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private static String showCreateTable(Connection conn, String qualifiedTable) throws Exception {
        try (Statement st = conn.createStatement(); java.sql.ResultSet rs = st.executeQuery("SHOW CREATE TABLE " + qualifiedTable)) {
            return rs.next() ? rs.getString(2) : "";
        }
    }

    private static DatasourceInfo datasource(String url, String schema) {
        DatasourceInfo info = new DatasourceInfo();
        info.setType("mysql");
        info.setUrl(url);
        info.setDriver("com.mysql.cj.jdbc.Driver");
        info.setUsername(USER);
        info.setPassword(PASSWORD);
        info.setDbschema(schema);
        return info;
    }
}
