package com.data.job.component;

import com.data.datasource.DataSourceConnection;
import com.data.datasource.DataSourceProvider;
import com.data.datasource.DataSourceProviderFactory;
import com.data.job.DatasourceInfo;
import com.data.job.DuckDBEngine;
import com.data.job.ETLFlowTask;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 验证 Oracle 数据源在非 SYS 账号下的元数据读取与 ETL 同步能力。
 *
 * <p>默认连接本地 {@code datay/datay123}（schema {@code DATAY}，仅具备普通 RESOURCE/CONNECT
 * 权限），可用系统属性覆盖：
 * <pre>
 *   -Doracle.url= -Doracle.user= -Doracle.password= -Doracle.schema=
 * </pre>
 */
public class OracleNonSysUserVerificationTest {

    private static final String ORACLE_URL = System.getProperty(
        "oracle.url",
        "jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1"
    );
    private static final String ORACLE_USER = System.getProperty("oracle.user", "datay");
    private static final String ORACLE_PASSWORD = System.getProperty("oracle.password", "datay123");
    private static final String SCHEMA = System.getProperty("oracle.schema", "DATAY");

    private static final String SOURCE_TABLE = "NS_VERIFY_SRC";
    private static final String TARGET_TABLE = "NS_VERIFY_DST";
    private static final String DUCK_FILE = "oracle-nonsys-verify.db";
    private static final String DUCK_TABLE = "NS_VERIFY_DUCK";

    private static DatasourceInfo oracleDatasource() {
        DatasourceInfo info = new DatasourceInfo();
        info.setType("oracle");
        info.setUrl(ORACLE_URL);
        info.setDriver("oracle.jdbc.OracleDriver");
        info.setUsername(ORACLE_USER);
        info.setPassword(ORACLE_PASSWORD);
        info.setDbschema(SCHEMA);
        return info;
    }

    private static Connection oracleConnection() throws Exception {
        Class.forName("oracle.jdbc.OracleDriver");
        return DriverManager.getConnection(ORACLE_URL, ORACLE_USER, ORACLE_PASSWORD);
    }

