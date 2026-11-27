package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datasource.DataSourceConnection;
import com.data.datasource.DataSourceProvider;
import com.data.datasource.DataSourceProviderFactory;
import com.data.datasource.QueryResult;
import com.data.job.DatasourceInfo;
import com.data.metadata.TableMeta;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Oracle 数据查询能力验证：元数据查询 + SQL 执行。
 *
 * <p>默认连接本地 Oracle Free（{@code datay/datay123}，schema {@code DATAY}），可用系统属性覆盖：
 * <pre>
 *   -Doracle.url= -Doracle.user= -Doracle.password= -Doracle.schema=
 * </pre>
 * 本地无 Oracle 时自动跳过。
 */
class OracleDataQueryVerificationTest {

    private static final String ORACLE_URL = System.getProperty(
        "oracle.url",
        "jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1"
    );
    private static final String ORACLE_USER = System.getProperty("oracle.user", "datay");
    private static final String ORACLE_PASSWORD = System.getProperty("oracle.password", "datay123");
    private static final String SCHEMA = System.getProperty("oracle.schema", "DATAY");

    private static final String TABLE = "DQ_VERIFY";
    private static final String QUALIFIED = SCHEMA + "." + TABLE;

    private DataSourceQueryService queryService;
    private DataSourceDTO dataSource;

    @BeforeAll
    static void prepareTable() throws Exception {
        assumeTrue(canConnect(), "本地 Oracle 不可用，跳过数据查询验证");
        try (Connection conn = oracleConnection(); Statement st = conn.createStatement()) {
            try {
                st.execute("DROP TABLE " + QUALIFIED);
            } catch (Exception ignored) {
                // 首次运行不存在
            }
            st.execute(
                "CREATE TABLE " +
                QUALIFIED +
                " (ID NUMBER(10) PRIMARY KEY, NAME VARCHAR2(100) NOT NULL, AMOUNT NUMBER(18,4), " +
                "CREATED_AT TIMESTAMP, NOTE CLOB)"
            );
            st.execute("COMMENT ON TABLE " + QUALIFIED + " IS 'data query verify table'");
            st.execute("COMMENT ON COLUMN " + QUALIFIED + ".NAME IS 'name column'");
            st.execute("CREATE INDEX IDX_DQ_VERIFY_NAME ON " + QUALIFIED + "(NAME)");
            st.execute(
                "INSERT INTO " +
                QUALIFIED +
                " (ID, NAME, AMOUNT, CREATED_AT, NOTE) VALUES " +
                "(1, 'alpha', 12.3456, TIMESTAMP '2024-01-01 10:00:00', 'note-alpha')"
            );
            st.execute(
                "INSERT INTO " +
                QUALIFIED +
                " (ID, NAME, AMOUNT, CREATED_AT, NOTE) VALUES " +
                "(2, 'beta', 99.0000, TIMESTAMP '2024-01-02 11:30:00', 'note-beta')"
            );
            st.execute(
                "INSERT INTO " +
                QUALIFIED +
                " (ID, NAME, AMOUNT, CREATED_AT, NOTE) VALUES " +
                "(3, 'gamma', 3.5000, TIMESTAMP '2024-01-03 12:45:00', NULL)"
            );

            try {
                st.execute("DROP TABLE " + SCHEMA + ".DQ_TZ_VERIFY");
            } catch (Exception ignored) {
                // 首次运行不存在
            }
            st.execute(
                "CREATE TABLE " +
                SCHEMA +
                ".DQ_TZ_VERIFY (ID NUMBER(10), TZ TIMESTAMP WITH TIME ZONE, TLZ TIMESTAMP WITH LOCAL TIME ZONE)"
            );
            st.execute(
                "INSERT INTO " +
                SCHEMA +
                ".DQ_TZ_VERIFY (ID, TZ, TLZ) VALUES " +
                "(1, TIMESTAMP '2024-05-06 07:08:09 +08:00', TIMESTAMP '2024-05-06 07:08:09')"
            );
        }
        try (Connection conn = oracleConnection(); Statement st = conn.createStatement()) {
            st.execute(
                "BEGIN EXECUTE IMMEDIATE 'DROP TABLE " +
                SCHEMA +
                ".DQ_VERIFY_TMP'; EXCEPTION WHEN OTHERS THEN NULL; END;"
            );
        }
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (!canConnect()) {
            return;
        }
        try (Connection conn = oracleConnection(); Statement st = conn.createStatement()) {
            st.execute(
                "BEGIN EXECUTE IMMEDIATE 'DROP TABLE " +
                SCHEMA +
                ".DQ_VERIFY_TMP'; EXCEPTION WHEN OTHERS THEN NULL; END;"
            );
            st.execute(
                "BEGIN EXECUTE IMMEDIATE 'DROP TABLE " +
                SCHEMA +
                ".DQ_TZ_VERIFY'; EXCEPTION WHEN OTHERS THEN NULL; END;"
            );
        }
    }

