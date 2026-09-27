package com.data.job.component;

import com.data.job.DatasourceInfo;
import com.data.job.ETLFlowTask;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * PostgreSQL CDC → DuckDB 全字段类型同步验证。
 */
public class PostgresCdcDataTypesToDuckDBTest {

    private static final String PG_URL = "jdbc:postgresql://127.0.0.1:5432/testdb";
    private static final String PG_USER = "admin";
    private static final String PG_PASSWORD = "123456";
    private static final String DUCK_URL = "jdbc:duckdb:./target/cdc-pg-types.duckdb";
    private static final String TABLE = "cdc_types";
    private static final String PUBLICATION = "datay_pub_cdc_types";
    private static final String SLOT = "datay_slot_cdc_types";

    /** 列: name, PG 读取表达式, DuckDB 读取表达式 */
    private static final String[][] COLUMNS = {
        { "id", "cast(id as varchar)", "cast(id as varchar)" },
        { "c_smallint", "cast(c_smallint as varchar)", "cast(c_smallint as varchar)" },
        { "c_int", "cast(c_int as varchar)", "cast(c_int as varchar)" },
        { "c_bigint", "cast(c_bigint as varchar)", "cast(c_bigint as varchar)" },
        { "c_real", "cast(c_real as varchar)", "cast(c_real as varchar)" },
        { "c_double", "cast(c_double as varchar)", "cast(c_double as varchar)" },
        { "c_numeric", "cast(c_numeric as varchar)", "cast(c_numeric as varchar)" },
        { "c_char", "c_char", "c_char" },
        { "c_varchar", "c_varchar", "c_varchar" },
        { "c_text", "c_text", "c_text" },
        { "c_bytea", "upper(encode(c_bytea, 'hex'))", "hex(c_bytea)" },
        { "c_date", "cast(c_date as varchar)", "cast(c_date as varchar)" },
        { "c_time", "cast(c_time as varchar)", "cast(c_time as varchar)" },
        { "c_timestamp", "to_char(c_timestamp, 'YYYY-MM-DD HH24:MI:SS')", "cast(c_timestamp as varchar)" },
        { "c_timestamptz", "to_char(c_timestamptz, 'YYYY-MM-DD HH24:MI:SS')", "cast(c_timestamptz as varchar)" },
        { "c_bool", "cast(c_bool as varchar)", "cast(c_bool as varchar)" },
        { "c_uuid", "cast(c_uuid as varchar)", "cast(c_uuid as varchar)" },
        { "c_jsonb", "cast(c_jsonb as varchar)", "cast(c_jsonb as varchar)" },
        { "c_interval", "cast(c_interval as varchar)", "cast(c_interval as varchar)" },
    };

