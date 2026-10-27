package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.Connection;
import com.data.job.DatasourceInfo;
import com.data.job.FlowFile;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JDBCLookup 的真实 MySQL 跨库场景验证。
 *
 * <p>场景：订单库 {@code datay_it_orders} 中的订单需要补全客户库 {@code datay_it_dim}
 * 里的客户信息（跨库关联），并用 {@code outputPrefix} 避免字段冲突。
 *
 * <p>默认跳过，需要本地 MySQL 时通过环境变量开启：
 * <pre>
 * DATAY_MYSQL_IT=true \
 * DATAY_MYSQL_HOST=127.0.0.1 DATAY_MYSQL_PORT=13306 \
 * DATAY_MYSQL_USER=root DATAY_MYSQL_PASSWORD=****** \
 * mvn -pl datay-core -Dtest=JDBCLookupMySQLTest test
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "DATAY_MYSQL_IT", matches = "true")
public class JDBCLookupMySQLTest extends ComponentTestBase {

    private static final String TARGET_ID = "mysql_lookup_sink";
    private static final String ORDERS_DB = "datay_it_orders";
    private static final String DIM_DB = "datay_it_dim";

    private static final String HOST = env("DATAY_MYSQL_HOST", "127.0.0.1");
    private static final String PORT = env("DATAY_MYSQL_PORT", "3306");
    private static final String USER = env("DATAY_MYSQL_USER", "root");
    private static final String PASSWORD = env("DATAY_MYSQL_PASSWORD", "");
    private static final String PARAMS =
        "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8";
    private static final String SERVER_URL = "jdbc:mysql://" + HOST + ":" + PORT + "/" + PARAMS;
    private static final String DIM_URL = "jdbc:mysql://" + HOST + ":" + PORT + "/" + DIM_DB + PARAMS;
    private static final String ORDERS_URL = "jdbc:mysql://" + HOST + ":" + PORT + "/" + ORDERS_DB + PARAMS;

    private LinkedBlockingQueue<Object> outputQueue;
    private int componentSeq;

