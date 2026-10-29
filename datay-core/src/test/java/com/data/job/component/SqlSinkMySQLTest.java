package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.data.job.DatasourceInfo;
import com.data.job.FlowFile;
import com.data.metadata.util.DBUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * SqlSink 的 MySQL 集成测试：验证 ON DUPLICATE KEY UPDATE 的原子累加与负增量。
 */
public class SqlSinkMySQLTest extends ComponentTestBase {

    private static final String MYSQL_URL =
        "jdbc:mysql://127.0.0.1:3306/test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8";
    private static final String MYSQL_USER = "root";
    private static final String MYSQL_PASSWORD = "1234qwer";
    private static final String TABLE = "test.sql_sink_summary";

    private SqlSink sink;
    private DatasourceInfo datasource;

    @BeforeEach
    public void setUp() throws Exception {
        assumeTrue(canConnect(), "本地 MySQL 不可用，跳过 SqlSink MySQL 集成测试");
        super.setUpBase();

        datasource = new DatasourceInfo();
        datasource.setType("mysql");
        datasource.setUrl(MYSQL_URL);
        datasource.setUsername(MYSQL_USER);
        datasource.setPassword(MYSQL_PASSWORD);
        datasource.setDbschema("test");

        try (Connection conn = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD); Statement statement = conn.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + TABLE);
            statement.execute(
                "CREATE TABLE " +
                TABLE +
                " (" +
                "stat_date DATE NOT NULL," +
                "store_key VARCHAR(50) NOT NULL," +
                "order_cnt BIGINT NOT NULL DEFAULT 0," +
                "sales_amount DECIMAL(18,2) NOT NULL DEFAULT 0," +
                "PRIMARY KEY (stat_date, store_key)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            statement.execute(
                "INSERT INTO " + TABLE + " (stat_date, store_key, order_cnt, sales_amount) VALUES ('2026-10-29', 'S1', 10, 100.00)"
            );
        }

        sink = new SqlSink();
        initComponent(sink, "sql-sink-mysql", "SqlSink");
        sink.setDatasource(datasource);
        sink.setBatchSize(2);
        sink.setTransaction(true);
        sink.setSql(
            "INSERT INTO " +
            TABLE +
            " (stat_date, store_key, order_cnt, sales_amount) " +
            "VALUES (${stat_date}, ${store_key}, ${order_cnt}, ${sales_amount}) " +
            "ON DUPLICATE KEY UPDATE order_cnt = order_cnt + VALUES(order_cnt), " +
            "sales_amount = sales_amount + VALUES(sales_amount)"
        );
    }

    @AfterEach
    public void tearDown() {
        try (Connection conn = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD); Statement statement = conn.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS " + TABLE);
        } catch (Exception ignored) {
            // 数据库不可用时 setUp 已跳过，这里不再失败
        }
        DBUtils.closePool(MYSQL_URL, MYSQL_USER);
    }

    @Test
    @Timeout(120000)
    public void testAccumulateAndNegativeDelta() throws Exception {
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(
            JSONArray.parseArray(
                "[{\"stat_date\":\"2026-10-29\",\"store_key\":\"S1\",\"order_cnt\":2,\"sales_amount\":20.50}," +
                "{\"stat_date\":\"2026-10-29\",\"store_key\":\"S1\",\"order_cnt\":3,\"sales_amount\":30.00}," +
                "{\"stat_date\":\"2026-10-29\",\"store_key\":\"S2\",\"order_cnt\":1,\"sales_amount\":10.00}]"
            )
        );
        sink.execute(flowFile);

        assertSummary("S1", 15, new BigDecimal("150.50"));
        assertSummary("S2", 1, new BigDecimal("10.00"));

        FlowFile negativeFlowFile = new FlowFile();
        negativeFlowFile.setJsonArray(
            JSONArray.parseArray(
                "[{\"stat_date\":\"2026-10-29\",\"store_key\":\"S1\",\"order_cnt\":-1,\"sales_amount\":-50.00}]"
            )
        );
        sink.execute(negativeFlowFile);

        assertSummary("S1", 14, new BigDecimal("100.50"));
    }

    private void assertSummary(String storeKey, long orderCnt, BigDecimal salesAmount) throws Exception {
        try (Connection conn = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD); Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery(
                 "SELECT order_cnt, sales_amount FROM " + TABLE + " WHERE stat_date = '2026-10-29' AND store_key = '" + storeKey + "'"
             )) {
            assertTrue(rs.next(), "应存在 store_key=" + storeKey + " 的汇总记录");
            assertEquals(orderCnt, rs.getLong("order_cnt"));
            assertEquals(0, salesAmount.compareTo(rs.getBigDecimal("sales_amount")));
        }
    }

    private boolean canConnect() {
        try (Connection ignored = DriverManager.getConnection(MYSQL_URL, MYSQL_USER, MYSQL_PASSWORD)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}