    @Test
    public void testPostgresCdcDataTypesToDuckDB() throws Exception {
        assumeTrue(supportsLogicalReplication(), "本地 PostgreSQL 未开启 wal_level=logical 或 admin 无 REPLICATION 权限，跳过测试");
        resetEnvironment();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Throwable> jobError = new AtomicReference<>();
        ETLFlowTask runner = new ETLFlowTask();
        String job = new String(java.nio.file.Files.readAllBytes(
            java.nio.file.Paths.get(System.getProperty("user.dir"), "src/test/postgresCdcTypesToDuckDB.json")));

        Future<?> jobFuture = executor.submit(() -> {
            try {
                runner.runJob(job);
            } catch (Throwable e) {
                jobError.set(e);
            }
        });

        try {
            awaitTrue(() -> replicationSlotActive() && publicationExists(), 30000, "等待 PostgreSQL 逻辑复制就绪超时");

            executePg(
                "INSERT INTO public." + TABLE + " VALUES (" +
                "1, 1234, 123456, 9000000000, 1.5, 3.1415926535, 12345.678901, " +
                "'abc', 'hello世界', 'text-value', '\\xdeadbeef'::bytea, " +
                "'2024-03-15', '12:34:56', '2024-03-15 12:34:56', '2024-03-15 12:34:56+00', " +
                "true, '11111111-2222-3333-4444-555555555555', '{\"a\": 1, \"b\": \"x\"}', interval '1 day 02:03:04')"
            );
            awaitTrue(() -> targetRowCount() == 1, 30000, "等待 INSERT 同步到 DuckDB 超时");
            assertRowEquals("INSERT");
            assertTargetTypes();

            executePg(
                "UPDATE public." + TABLE + " SET " +
                "c_smallint=2345, c_int=654321, c_bigint=8000000000, c_real=2.25, c_double=2.7182818284, c_numeric=99999.123456, " +
                "c_char='xyz', c_varchar='updated值', c_text='text-updated', c_bytea='\\x0102'::bytea, " +
                "c_date='2025-01-02', c_time='01:02:03', c_timestamp='2025-01-02 01:02:03', c_timestamptz='2025-01-02 01:02:03+00', " +
                "c_bool=false, c_uuid='aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee', c_jsonb='{\"c\": 3}', c_interval=interval '3 hours' WHERE id=1"
            );
            awaitTrue(() -> "654321".equals(targetColumnString("c_int")), 30000, "等待 UPDATE 同步到 DuckDB 超时");
            assertRowEquals("UPDATE");
        } finally {
            runner.cancel();
            try {
                jobFuture.get(15, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
            executor.shutdownNow();
            DBUtils.closePool(DUCK_URL, null);
            new File("./target/cdc-pg-types.duckdb").delete();
            new File("./target/cdc-pg-types.duckdb.wal").delete();
            dropPublicationAndSlot();
        }

        Throwable error = jobError.get();
        if (error != null && !isCancellation(error)) {
            throw new AssertionError("CDC 同步任务异常结束: " + error.getMessage(), error);
        }
    }

    private void assertRowEquals(String phase) throws Exception {
        Map<String, String> source = readRow(openPg(), TABLE, true);
        Map<String, String> target = readRow(duckConnection(), TABLE, false);
        for (String[] column : COLUMNS) {
            assertEquals(source.get(column[0]), target.get(column[0]), phase + " 列 [" + column[0] + "] 同步不一致");
        }
    }

    private Map<String, String> readRow(Connection conn, String table, boolean postgres) throws Exception {
        Map<String, String> row = new LinkedHashMap<>();
        StringBuilder sql = new StringBuilder("SELECT ");
        for (int i = 0; i < COLUMNS.length; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(postgres ? COLUMNS[i][1] : COLUMNS[i][2]).append(" AS c").append(i);
        }
        sql.append(" FROM ").append(postgres ? "public." + table : "main." + table).append(" WHERE id = 1");
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql.toString())) {
            if (rs.next()) {
                for (int i = 0; i < COLUMNS.length; i++) {
                    row.put(COLUMNS[i][0], rs.getString("c" + i));
                }
            }
        }
        return row;
    }

    private int targetRowCount() {
        return queryDuckInt("select count(*) from main." + TABLE);
    }

    private void assertTargetTypes() throws Exception {
        assertTargetType("c_int", "BIGINT");
        assertTargetType("c_bigint", "BIGINT");
        assertTargetType("c_double", "DOUBLE");
        assertTargetType("c_numeric", "DECIMAL");
        assertTargetType("c_bytea", "BLOB");
        assertTargetType("c_date", "DATE");
        assertTargetType("c_time", "TIME");
        assertTargetType("c_timestamp", "TIMESTAMP");
        assertTargetType("c_bool", "BOOLEAN");
        assertTargetType("c_uuid", "VARCHAR");
        assertTargetType("c_jsonb", "VARCHAR");
    }