    private static boolean canConnect() {
        try (Connection conn = oracleConnection()) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @BeforeAll
    public static void prepareSourceTable() throws Exception {
        assumeTrue(canConnect(), "本地 Oracle 不可用或非 SYS 测试账号无权限，跳过元数据/同步验证");
        try (Connection conn = oracleConnection(); Statement stmt = conn.createStatement()) {
            try {
                stmt.execute("DROP TABLE " + SCHEMA + "." + SOURCE_TABLE);
            } catch (Exception ignored) {
                // 首次运行不存在
            }
            try {
                stmt.execute("DROP TABLE " + SCHEMA + "." + TARGET_TABLE);
            } catch (Exception ignored) {
                // 首次运行不存在
            }
            stmt.execute(
                "CREATE TABLE " +
                SCHEMA +
                "." +
                SOURCE_TABLE +
                " (ID NUMBER(10) PRIMARY KEY, NAME VARCHAR2(100) NOT NULL, AMOUNT NUMBER(18,4), CREATED_AT TIMESTAMP)"
            );
            stmt.execute("COMMENT ON TABLE " + SCHEMA + "." + SOURCE_TABLE + " IS 'non-sys metadata test'");
            stmt.execute("COMMENT ON COLUMN " + SCHEMA + "." + SOURCE_TABLE + ".NAME IS 'name column'");
            stmt.execute("CREATE INDEX IDX_NS_VERIFY_NAME ON " + SCHEMA + "." + SOURCE_TABLE + "(NAME)");
            stmt.execute(
                "INSERT INTO " +
                SCHEMA +
                "." +
                SOURCE_TABLE +
                " (ID, NAME, AMOUNT, CREATED_AT) VALUES (1, 'alpha', 12.3456, TIMESTAMP '2024-01-01 10:00:00')"
            );
            stmt.execute(
                "INSERT INTO " +
                SCHEMA +
                "." +
                SOURCE_TABLE +
                " (ID, NAME, AMOUNT, CREATED_AT) VALUES (2, 'beta', 99.0000, TIMESTAMP '2024-01-02 11:30:00')"
            );
        }
    }

    @Test
    @Timeout(120000)
    public void testMetadataAsNonSysUser() throws Exception {
        DataSourceProvider provider = DataSourceProviderFactory.provider("oracle");
        assertNotNull(provider, "Oracle Provider 未注册");

        try (DataSourceConnection connection = provider.connect(oracleDatasource())) {
            assertTrue(connection.testConnection(), "非 SYS 账号连通性测试失败");

            List<String> schemas = connection.getSchemas();
            assertTrue(schemas.stream().anyMatch(s -> s.equalsIgnoreCase(SCHEMA)), "schema 列表缺少 " + SCHEMA);

            List<TableMeta> tables = connection.getTables(SCHEMA, null, SOURCE_TABLE);
            assertTrue(
                tables.stream().anyMatch(t -> SOURCE_TABLE.equalsIgnoreCase(t.getTable())),
                "表清单缺少 " + SOURCE_TABLE
            );

            TableMeta meta = connection.getTableMeta(SCHEMA, SOURCE_TABLE);
            assertEquals(4, meta.columns().size(), "字段数量不正确");
            assertTrue(meta.columns().stream().anyMatch(c -> "ID".equalsIgnoreCase(c.getName()) && c.isPrimaryKey()), "主键未识别");
            assertTrue(meta.columns().stream().anyMatch(c -> "NAME".equalsIgnoreCase(c.getName()) && !c.isNullable()), "NOT NULL 未识别");
            assertTrue(meta.columns().stream().anyMatch(c -> "AMOUNT".equalsIgnoreCase(c.getName())), "AMOUNT 字段缺失");

            TableMeta detail = connection.getTableDetail(SCHEMA, SOURCE_TABLE);
            assertEquals("non-sys metadata test", detail.getComment(), "表注释未读取到");
            assertTrue(
                detail.columns().stream().anyMatch(c -> "NAME".equalsIgnoreCase(c.getName()) && "name column".equals(c.getComment())),
                "字段注释未读取到"
            );
            assertTrue(
                detail.getIndexes() != null && detail.getIndexes().stream().anyMatch(i -> "IDX_NS_VERIFY_NAME".equalsIgnoreCase(i.getName())),
                "索引未读取到"
            );

        }

        try (Connection conn = oracleConnection()) {
            assertTrue(DBUtils.tableExists(conn, SCHEMA, SOURCE_TABLE), "表存在性检查失败");
        }
    }

    /**
     * 验证 ETL 实际使用的 DBUtils 元数据路径（不经过 DataSourceConnection）。
     */
    @Test
    @Timeout(120000)
    public void testDbutilsMetadataPathAsNonSysUser() throws Exception {
        // 走 ConnectionPoolManager，与应用内 ETL 实际获取连接的路径一致
        try (Connection conn = DBUtils.getConnection(oracleDatasource())) {
            TableMeta meta = DBUtils.getTableMetaData(conn, SCHEMA, SOURCE_TABLE);
            assertEquals(4, meta.columns().size(), "DBUtils 元数据字段数量不正确");

            TableMeta detail = DBUtils.getTableDetail(conn, SCHEMA, SOURCE_TABLE);
            assertTrue(detail.getIndexes() != null && !detail.getIndexes().isEmpty(), "DBUtils 未读取到索引");
            assertEquals("non-sys metadata test", detail.getComment(), "DBUtils 未读取到表注释");
        }
    }

    /**
     * Oracle（非 SYS）作为 ETL 源，全量同步到 DuckDB。
     */
    @Test
    @Timeout(180000)
    public void testEtlSyncFromOracleAsNonSysUser() throws Exception {
        DuckDBEngine.deleteDBFile(DUCK_FILE);
        String job =
            "{\n" +
            "  \"units\": [\n" +
            "    {\n" +
            "      \".id\": \"StreamJdbcInput\",\n" +
            "      \".name\": \"StreamJdbcInput\",\n" +
            "      \"sourceId\": {\n" +
            "        \"type\": \"oracle\",\n" +
            "        \"url\": \"" +
            ORACLE_URL +
            "\",\n" +
            "        \"driver\": \"oracle.jdbc.OracleDriver\",\n" +
            "        \"username\": \"" +
            ORACLE_USER +
            "\",\n" +
            "        \"password\": \"" +
            ORACLE_PASSWORD +
            "\",\n" +
            "        \"dbschema\": \"" +
            SCHEMA +
            "\"\n" +
            "      },\n" +
            "      \"schema\": \"" +
            SCHEMA +
            "\",\n" +
            "      \"table\": \"" +
            SOURCE_TABLE +
            "\"\n" +
            "    },\n" +
            "    {\n" +
            "      \".id\": \"DuckDBWrite\",\n" +
            "      \".name\": \"DuckDBWrite\",\n" +
            "      \"table\": \"" +
            DUCK_TABLE +
            "\",\n" +
            "      \"model\": \"overwrite\",\n" +
            "      \"dbFile\": \"" +
            DUCK_FILE +
            "\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"connections\": [\n" +
            "    {\"sourceId\": \"StreamJdbcInput\", \"targetId\": \"DuckDBWrite\", \"sourcePort\": 0}\n" +
            "  ],\n" +
            "  \"version\": \"1.0.0\"\n" +
            "}";

        new ETLFlowTask().runJob(job);

        try (Connection duck = DuckDBEngine.getInstance().getConnection(DUCK_FILE); Statement stmt = duck.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT count(*), sum(AMOUNT) FROM main." + DUCK_TABLE)) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "同步到 DuckDB 的行数不正确");
                assertEquals(111.3456d, rs.getBigDecimal(2).doubleValue(), 0.0001d, "同步到 DuckDB 的金额汇总不正确");
            }
        }
    }

