package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.Component;
import com.data.job.ComponentFactory;
import com.data.job.DuckDBEngine;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JoinTest {

    private Join component;
    private ExecutionContext context;
    private TaskLogger taskLogger;

    @BeforeEach
    public void setUp() {
        component = new Join();

        context = new ExecutionContext();
        context.setJobInstanceCode("test-join-instance");
        context.setJobCode("test-join-job");

        taskLogger = new TaskLogger("test-join-job", "test-join-instance");

        component.setContext(context);
        component.setTaskLogger(taskLogger);
        component.setId("test-join");
        component.setName("Join");
        component.setActiveThreads(new AtomicInteger(1));
    }

    @Test
    public void testResolveOutputTableDefault() {
        assertEquals("join_result", component.resolveOutputTable());
    }

    @Test
    public void testResolveOutputTableConfigured() {
        component.setOutputTable(" my_result ");
        assertEquals("my_result", component.resolveOutputTable());
    }

    @Test
    public void testBuildSelectSqlSingleTable() {
        component.setFromTable("orders");
        assertEquals("SELECT * FROM main.orders", component.buildSelectSql());
    }

    @Test
    public void testBuildSelectSqlWithJoins() {
        component.setSelectColumns("orders.id, users.name");
        component.setFromTable("orders");

        JSONArray joins = new JSONArray();
        JSONObject left = new JSONObject();
        left.put("type", "LEFT");
        left.put("table", "users");
        left.put("on", "orders.user_id = users.id");
        joins.add(left);
        JSONObject inner = new JSONObject();
        inner.put("table", "products");
        inner.put("on", "orders.product_id = products.id");
        joins.add(inner);
        component.setJoins(joins);

        assertEquals(
            "SELECT orders.id, users.name FROM main.orders LEFT JOIN main.users ON orders.user_id = users.id INNER JOIN main.products ON orders.product_id = products.id",
            component.buildSelectSql()
        );
    }

    @Test
    public void testBuildSelectSqlWithFieldConditions() {
        component.setSelectColumns("orders.id, users.name");
        component.setFromTable("orders");

        JSONArray joins = new JSONArray();
        JSONObject left = new JSONObject();
        left.put("type", "LEFT");
        left.put("table", "users");
        left.put("leftTable", "orders");
        left.put("leftField", "user_id");
        left.put("rightField", "id");
        joins.add(left);
        component.setJoins(joins);

        assertEquals(
            "SELECT orders.id, users.name FROM main.orders LEFT JOIN main.users ON orders.user_id = users.id",
            component.buildSelectSql()
        );
    }

    @Test
    public void testBuildSelectSqlFieldConditionsCanReferencePreviousJoin() {
        component.setFromTable("orders");

        JSONArray joins = new JSONArray();
        JSONObject users = new JSONObject();
        users.put("type", "LEFT");
        users.put("table", "users");
        users.put("leftField", "user_id");
        users.put("rightField", "id");
        joins.add(users);

        JSONObject products = new JSONObject();
        products.put("type", "INNER");
        products.put("table", "products");
        products.put("leftTable", "users");
        products.put("leftField", "product_id");
        products.put("rightField", "id");
        joins.add(products);
        component.setJoins(joins);

        assertEquals(
            "SELECT * FROM main.orders LEFT JOIN main.users ON orders.user_id = users.id INNER JOIN main.products ON users.product_id = products.id",
            component.buildSelectSql()
        );
    }

    @Test
    public void testBuildSelectSqlWithoutFromTable() {
        assertThrows(IllegalArgumentException.class, () -> component.buildSelectSql());
    }

    @Test
    public void testBuildSelectSqlWithoutOn() {
        component.setFromTable("orders");
        JSONArray joins = new JSONArray();
        JSONObject join = new JSONObject();
        join.put("table", "users");
        joins.add(join);
        component.setJoins(joins);
        assertThrows(IllegalArgumentException.class, () -> component.buildSelectSql());
    }

    @Test
    public void testFactoryBindsJoinConfig() {
        Map<String, Object> params = new HashMap<>();
        params.put(".id", "join1");
        params.put(".name", "Join");
        params.put("outputTable", "configured_result");
        params.put("fromTable", "orders");
        JSONArray joins = new JSONArray();
        JSONObject join = new JSONObject();
        join.put("type", "LEFT");
        join.put("table", "users");
        join.put("leftTable", "orders");
        join.put("leftField", "user_id");
        join.put("rightField", "id");
        joins.add(join);
        params.put("joins", joins);

        Component created = ComponentFactory.create("Join", params);

        assertTrue(created instanceof Join);
        Join joinComponent = (Join) created;
        assertEquals("configured_result", joinComponent.resolveOutputTable());
        assertEquals(
            "SELECT * FROM main.orders LEFT JOIN main.users ON orders.user_id = users.id",
            joinComponent.buildSelectSql()
        );
    }

    @Test
    @Timeout(60000)
    public void testMaterializeAndJoin() throws Exception {
        TableMeta ordersMeta = new TableMeta();
        ordersMeta.setDbType("mysql");
        ordersMeta.addColumn(new ColumnMeta("id", "INT"));
        ordersMeta.addColumn(new ColumnMeta("user_id", "INT"));
        ordersMeta.addColumn(new ColumnMeta("amount", "INT"));

        TableMeta usersMeta = new TableMeta();
        usersMeta.setDbType("mysql");
        usersMeta.addColumn(new ColumnMeta("id", "INT"));
        usersMeta.addColumn(new ColumnMeta("name", "VARCHAR"));

        component.setOutputTable("join_e2e_result");
        component.setSelectColumns("join_e2e_orders.id AS order_id, join_e2e_orders.amount, join_e2e_users.name");
        component.setFromTable("join_e2e_orders");
        JSONArray joins = new JSONArray();
        JSONObject join = new JSONObject();
        join.put("type", "LEFT");
        join.put("table", "join_e2e_users");
        join.put("leftTable", "join_e2e_orders");
        join.put("leftField", "user_id");
        join.put("rightField", "id");
        joins.add(join);
        component.setJoins(joins);

        FlowFile orders = new FlowFile();
        JSONArray orderRows = new JSONArray();
        orderRows.add(row(1, 10, 100));
        orderRows.add(row(2, 11, 200));
        orderRows.add(row(3, 99, 300));
        orders.setJsonArray(orderRows);
        orders.setAttribute(FlowFile.ATTRIBUTE_TABLE, "join_e2e_orders");
        orders.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, ordersMeta);

        FlowFile users = new FlowFile();
        JSONArray userRows = new JSONArray();
        userRows.add(nameRow(10, "alice"));
        userRows.add(nameRow(11, "bob"));
        users.setJsonArray(userRows);
        users.setAttribute(FlowFile.ATTRIBUTE_TABLE, "join_e2e_users");
        users.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, usersMeta);

        component.execute(orders);
        component.upstreamFinish = true;
        component.execute(users);

        try (Connection conn = DuckDBEngine.getInstance().getConnection("test-join-instance");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT order_id, amount, name FROM main.join_e2e_result ORDER BY order_id")) {
            assertTrue(rs.next());
            assertEquals(1, rs.getInt("order_id"));
            assertEquals(100, rs.getInt("amount"));
            assertEquals("alice", rs.getString("name"));
            assertTrue(rs.next());
            assertEquals(2, rs.getInt("order_id"));
            assertEquals("bob", rs.getString("name"));
            assertTrue(rs.next());
            assertEquals(3, rs.getInt("order_id"));
            assertEquals(300, rs.getInt("amount"));
            assertNull(rs.getString("name"));
            assertTrue(!rs.next());
        } finally {
            try (Connection conn = DuckDBEngine.getInstance().getConnection("test-join-instance");
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS main.join_e2e_orders");
                stmt.execute("DROP TABLE IF EXISTS main.join_e2e_users");
                stmt.execute("DROP TABLE IF EXISTS main.join_e2e_result");
            }
            DuckDBEngine.deleteDBFile("test-join-instance");
        }
    }

    @Test
    @Timeout(60000)
    public void testEmptyBatchStillCreatesTable() throws Exception {
        TableMeta ordersMeta = new TableMeta();
        ordersMeta.setDbType("mysql");
        ordersMeta.addColumn(new ColumnMeta("id", "INT"));

        component.setOutputTable("join_empty_result");
        component.setFromTable("join_empty_table");

        FlowFile emptyBatch = new FlowFile();
        emptyBatch.setJsonArray(new JSONArray());
        emptyBatch.setAttribute(FlowFile.ATTRIBUTE_TABLE, "join_empty_table");
        emptyBatch.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, ordersMeta);

        component.execute(emptyBatch);
        component.upstreamFinish = true;
        component.execute(new FlowFile());

        try (Connection conn = DuckDBEngine.getInstance().getConnection("test-join-instance");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM main.join_empty_result")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        } finally {
            try (Connection conn = DuckDBEngine.getInstance().getConnection("test-join-instance");
                 Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE IF EXISTS main.join_empty_table");
                stmt.execute("DROP TABLE IF EXISTS main.join_empty_result");
            }
            DuckDBEngine.deleteDBFile("test-join-instance");
        }
    }

    private JSONObject row(int id, int userId, int amount) {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("user_id", userId);
        obj.put("amount", amount);
        return obj;
    }

    private JSONObject nameRow(int id, String name) {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("name", name);
        return obj;
    }
}