    @Test
    void shouldReadOracleMetadata() throws Exception {
        DataSourceProvider provider = DataSourceProviderFactory.provider("oracle");
        assertThat(provider).isNotNull();

        try (DataSourceConnection connection = provider.connect(datasourceInfo())) {
            assertThat(connection.testConnection()).isTrue();
            assertThat(connection.getCatalogs()).isEmpty();
            assertThat(connection.getSchemas()).anyMatch(s -> SCHEMA.equalsIgnoreCase(s));

            List<TableMeta> tables = connection.getTables(SCHEMA, null, TABLE);
            assertThat(tables).anyMatch(t -> TABLE.equalsIgnoreCase(t.getTable()));

            TableMeta meta = connection.getTableMeta(SCHEMA, TABLE);
            assertThat(meta.getDbType()).isEqualTo("ORACLE");
            assertThat(meta.columns()).hasSize(5);
            assertThat(meta.columns()).anyMatch(c -> "ID".equalsIgnoreCase(c.getName()) && c.isPrimaryKey());
            assertThat(meta.columns()).anyMatch(c -> "NAME".equalsIgnoreCase(c.getName()) && !c.isNullable());
            assertThat(meta.columns()).anyMatch(c -> "NOTE".equalsIgnoreCase(c.getName()));

            TableMeta detail = connection.getTableDetail(SCHEMA, TABLE);
            assertThat(detail.getComment()).isEqualTo("data query verify table");
            assertThat(detail.columns())
                .anyMatch(c -> "NAME".equalsIgnoreCase(c.getName()) && "name column".equals(c.getComment()));
            assertThat(detail.getIndexes()).anyMatch(i -> "IDX_DQ_VERIFY_NAME".equalsIgnoreCase(i.getName()));
        }
    }

