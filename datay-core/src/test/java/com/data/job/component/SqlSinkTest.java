package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.data.job.Component;
import com.data.job.ComponentFactory;
import com.data.job.DatasourceInfo;
import com.data.job.FlowFile;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SqlSink 的 DuckDB 本地测试：覆盖动态参数绑定、累加、批量、嵌套字段、事务回滚等。
 */
public class SqlSinkTest extends ComponentTestBase {

    private SqlSink sink;
    private DatasourceInfo datasource;
    private String url;
    private String dbFile;

    @BeforeEach
    public void setUp() {
        super.setUpBase();
        dbFile = "./target/sql-sink-" + UUID.randomUUID() + ".duckdb";
        url = "jdbc:duckdb:" + dbFile;

        datasource = new DatasourceInfo();
        datasource.setUrl(url);
        datasource.setType("duckdb");

        sink = new SqlSink();
        initComponent(sink, "sql-sink", "SqlSink");
        sink.setDatasource(datasource);
        sink.setBatchSize(2);
        sink.setTransaction(true);
    }

    @AfterEach
    public void tearDown() {
        DBUtils.closePool(url, null);
        new File(dbFile).delete();
        new File(dbFile + ".wal").delete();
    }

    @Test
    @Timeout(60000)
    public void testAccumulateWithOnConflict() throws Exception {
        executeSql("CREATE TABLE summary (id INTEGER PRIMARY KEY, cnt INTEGER, amount DECIMAL(18,2))");
        executeSql("INSERT INTO summary (id, cnt, amount) VALUES (1, 10, 100.00)");

        sink.setSql(
            "INSERT INTO summary (id, cnt, amount) VALUES (${id}, ${cnt}, ${amount}) " +
            "ON CONFLICT (id) DO UPDATE SET cnt = summary.cnt + excluded.cnt, amount = summary.amount + excluded.amount"
        );

        sink.execute(
            flowFile(
                "[{\"id\":1,\"cnt\":2,\"amount\":20.50}," +
                "{\"id\":1,\"cnt\":3,\"amount\":30.00}," +
                "{\"id\":2,\"cnt\":1,\"amount\":10.00}]"
            )
        );

        assertSummary(1, 15, new BigDecimal("150.50"));
        assertSummary(2, 1, new BigDecimal("10.00"));
    }

    @Test
    @Timeout(60000)
    public void testBatchSplitting() throws Exception {
        executeSql("CREATE TABLE batch_test (id INTEGER PRIMARY KEY, cnt INTEGER)");
        sink.setBatchSize(1);
        sink.setSql("INSERT INTO batch_test (id, cnt) VALUES (${id}, ${cnt})");

        sink.execute(flowFile("[{\"id\":1,\"cnt\":10},{\"id\":2,\"cnt\":20},{\"id\":3,\"cnt\":30}]"));

        assertEquals(3, count("batch_test"));
    }

    @Test
    @Timeout(60000)
    public void testNestedFieldAndFlowFileAttributeBinding() throws Exception {
        executeSql("CREATE TABLE bind_test (id INTEGER, before_amount INTEGER, event_type VARCHAR)");
        sink.setSql(
            "INSERT INTO bind_test (id, before_amount, event_type) " +
            "VALUES (${id}, ${__before.amount}, ${attr:_eventType})"
        );

        FlowFile flowFile = flowFile("[{\"id\":1,\"__before\":{\"amount\":5}}]");
        flowFile.setAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE, "UPDATE");
        sink.execute(flowFile);