    @BeforeEach
    public void prepareDatabases() throws SQLException {
        try (java.sql.Connection conn = DriverManager.getConnection(SERVER_URL, USER, PASSWORD);
             Statement statement = conn.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + ORDERS_DB);
            statement.execute("DROP DATABASE IF EXISTS " + DIM_DB);
            statement.execute("CREATE DATABASE " + ORDERS_DB + " DEFAULT CHARACTER SET utf8mb4");
            statement.execute("CREATE DATABASE " + DIM_DB + " DEFAULT CHARACTER SET utf8mb4");
            statement.execute(
                "CREATE TABLE " + DIM_DB + ".customers ("
                    + "id BIGINT PRIMARY KEY,"
                    + "name VARCHAR(50),"
                    + "member_level VARCHAR(10),"
                    + "region VARCHAR(20),"
                    + "credit_limit DECIMAL(12,2),"
                    + "status TINYINT"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            statement.execute(
                "CREATE TABLE " + ORDERS_DB + ".orders ("
                    + "order_id BIGINT PRIMARY KEY,"
                    + "customer_id BIGINT,"
                    + "amount DECIMAL(10,2),"
                    + "region VARCHAR(20),"
                    + "status VARCHAR(20),"
                    + "created_at DATETIME"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO " + DIM_DB + ".customers (id, name, member_level, region, credit_limit, status) "
                    + "VALUES (?, ?, ?, ?, ?, ?)")) {
                addCustomer(ps, 1001, "张伟", "金卡", "华东", "50000.00", 1);
                addCustomer(ps, 1002, "李娜", "金卡", "华北", "62000.00", 1);
                addCustomer(ps, 1003, "王强", "银卡", "华南", "18000.00", 1);
                addCustomer(ps, 1004, "赵敏", "银卡", "华东", "22000.00", 1);
                addCustomer(ps, 1005, "陈晨", "普通", "西南", "5000.00", 1);
                addCustomer(ps, 1006, "刘洋", "普通", "华北", "3200.00", 1);
                addCustomer(ps, 1007, "周婷", "金卡", "华南", "80000.00", 1);
                // 该客户存在但 status = 0，用于验证 where 过滤
                addCustomer(ps, 1008, "吴磊", "普通", "西北", "1000.00", 0);
                ps.executeBatch();
            }

            long[] customerIds = {
                1001, 1002, 1003, 1001, 1004, 1005, 1006, 1007, 1008, 9999,
                1001, 1002, 1003, 1004, 1005, 1006, 1007, 1001, 1002, 1003
            };
            try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO " + ORDERS_DB + ".orders "
                    + "(order_id, customer_id, amount, region, status, created_at) VALUES (?, ?, ?, ?, ?, ?)")) {
                for (int i = 0; i < customerIds.length; i++) {
                    ps.setLong(1, 2001L + i);
                    ps.setLong(2, customerIds[i]);
                    ps.setBigDecimal(3, new BigDecimal("100.00").add(new BigDecimal(i * 10)));
                    ps.setString(4, "未知区域");
                    ps.setString(5, i % 3 == 0 ? "PAID" : "CREATED");
                    ps.setTimestamp(6, new Timestamp(1_700_000_000_000L + i * 86_400_000L));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    @AfterEach
    public void tearDown() {
        DBUtils.closePool(DIM_URL, USER);
    }

    @Test
    public void testLeftJoinEnrichesOrdersAcrossDatabases() throws Exception {
        JSONArray orders = loadOrders();
        // 模拟上游存在 Integer / String 类型的关联键，验证规范化后仍能匹配 BIGINT
        orders.getJSONObject(3).put("customer_id", 1001);
        orders.getJSONObject(2).put("customer_id", "1003");
        orders.getJSONObject(4).put("customer_id", new BigDecimal("1004.00"));

        JDBCLookup lookup = newLookup();
        lookup.setTable("customers");
        lookup.setLocalField("customer_id");
        lookup.setLookupField("id");
        lookup.setSelectColumns("name, member_level, region, credit_limit, status");
        lookup.setOutputPrefix("customer_");
        lookup.setWhere("status = 1");
        lookup.setJoinType("LEFT");
        lookup.setBatchSize(3);
        lookup.setCache(true);
        prepareOutput(lookup);

        FlowFile output = execute(lookup, orders, ordersMeta());

        JSONArray rows = output.getJsonArray();
        assertEquals(20, rows.size());

        JSONObject first = rows.getJSONObject(0);
        assertEquals("张伟", first.getString("customer_name"));
        assertEquals("金卡", first.getString("customer_member_level"));
        assertEquals("华东", first.getString("customer_region"));
        assertEquals(0, new BigDecimal("50000.00").compareTo(first.getBigDecimal("customer_credit_limit")));
        assertEquals(1, first.getIntValue("customer_status"));
        // 上游原始字段保持不变
        assertEquals(2001L, first.getLongValue("order_id"));
        assertEquals("未知区域", first.getString("region"));

        // 第 3 条 customer_id 被改写为字符串 "1003"，仍应命中王强
        JSONObject stringKeyRow = rows.getJSONObject(2);
        assertEquals("王强", stringKeyRow.getString("customer_name"));
        // 第 4 条 customer_id 为 Integer，第 5 条为 BigDecimal(1004.00)
        assertEquals("张伟", rows.getJSONObject(3).getString("customer_name"));
        assertEquals("赵敏", rows.getJSONObject(4).getString("customer_name"));

        // 1008 存在但被 where 过滤，LEFT 关联补 null
        JSONObject filtered = rows.getJSONObject(8);
        assertTrue(filtered.containsKey("customer_name"));
        assertNull(filtered.get("customer_name"));
        // 9999 在客户库不存在，LEFT 关联补 null
        assertNull(rows.getJSONObject(9).get("customer_name"));

        TableMeta meta = (TableMeta) output.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        assertNotNull(meta);
        assertEquals("orders", meta.getTable());
        assertNotNull(findColumn(meta, "order_id"));
        assertNotNull(findColumn(meta, "customer_name"));
        assertNotNull(findColumn(meta, "customer_member_level"));
        assertNotNull(findColumn(meta, "customer_region"));
        assertNotNull(findColumn(meta, "customer_credit_limit"));
        assertNotNull(findColumn(meta, "customer_status"));
        assertEquals("VARCHAR", findColumn(meta, "customer_name").getType());
        assertEquals("DECIMAL", findColumn(meta, "customer_credit_limit").getType());
    }

    @Test
    public void testInnerJoinDropsUnmatchedOrders() throws Exception {
        JDBCLookup lookup = newLookup();
        lookup.setTable("customers");
        lookup.setLocalField("customer_id");
        lookup.setLookupField("id");
        lookup.setSelectColumns("name");
        lookup.setOutputPrefix("customer_");
        lookup.setJoinType("INNER");
        lookup.setBatchSize(4);
        prepareOutput(lookup);

        FlowFile output = execute(lookup, loadOrders(), ordersMeta());

        // 20 条订单中仅 customer_id=9999 在客户库不存在，INNER 关联后保留 19 条
        assertEquals(19, output.getJsonArray().size());
        for (Object item : output.getJsonArray()) {
            assertNotNull(((JSONObject) item).getString("customer_name"));
        }
    }

    @Test
    public void testRepeatedLookupUsesCache() throws Exception {
        JDBCLookup lookup = newLookup();
        lookup.setTable("customers");
        lookup.setLocalField("customer_id");
        lookup.setLookupField("id");
        lookup.setSelectColumns("name");
        lookup.setOutputPrefix("customer_");
        lookup.setCache(true);
        prepareOutput(lookup);

        FlowFile first = execute(lookup, loadOrders(), ordersMeta());
        assertEquals("张伟", first.getJsonArray().getJSONObject(0).getString("customer_name"));

        // 第一次查询后更新数据库，缓存命中时应仍返回旧值
        updateCustomerName(1001, "张伟-已改名");
        FlowFile second = execute(lookup, loadOrders(), ordersMeta());
        assertEquals("张伟", second.getJsonArray().getJSONObject(0).getString("customer_name"));

        // 关闭缓存的新组件应读到最新值
        JDBCLookup noCache = newLookup();
        noCache.setTable("customers");
        noCache.setLocalField("customer_id");
        noCache.setLookupField("id");
        noCache.setSelectColumns("name");
        noCache.setOutputPrefix("customer_");
        noCache.setCache(false);
        prepareOutput(noCache);
        FlowFile third = execute(noCache, loadOrders(), ordersMeta());
        assertEquals("张伟-已改名", third.getJsonArray().getJSONObject(0).getString("customer_name"));
    }

    @Test
    public void testSameNameFieldOverwritesUpstreamValue() throws Exception {
        JDBCLookup lookup = newLookup();
        lookup.setTable("customers");
        lookup.setLocalField("customer_id");
        lookup.setLookupField("id");
        lookup.setSelectColumns("region, member_level");
        // 不配置前缀：customers.region 会覆盖 orders.region
        lookup.setOutputPrefix("");
        prepareOutput(lookup);

        FlowFile output = execute(lookup, loadOrders(), ordersMeta());

        JSONObject first = output.getJsonArray().getJSONObject(0);
        assertEquals("华东", first.getString("region"));
        assertEquals("金卡", first.getString("member_level"));
    }

    private JDBCLookup newLookup() {
        JDBCLookup lookup = new JDBCLookup();
        initComponent(lookup, "mysql_lookup_" + (++componentSeq), "JDBCLookup");
        lookup.setSourceId(dimDatasource());
        lookup.setSchema(DIM_DB);
        return lookup;
    }

    private FlowFile execute(JDBCLookup lookup, JSONArray orders, TableMeta meta) {
        FlowFile input = new FlowFile();
        input.setJsonArray(orders);
        input.setAttribute(FlowFile.ATTRIBUTE_TABLE, "orders");
        input.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, meta);
        lookup.execute(input);
        Object result = outputQueue == null ? null : outputQueue.poll();
        assertTrue(result instanceof FlowFile, "组件应输出 FlowFile");
        return (FlowFile) result;
    }

    private void prepareOutput(JDBCLookup lookup) {
        lookup.getOutput().add(new Connection(lookup.getId(), TARGET_ID, 0));
        outputQueue = new LinkedBlockingQueue<>();
        context.getConnections().put(lookup.getId() + "_" + TARGET_ID, outputQueue);
    }

    private DatasourceInfo dimDatasource() {
        DatasourceInfo datasource = new DatasourceInfo();
        datasource.setType("mysql");
        datasource.setUrl(DIM_URL);
        datasource.setUsername(USER);
        datasource.setPassword(PASSWORD);
        datasource.setDbschema(DIM_DB);
        return datasource;
    }

    private JSONArray loadOrders() throws SQLException {
        JSONArray rows = new JSONArray();
        try (java.sql.Connection conn = DriverManager.getConnection(ORDERS_URL, USER, PASSWORD);
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(
                 "SELECT order_id, customer_id, amount, region, status, created_at "
                     + "FROM " + ORDERS_DB + ".orders ORDER BY order_id")) {
            while (rs.next()) {
                JSONObject row = new JSONObject();
                row.put("order_id", rs.getLong("order_id"));
                row.put("customer_id", rs.getObject("customer_id"));
                row.put("amount", rs.getBigDecimal("amount"));
                row.put("region", rs.getString("region"));
                row.put("status", rs.getString("status"));
                row.put("created_at", rs.getTimestamp("created_at"));
                rows.add(row);
            }
        }
        return rows;
    }

    private TableMeta ordersMeta() throws SQLException {
        try (java.sql.Connection conn = DriverManager.getConnection(ORDERS_URL, USER, PASSWORD)) {
            TableMeta meta = DBUtils.getTableMetaData(conn, ORDERS_DB, "orders");
            meta.setDbType("mysql");
            meta.setCatalog(ORDERS_DB);
            meta.setSchema(ORDERS_DB);
            meta.setTable("orders");
            return meta;
        }
    }

    private void updateCustomerName(long id, String name) throws SQLException {
        try (java.sql.Connection conn = DriverManager.getConnection(SERVER_URL, USER, PASSWORD);
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE " + DIM_DB + ".customers SET name = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    private void addCustomer(PreparedStatement ps, long id, String name, String level,
                             String region, String creditLimit, int status) throws SQLException {
        ps.setLong(1, id);
        ps.setString(2, name);
        ps.setString(3, level);
        ps.setString(4, region);
        ps.setBigDecimal(5, new BigDecimal(creditLimit));
        ps.setInt(6, status);
        ps.addBatch();
    }

    private ColumnMeta findColumn(TableMeta meta, String name) {
        List<ColumnMeta> columns = meta.columns();
        for (ColumnMeta column : columns) {
            if (name.equalsIgnoreCase(column.getName())) {
                return column;
            }
        }
        return null;
    }

    private static String env(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }
}