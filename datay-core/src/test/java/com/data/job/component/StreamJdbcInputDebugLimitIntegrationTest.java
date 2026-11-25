package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.alibaba.fastjson2.JSONObject;
import com.data.job.DatasourceInfo;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * StreamJdbcInput 调试模式下推 SQL 行数限制的端到端验证。
 *
 * <p>每个用例连接本地数据库，写入 150 行后以调试模式（上限 100）执行
 * {@link StreamJdbcInput}，验证：</p>
 * <ol>
 *   <li>实际执行的 SQL 使用了对应数据库方言的行数限制；</li>
 *   <li>组件只读取到采样上限行数；</li>
 *   <li>把生成的 SQL 直接交给数据库执行，数据库同样只返回采样上限行数，确认限制在库端生效。</li>
 * </ol>
 *
 * <p>数据库未启动时用例自动跳过，不影响常规单元测试。</p>
 */
class StreamJdbcInputDebugLimitIntegrationTest extends ComponentTestBase {

    private static final int TOTAL_ROWS = 150;
    private static final int DEBUG_LIMIT = 100;

    private record DbCase(
        String name,
        String type,
        String url,
        String user,
        String password,
        String schema,
        String table,
        String createSql,
        String expectedLimitFragment
    ) {
        String qualifiedTable() {
            return schema + "." + table;
        }

        String insertSql() {
            return "INSERT INTO " + qualifiedTable() + " (id, name) VALUES (?, ?)";
        }
    }

    private static final DbCase MYSQL = new DbCase(
        "mysql",
        "mysql",
        "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&connectTimeout=3000",
        "root",
        "1234qwer",
        "test",
        "datay_debug_limit",
        "CREATE TABLE test.datay_debug_limit (id INT PRIMARY KEY, name VARCHAR(64))",
        " LIMIT 100"
    );

    private static final DbCase SQLSERVER = new DbCase(
        "sqlserver",
        "sqlserver",
        "jdbc:sqlserver://127.0.0.1:14333;databaseName=datay_cdc;encrypt=true;trustServerCertificate=true;loginTimeout=5",
        "datay",
        "Datay123456!",
        "dbo",
        "datay_debug_limit",
        "CREATE TABLE dbo.datay_debug_limit (id INT PRIMARY KEY, name VARCHAR(64))",
        "SELECT TOP 100 * FROM ("
    );

    private static final DbCase ORACLE = new DbCase(
        "oracle",
        "oracle",
        "jdbc:oracle:thin:@//127.0.0.1:1521/FREEPDB1",
        "datay",
        "datay123",
        "DATAY",
        "DATAY_DEBUG_LIMIT",
        "CREATE TABLE DATAY.DATAY_DEBUG_LIMIT (ID NUMBER(10) PRIMARY KEY, NAME VARCHAR2(64))",
        "WHERE ROWNUM <= 100"
    );

    private static final DbCase DM = new DbCase(
        "dm",
        "dm",
        "jdbc:dm://127.0.0.1:5236?connectTimeout=3000",
        "DATAY",
        "Datay123456",
        "DATAY",
        "DATAY_DEBUG_LIMIT",
        "CREATE TABLE DATAY.DATAY_DEBUG_LIMIT (ID INT PRIMARY KEY, NAME VARCHAR(64))",
        " LIMIT 100"
    );

    private static final DbCase POSTGRESQL = new DbCase(
        "postgresql",
        "postgresql",
        "jdbc:postgresql://127.0.0.1:15432/datay_test?connectTimeout=3",
        "postgres",
        "Datay@12345",
        "public",
        "datay_debug_limit",
        "CREATE TABLE public.datay_debug_limit (id INT PRIMARY KEY, name VARCHAR(64))",
        " LIMIT 100"
    );

    private static final DbCase KINGBASE = new DbCase(
        "kingbase",
        "kingbase",
        "jdbc:kingbase8://127.0.0.1:54321/test?connectTimeout=3",
        "system",
        "Kingbase123",
        "public",
        "datay_debug_limit",
        "CREATE TABLE public.datay_debug_limit (id INT PRIMARY KEY, name VARCHAR(64))",
        " LIMIT 100"
    );

    private static final DbCase DUCKDB = new DbCase(
        "duckdb",
        "duckdb",
        "jdbc:duckdb:./target/stream-jdbc-debug-limit-it.duckdb",
        null,
        null,
        "main",
        "datay_debug_limit",
        "CREATE TABLE main.datay_debug_limit (id INT PRIMARY KEY, name VARCHAR(64))",
        " LIMIT 100"
    );