        try (Connection conn = DBUtils.getConnection(datasource); Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery("SELECT id, before_amount, event_type FROM bind_test")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("id"));
            assertEquals(5, rs.getInt("before_amount"));
            assertEquals("UPDATE", rs.getString("event_type"));
        }
    }

    @Test
    @Timeout(60000)
    public void testQuestionMarkInValueIsBound() throws Exception {
        executeSql("CREATE TABLE question_test (value VARCHAR)");
        sink.setSql("INSERT INTO question_test (value) VALUES (${value})");

        sink.execute(flowFile("[{\"value\":\"?\"}]"));

        try (Connection conn = DBUtils.getConnection(datasource); Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery("SELECT value FROM question_test")) {
            assertTrue(rs.next());
            assertEquals("?", rs.getString("value"));
        }
    }

    @Test
    @Timeout(60000)
    public void testStringValueIsBoundWithoutQuotes() throws Exception {
        executeSql("CREATE TABLE string_test (id INTEGER, name VARCHAR)");
        sink.setSql("INSERT INTO string_test (id, name) VALUES (${id}, ${name})");

        sink.execute(flowFile("[{\"id\":1,\"name\":\"O'Reilly\"}]"));

        try (Connection conn = DBUtils.getConnection(datasource); Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery("SELECT name FROM string_test WHERE id = 1")) {
            assertTrue(rs.next());
            assertEquals("O'Reilly", rs.getString("name"));
        }
    }

    @Test
    @Timeout(60000)
    public void testUnresolvedDynamicParameterFails() throws Exception {
        executeSql("CREATE TABLE unresolved_test (id INTEGER, value VARCHAR)");
        sink.setSql("INSERT INTO unresolved_test (id, value) VALUES (${id}, ${not_exists})");

        IllegalArgumentException error = assertThrows(
            IllegalArgumentException.class,
            () -> sink.execute(flowFile("[{\"id\":1}]"))
        );
        assertTrue(error.getMessage().contains("${not_exists}"));
    }

    @Test
    @Timeout(60000)
    public void testTransactionRollsBackAllBatches() throws Exception {
        executeSql("CREATE TABLE tx_test (id INTEGER PRIMARY KEY, cnt INTEGER)");
        sink.setBatchSize(1);
        sink.setTransaction(true);
        sink.setSql("INSERT INTO tx_test (id, cnt) VALUES (${id}, ${cnt})");

        assertThrows(
            RuntimeException.class,
            () -> sink.execute(flowFile("[{\"id\":1,\"cnt\":10},{\"id\":1,\"cnt\":20}]"))
        );
        assertEquals(0, count("tx_test"));
    }

    @Test
    @Timeout(60000)
    public void testIgnoreErrorSkipsFailedWrite() throws Exception {
        sink.setSql("INSERT INTO missing_table (id, cnt) VALUES (${id}, ${cnt})");
        sink.setIgnoreError(true);

        sink.execute(flowFile("[{\"id\":1,\"cnt\":2}]"));
    }

    @Test
    @Timeout(60000)
    public void testStaticSqlExecutesOncePerFlowFile() throws Exception {
        executeSql("CREATE TABLE static_test (id INTEGER, cnt INTEGER)");
        sink.setSql("INSERT INTO static_test (id, cnt) VALUES (1, 10)");

        sink.execute(flowFile("[]"));
        assertEquals(0, count("static_test"));

        FlowFile flowFile = flowFile("[{\"ignored\":true}]");
        sink.execute(flowFile);
        assertEquals(1, count("static_test"));
    }

    @Test
    @Timeout(60000)
    public void testEventTypeFilter() throws Exception {
        executeSql("CREATE TABLE event_test (id INTEGER)");
        sink.setSql("INSERT INTO event_test (id) VALUES (${id})");
        sink.setEventTypes(List.of("INSERT"));

        FlowFile deleteFlowFile = flowFile("[{\"id\":1}]");
        deleteFlowFile.setAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE, "DELETE");
        sink.execute(deleteFlowFile);
        assertEquals(0, count("event_test"));

        FlowFile insertFlowFile = flowFile("[{\"id\":2}]");
        insertFlowFile.setAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE, "INSERT");
        sink.execute(insertFlowFile);
        assertEquals(1, count("event_test"));
    }

    @Test
    public void testComponentFactoryRegistration() {
        assertNotNull(ComponentFactory.getDescriptor("SqlSink"));

        Map<String, Object> source = new HashMap<>();
        source.put("url", url);
        source.put("type", "duckdb");

        Map<String, Object> params = new HashMap<>();
        params.put(".id", "sql-sink");
        params.put(".name", "SqlSink");
        params.put("sourceId", source);
        params.put("datasource", source);
        params.put("sql", "INSERT INTO t (id) VALUES (${id})");

        Component component = ComponentFactory.create("SqlSink", params);
        assertTrue(component instanceof SqlSink);
    }

    @Test
    public void testFirstKeywordSkipsComments() {
        assertEquals("INSERT", SqlSink.firstKeyword("-- comment\n/* block */ insert into t values (1)"));
    }

    private FlowFile flowFile(String json) {
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(JSONArray.parseArray(json));
        return flowFile;
    }

    private void executeSql(String sql) throws Exception {
        try (Connection conn = DBUtils.getConnection(datasource); Statement statement = conn.createStatement()) {
            statement.execute(sql);
        }
    }

    private int count(String table) throws Exception {
        try (Connection conn = DBUtils.getConnection(datasource); Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    private void assertSummary(int id, int cnt, BigDecimal amount) throws Exception {
        try (Connection conn = DBUtils.getConnection(datasource); Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(
                 "SELECT cnt, amount FROM summary WHERE id = " + id
             )) {
            assertTrue(rs.next(), "summary 中应存在 id=" + id + " 的记录");
            assertEquals(cnt, rs.getInt("cnt"));
            assertEquals(0, amount.compareTo(rs.getBigDecimal("amount")));
        }
    }
}