    /**
     * Oracle（非 SYS）作为 ETL 目标，验证目标端建表与写入。
     */
    @Test
    @Timeout(180000)
    public void testEtlSyncToOracleAsNonSysUser() throws Exception {
        try (Connection conn = oracleConnection(); Statement stmt = conn.createStatement()) {
            try {
                stmt.execute("DROP TABLE " + SCHEMA + "." + TARGET_TABLE);
            } catch (Exception ignored) {
                // 首次运行不存在
            }
        }

        String job =
            "{\n" +
            "  \"units\": [\n" +
            "    {\n" +
            "      \".id\": \"StreamJdbcInput\",\n" +
            "      \".name\": \"StreamJdbcInput\",\n" +
            "      \"sourceId\": {\n" +
            "        \"type\": \"oracle\",\n" +
            "        \"url\": \"" +
            ORACLE_URL +
            "\",\n" +
            "        \"driver\": \"oracle.jdbc.OracleDriver\",\n" +
            "        \"username\": \"" +
            ORACLE_USER +
            "\",\n" +
            "        \"password\": \"" +
            ORACLE_PASSWORD +
            "\",\n" +
            "        \"dbschema\": \"" +
            SCHEMA +
            "\"\n" +
            "      },\n" +
            "      \"schema\": \"" +
            SCHEMA +
            "\",\n" +
            "      \"table\": \"" +
            SOURCE_TABLE +
            "\"\n" +
            "    },\n" +
            "    {\n" +
            "      \".id\": \"StreamJdbcOutput\",\n" +
            "      \".name\": \"StreamJdbcOutput\",\n" +
            "      \"sourceId\": {\n" +
            "        \"type\": \"oracle\",\n" +
            "        \"url\": \"" +
            ORACLE_URL +
            "\",\n" +
            "        \"driver\": \"oracle.jdbc.OracleDriver\",\n" +
            "        \"username\": \"" +
            ORACLE_USER +
            "\",\n" +
            "        \"password\": \"" +
            ORACLE_PASSWORD +
            "\",\n" +
            "        \"dbschema\": \"" +
            SCHEMA +
            "\"\n" +
            "      },\n" +
            "      \"schema\": \"" +
            SCHEMA +
            "\",\n" +
            "      \"table\": \"" +
            TARGET_TABLE +
            "\",\n" +
            "      \"model\": \"append\"\n" +
            "    }\n" +
            "  ],\n" +
            "  \"connections\": [\n" +
            "    {\"sourceId\": \"StreamJdbcInput\", \"targetId\": \"StreamJdbcOutput\", \"sourcePort\": 0}\n" +
            "  ],\n" +
            "  \"version\": \"1.0.0\"\n" +
            "}";

        new ETLFlowTask().runJob(job);

        try (Connection conn = oracleConnection(); Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT count(*), sum(AMOUNT) FROM " + SCHEMA + "." + TARGET_TABLE)) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1), "写入 Oracle 目标表的行数不正确");
                assertEquals(111.3456d, rs.getBigDecimal(2).doubleValue(), 0.0001d, "写入 Oracle 目标表的金额汇总不正确");
            }
        }
    }
}