    private static final DbCase SQLITE = new DbCase(
        "sqlite",
        "sqlite",
        "jdbc:sqlite:./target/stream-jdbc-debug-limit-it.sqlite",
        null,
        null,
        "main",
        "datay_debug_limit",
        "CREATE TABLE main.datay_debug_limit (id INT PRIMARY KEY, name VARCHAR(64))",
        " LIMIT 100"
    );

    @Test
    void mysql() throws Exception {
        verify(MYSQL);
    }

    @Test
    void sqlServer() throws Exception {
        verify(SQLSERVER);
    }

    @Test
    void oracle() throws Exception {
        verify(ORACLE);
    }

    @Test
    void dm() throws Exception {
        verify(DM);
    }

    @Test
    void postgresql() throws Exception {
        verify(POSTGRESQL);
    }

    @Test
    void kingbase() throws Exception {
        verify(KINGBASE);
    }

    @Test
    void duckdb() throws Exception {
        verify(DUCKDB);
    }

    @Test
    void sqlite() throws Exception {
        verify(SQLITE);
    }

    private void verify(DbCase db) throws Exception {
        DatasourceInfo datasource = datasource(db);
        assumeTrue(canConnect(datasource), "本地 " + db.name() + " 未启动或账号不可用，跳过");

        resetTable(datasource, db);
        try {
            CapturingStreamJdbcInput input = new CapturingStreamJdbcInput();
            initComponent(input, "it-" + db.name(), "StreamJdbcInput");
            input.setDatasource(datasource);
            input.setSchema(db.schema());
            input.setTable(db.table());

            context.setDebugMode(true);
            context.setDebugRowLimit(DEBUG_LIMIT);

            input.execute(new FlowFile());

            assertEquals(1, input.queries.size(), db.name() + " 应只执行一次查询");
            String sql = input.queries.get(0);
            assertTrue(
                sql.contains(db.expectedLimitFragment()),
                db.name() + " 调试 SQL 未使用方言限制，实际: " + sql
            );
            assertEquals(DEBUG_LIMIT, input.records.size(), db.name() + " 组件读取行数应等于采样上限");

            try (
                Connection conn = DBUtils.getConnection(datasource);
                Statement statement = conn.createStatement();
                ResultSet rs = statement.executeQuery(sql)
            ) {
                int count = 0;
                while (rs.next()) {
                    count++;
                }
                assertEquals(DEBUG_LIMIT, count, db.name() + " 数据库端应只返回采样上限行数，SQL: " + sql);
            }
        } finally {
            dropTableQuietly(datasource, db);
        }
    }

    private DatasourceInfo datasource(DbCase db) {
        DatasourceInfo info = new DatasourceInfo();
        info.setType(db.type());
        info.setUrl(db.url());
        info.setUsername(db.user());
        info.setPassword(db.password());
        info.setDbschema(db.schema());
        return info;
    }

    private boolean canConnect(DatasourceInfo datasource) {
        try (Connection conn = DBUtils.getConnection(datasource)) {
            return conn.isValid(3);
        } catch (Exception e) {
            return false;
        }
    }

    private void resetTable(DatasourceInfo datasource, DbCase db) throws SQLException {
        dropTableQuietly(datasource, db);
        try (Connection conn = DBUtils.getConnection(datasource)) {
            try (Statement statement = conn.createStatement()) {
                statement.execute(db.createSql());
            }
            try (PreparedStatement statement = conn.prepareStatement(db.insertSql())) {
                for (int i = 1; i <= TOTAL_ROWS; i++) {
                    statement.setInt(1, i);
                    statement.setString(2, "row-" + i);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }
    }

    private void dropTableQuietly(DatasourceInfo datasource, DbCase db) {
        try (Connection conn = DBUtils.getConnection(datasource); Statement statement = conn.createStatement()) {
            statement.execute("DROP TABLE " + db.qualifiedTable());
        } catch (Exception ignored) {
            // 表不存在或数据库不可用时忽略清理失败
        }
    }

    /**
     * 捕获组件实际执行的 SQL 与读取到的记录，不向下游发送数据。
     */
    private static class CapturingStreamJdbcInput extends StreamJdbcInput {

        private final List<String> queries = new ArrayList<>();
        private final List<JSONObject> records = new ArrayList<>();

        @Override
        protected String buildQuery(TableMeta srcTable, String tableIncrColumn, FlowFile flowFile,
                                    String partitionPredicate) {
            String sql = super.buildQuery(srcTable, tableIncrColumn, flowFile, partitionPredicate);
            queries.add(sql);
            return sql;
        }

        @Override
        protected void emitBatch(List<JSONObject> batchRecords, TableMeta srcTable) {
            records.addAll(batchRecords);
        }
    }
}