    private void assertTargetType(String column, String expectedPrefix) throws Exception {
        String actual;
        try (Connection conn = duckConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(
            "select data_type from information_schema.columns where table_schema = 'main' and table_name = '" + TABLE + "' and lower(column_name) = lower('" + column + "')"
        )) {
            actual = rs.next() ? rs.getString(1) : null;
        }
        assertTrue(actual != null && actual.toUpperCase().startsWith(expectedPrefix), "目标列 " + column + " 类型应为 " + expectedPrefix + "，实际为 " + actual);
    }

    private String targetColumnString(String column) {
        try (Connection conn = duckConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("select " + column + " from main." + TABLE + " where id = 1")) {
            return rs.next() ? rs.getString(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private int queryDuckInt(String sql) {
        try (Connection conn = duckConnection(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private Connection duckConnection() throws Exception {
        DatasourceInfo duck = new DatasourceInfo();
        duck.setUrl(DUCK_URL);
        return DBUtils.getConnection(duck);
    }

    private boolean supportsLogicalReplication() {
        try (Connection conn = openPg()) {
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("show wal_level")) {
                if (!rs.next() || !"logical".equalsIgnoreCase(rs.getString(1))) {
                    return false;
                }
            }
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("select rolreplication from pg_roles where rolname = '" + PG_USER + "'")) {
                return rs.next() && rs.getBoolean(1);
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void resetEnvironment() throws Exception {
        DBUtils.closePool(DUCK_URL, null);
        new File("./target/cdc-pg-types.duckdb").delete();
        new File("./target/cdc-pg-types.duckdb.wal").delete();
        File statusFile = new File("./log/etl_status.json");
        if (statusFile.exists()) {
            statusFile.delete();
        }
        dropPublicationAndSlot();
        try (Connection conn = openPg(); Statement st = conn.createStatement()) {
            st.execute("DROP TABLE IF EXISTS public." + TABLE);
            st.execute(
                "CREATE TABLE public." + TABLE + " (" +
                "id bigint primary key, c_smallint smallint, c_int integer, c_bigint bigint, " +
                "c_real real, c_double double precision, c_numeric numeric(20,6), " +
                "c_char char(10), c_varchar varchar(100), c_text text, c_bytea bytea, " +
                "c_date date, c_time time, c_timestamp timestamp, c_timestamptz timestamptz, " +
                "c_bool boolean, c_uuid uuid, c_jsonb jsonb, c_interval interval)"
            );
            st.execute("ALTER TABLE public." + TABLE + " REPLICA IDENTITY FULL");
        }
    }

    private void dropPublicationAndSlot() {
        try (Connection conn = openPg(); Statement st = conn.createStatement()) {
            st.execute("DROP PUBLICATION IF EXISTS " + PUBLICATION);
            try (ResultSet rs = st.executeQuery("select 1 from pg_replication_slots where slot_name = '" + SLOT + "'")) {
                if (rs.next()) {
                    st.execute("select pg_drop_replication_slot('" + SLOT + "')");
                }
            }
        } catch (Exception ignored) {
        }
    }

    private boolean publicationExists() {
        try (Connection conn = openPg(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("select 1 from pg_publication where pubname = '" + PUBLICATION + "'")) {
            return rs.next();
        } catch (Exception e) {
            return false;
        }
    }

    private boolean replicationSlotActive() {
        try (Connection conn = openPg(); Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("select active from pg_replication_slots where slot_name = '" + SLOT + "'")) {
            return rs.next() && rs.getBoolean(1);
        } catch (Exception e) {
            return false;
        }
    }

    private void executePg(String sql) throws Exception {
        try (Connection conn = openPg(); Statement st = conn.createStatement()) {
            st.execute(sql);
        }
    }

    private Connection openPg() throws Exception {
        return DriverManager.getConnection(PG_URL, PG_USER, PG_PASSWORD);
    }

    private void awaitTrue(BooleanSupplier condition, long timeoutMillis, String message) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(200);
        }
        assertTrue(false, message);
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
}
