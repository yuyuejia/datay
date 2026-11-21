package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.Connection;
import com.data.job.DatasourceInfo;
import com.data.job.DebugLimitReachedException;
import com.data.job.DebugResult;
import com.data.job.FlowFile;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DorisArrowFlightInput 真实 Doris 联调测试。
 *
 * <p>默认跳过；启动 Doris 2.1+ 并配置 FE/BE arrow_flight_sql_port 后，设置
 * {@code DORIS_ARROW_IT=true} 运行。</p>
 *
 * <p>测试前需要准备：</p>
 * <pre>
 * CREATE DATABASE demo;
 * USE demo;
 * CREATE TABLE orders (
 *   id INT, name VARCHAR(100), amount DECIMAL(10,2),
 *   biz_date DATE, update_time DATETIME
 * ) ENGINE=OLAP DUPLICATE KEY(id)
 * DISTRIBUTED BY HASH(id) BUCKETS 1 PROPERTIES ('replication_num'='1');
 * INSERT INTO orders VALUES
 *   (1,'订单-1',12.34,'2026-11-19','2026-11-19 10:00:00'),
 *   (2,'订单-2',20.00,'2026-11-20','2026-11-20 10:00:00'),
 *   (3,'订单-3',30.50,'2026-11-21','2026-11-21 10:00:00');
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "DORIS_ARROW_IT", matches = "true")
class DorisArrowFlightInputIntegrationTest extends ComponentTestBase {

    private static final String FE_HOST = System.getenv().getOrDefault("DORIS_FE_HOST", "127.0.0.1");
    private static final int QUERY_PORT = Integer.parseInt(System.getenv().getOrDefault("DORIS_QUERY_PORT", "9030"));
    private static final int ARROW_PORT = Integer.parseInt(System.getenv().getOrDefault("DORIS_ARROW_PORT", "8070"));

    private DorisArrowFlightInput component;

    @BeforeEach
    void setUp() {
        component = new DorisArrowFlightInput();
        initComponent(component, "doris-arrow-it", "DorisArrowFlightInput");

        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setType("DORIS");
        datasource.setUrl("jdbc:mysql://" + FE_HOST + ":" + QUERY_PORT + "/demo");
        datasource.setHostname(FE_HOST);
        datasource.setUsername("root");
        datasource.setPassword("");
        datasource.setDbschema("demo");

        component.setSourceId(datasource);
        component.setSchema("demo");
        component.setTable("orders");
        component.setArrowFlightHost(FE_HOST);
        component.setArrowFlightPort(ARROW_PORT);
        component.setBatchSize(2);
    }

    @Test
    void readsArrowFlightBatches() throws Exception {
        LinkedBlockingQueue<Object> queue = attachOutput();

        component.execute(createStartFlowFile());

        List<JSONObject> records = drain(queue);
        assertEquals(3, records.size());

        JSONObject first = records.stream().filter(record -> record.getIntValue("id") == 1).findFirst().orElseThrow();
        assertEquals(1, first.getIntValue("id"));
        assertEquals("订单-1", first.getString("name"));
        assertEquals(0, new BigDecimal("12.34").compareTo(first.getBigDecimal("amount")));
        assertEquals(LocalDate.of(2026, 11, 19), first.getObject("biz_date", LocalDate.class));
        assertEquals(LocalDateTime.of(2026, 11, 19, 10, 0), first.getObject("update_time", LocalDateTime.class));

        JSONObject last = records.stream().filter(record -> record.getIntValue("id") == 3).findFirst().orElseThrow();
        assertEquals(3, last.getIntValue("id"));
        assertEquals("订单-3", last.getString("name"));
    }

    @Test
    void supportsIncrementalRead() throws Exception {
        component.setIncrColumn("id");
        component.setLastIncrValue("orders", 1);
        LinkedBlockingQueue<Object> queue = attachOutput();

        component.execute(createStartFlowFile());

        List<JSONObject> records = drain(queue);
        assertEquals(2, records.size());
        assertEquals(2, records.get(0).getIntValue("id"));
        assertEquals(3, records.get(1).getIntValue("id"));
    }

    @Test
    void debugModeOnlyReadsConfiguredRows() throws Exception {
        context.setDebugMode(true);
        context.setDebugRowLimit(2);
        component.setBatchSize(100);

        assertThrows(DebugLimitReachedException.class, () -> component.execute(createStartFlowFile()));

        DebugResult result = context.getDebugResults().get(component.getId());
        assertNotNull(result);
        assertEquals(2, result.getRows().size());
        assertTrue(result.isTruncated());
    }

    @Test
    void debugModeDefaultsToOneHundredRows() throws Exception {
        String table = "orders_debug_it";
        createTableWithRows(table, 250);
        try {
            component.setTable(table);
            component.setBatchSize(10);
            context.setDebugMode(true);

            assertThrows(DebugLimitReachedException.class, () -> component.execute(createStartFlowFile()));

            DebugResult result = context.getDebugResults().get(component.getId());
            assertNotNull(result);
            assertEquals(100, result.getRows().size());
            assertTrue(result.isTruncated());
        } finally {
            dropTable(table);
        }
    }

    private void createTableWithRows(String table, int rowCount) throws Exception {
        try (java.sql.Connection connection = DBUtils.getConnection(component.getSourceId())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS demo." + table);
                statement.execute(
                    "CREATE TABLE demo." + table + " (" +
                    "id INT, name VARCHAR(100), amount DECIMAL(10,2), biz_date DATE, update_time DATETIME" +
                    ") ENGINE=OLAP DUPLICATE KEY(id) DISTRIBUTED BY HASH(id) BUCKETS 1 " +
                    "PROPERTIES ('replication_num'='1')"
                );
            }
            try (
                PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO demo." + table + " VALUES (?,?,?,?,?)"
                )
            ) {
                for (int i = 1; i <= rowCount; i++) {
                    statement.setInt(1, i);
                    statement.setString(2, "订单-" + i);
                    statement.setBigDecimal(3, new BigDecimal("12.34"));
                    statement.setObject(4, LocalDate.of(2026, 11, 21));
                    statement.setObject(5, LocalDateTime.of(2026, 11, 21, 10, 0));
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }
    }

    private void dropTable(String table) {
        try (java.sql.Connection connection = DBUtils.getConnection(component.getSourceId())) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS demo." + table);
            }
        } catch (Exception ignored) {
            // 清理失败不影响测试结论
        }
    }

    private LinkedBlockingQueue<Object> attachOutput() {
        component.getOutput().add(new Connection(component.getId(), "sink", 0, null, 10));
        LinkedBlockingQueue<Object> queue = new LinkedBlockingQueue<>(10);
        context.getConnections().put(component.getId() + "_sink", queue);
        return queue;
    }

    private List<JSONObject> drain(LinkedBlockingQueue<Object> queue) {
        List<JSONObject> records = new ArrayList<>();
        Object item;
        while ((item = queue.poll()) != null) {
            FlowFile flowFile = (FlowFile) item;
            JSONArray array = flowFile.getJsonArray();
            for (Object record : array) {
                records.add((JSONObject) record);
            }
        }
        return records;
    }
}