    @Test
    void shouldExecuteSelectWithRowLimit() throws Exception {
        Map<String, Object> result = queryService().executeQuery(
            dataSource(),
            "SELECT ID, NAME, AMOUNT, CREATED_AT, NOTE FROM " + QUALIFIED + " ORDER BY ID",
            2
        );

        assertThat(result.get("columns")).isEqualTo(List.of("ID", "NAME", "AMOUNT", "CREATED_AT", "NOTE"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(rows).hasSize(2);
        assertThat(result.get("truncated")).isEqualTo(true);
        assertThat(rows.get(0).get("AMOUNT")).isInstanceOf(BigDecimal.class);
        assertThat(rows.get(0).get("CREATED_AT")).isNotNull();
        assertThat(rows.get(0).get("NOTE")).isEqualTo("note-alpha");
    }

    @Test
    void shouldExecuteLowercaseUnquotedSqlAndTrailingSemicolon() throws Exception {
        Map<String, Object> result = queryService().executeQuery(
            dataSource(),
            "select id, name from datay.dq_verify where id = 1;",
            null
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("NAME")).isEqualTo("alpha");
    }

    @Test
    void shouldExecuteParameterizedQuery() throws Exception {
        try (DataSourceConnection connection = DataSourceProviderFactory.connect(datasourceInfo())) {
            QueryResult result = connection.executeQuery(
                "SELECT NAME FROM " + QUALIFIED + " WHERE ID = ?",
                List.of(2),
                10
            );
            assertThat(result.getRows()).hasSize(1);
            assertThat(result.getRows().get(0).get("NAME")).isEqualTo("beta");
        }
    }

    @Test
    void shouldExecuteDmlAndDdl() throws Exception {
        DataSourceQueryService service = queryService();
        DataSourceDTO dto = dataSource();

        Map<String, Object> insert = service.executeQuery(
            dto,
            "INSERT INTO " + QUALIFIED + " (ID, NAME, AMOUNT) VALUES (4, 'delta', 4.5)",
            10
        );
        assertThat(insert.get("affectedRows")).isEqualTo(1);

        Map<String, Object> update = service.executeQuery(dto, "UPDATE " + QUALIFIED + " SET NAME = 'delta2' WHERE ID = 4", 10);
        assertThat(update.get("affectedRows")).isEqualTo(1);

        Map<String, Object> count = service.executeQuery(dto, "SELECT COUNT(*) AS CNT FROM " + QUALIFIED, 10);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) count.get("rows");
        assertThat(((Number) rows.get(0).get("CNT")).intValue()).isEqualTo(4);

        Map<String, Object> ddl = service.executeQuery(
            dto,
            "CREATE TABLE " + SCHEMA + ".DQ_VERIFY_TMP (ID NUMBER(10), NAME VARCHAR2(20))",
            10
        );
        assertThat(ddl.get("affectedRows")).isEqualTo(0);

        Map<String, Object> delete = service.executeQuery(dto, "DELETE FROM " + QUALIFIED + " WHERE ID = 4", 10);
        assertThat(delete.get("affectedRows")).isEqualTo(1);
    }

    @Test
    void shouldReadTimestampWithTimeZone() throws Exception {
        Map<String, Object> result = queryService().executeQuery(
            dataSource(),
            "SELECT TZ, TLZ FROM " + SCHEMA + ".DQ_TZ_VERIFY WHERE ID = 1",
            10
        );

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(rows).hasSize(1);
        // 旧实现会输出 oracle.sql.TIMESTAMPTZ@1a2b3c 之类的对象地址
        assertThat(String.valueOf(rows.get(0).get("TZ")))
            .doesNotContain("@")
            .containsPattern("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}");
        assertThat(String.valueOf(rows.get(0).get("TLZ")))
            .doesNotContain("@")
            .containsPattern("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}");
    }

    @Test
    void shouldExecutePlsqlBlockWithTrailingSemicolon() throws Exception {
        DataSourceQueryService service = queryService();
        // PL/SQL 匿名块必须保留 END; 的结尾分号，不能被通用的“去尾分号”逻辑破坏
        service.executeQuery(
            dataSource(),
            "BEGIN UPDATE " + QUALIFIED + " SET NAME = 'alpha' WHERE ID = 1; END;",
            10
        );

        Map<String, Object> result = service.executeQuery(
            dataSource(),
            "SELECT NAME FROM " + QUALIFIED + " WHERE ID = 1",
            10
        );
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(rows.get(0).get("NAME")).isEqualTo("alpha");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPreserveDuplicateColumnLabels() throws Exception {
        Map<String, Object> result = queryService().executeQuery(
            dataSource(),
            "SELECT ID AS X, NAME AS X FROM " + QUALIFIED + " ORDER BY ID",
            10
        );

        assertThat((List<String>) result.get("columns")).containsExactly("X", "X_2");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        assertThat(rows).hasSize(3);
        assertThat(((Number) rows.get(0).get("X")).intValue()).isEqualTo(1);
        assertThat(rows.get(0).get("X_2")).isEqualTo("alpha");
    }

    @Test
    void shouldSearchTablesCaseInsensitively() throws Exception {
        try (DataSourceConnection connection = DataSourceProviderFactory.connect(datasourceInfo())) {
            assertThat(connection.getTables(SCHEMA, null, "dq_verify"))
                .filteredOn(t -> TABLE.equalsIgnoreCase(t.getTable()))
                .hasSize(1);
            assertThat(connection.getTables(SCHEMA, null, "Dq_Verify"))
                .filteredOn(t -> TABLE.equalsIgnoreCase(t.getTable()))
                .hasSize(1);
            // REST 入口会同时传 catalog（Oracle 为 null），确认走 catalog 重载时也生效
            assertThat(connection.getTables(null, SCHEMA, null, "dq_verify"))
                .filteredOn(t -> TABLE.equalsIgnoreCase(t.getTable()))
                .hasSize(1);
        }
    }

    private DataSourceQueryService queryService() {
        if (queryService == null) {
            queryService = new DataSourceQueryService();
        }
        return queryService;
    }

    private DataSourceDTO dataSource() {
        if (dataSource == null) {
            dataSource = new DataSourceDTO();
            dataSource.setType("oracle");
            dataSource.setUrl(ORACLE_URL);
            dataSource.setUsername(ORACLE_USER);
            dataSource.setPassword(ORACLE_PASSWORD);
            dataSource.setSchemaName(SCHEMA);
        }
        return dataSource;
    }

    private static DatasourceInfo datasourceInfo() {
        DatasourceInfo info = new DatasourceInfo();
        info.setType("oracle");
        info.setUrl(ORACLE_URL);
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
}
