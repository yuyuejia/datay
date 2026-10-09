package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.Connection;
import com.data.job.ExecutionContext;
import com.data.job.FlowFile;
import com.data.job.TaskLogger;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.math.BigDecimal;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 回归验证：上游表元数据来自 DuckDB（{@code TYPE_NAME=DECIMAL(18,2)}）时，
 * StreamSqlUnit 建临时表不能再把 DECIMAL 误判为 VARCHAR，否则聚合 SUM 会报 "sum(VARCHAR)"。
 */
public class StreamSqlUnitDecimalTest {

    private StreamSqlUnit component;
    private ExecutionContext context;
    private LinkedBlockingQueue<Object> outputQueue;

    @BeforeEach
    public void setUp() {
        component = new StreamSqlUnit();

        context = new ExecutionContext();
        context.setJobInstanceCode("test-sqlunit-decimal");
        context.setJobCode("test-job");

        TaskLogger taskLogger = new TaskLogger("test-job", "test-sqlunit-decimal");

        component.setContext(context);
        component.setTaskLogger(taskLogger);
        component.setId("n2");
        component.setName("StreamSqlUnit");
        component.setActiveThreads(new AtomicInteger(1));
        component.setSql(
            "SELECT store_key, CAST(COUNT(*) AS BIGINT) AS order_cnt, " +
            "CAST(SUM(order_amount) AS DECIMAL(18,2)) AS sales_amount FROM ods_order GROUP BY store_key"
        );

        component.getOutput().add(new Connection("n2", "n3", 0));
        outputQueue = new LinkedBlockingQueue<>();
        context.getConnections().put("n2_n3", outputQueue);
    }

    @Test
    @Timeout(120000)
    public void testAggregateDecimalColumnFromDuckDBMetadata() {
        JSONArray records = new JSONArray();
        records.add(record("S001", new BigDecimal("100.50")));
        records.add(record("S001", new BigDecimal("200.00")));
        records.add(record("S002", new BigDecimal("50.25")));

        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(records);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, "ods_order");
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, tableMeta());

        assertDoesNotThrow(() -> component.execute(flowFile));

        FlowFile out = (FlowFile) outputQueue.poll();
        assertNotNull(out, "应输出聚合结果");
        assertEquals(2, out.getJsonArray().size(), "应按门店聚合为 2 行");
    }

    private TableMeta tableMeta() {
        TableMeta table = new TableMeta();
        table.setTable("ods_order");
        table.setSchema("main");
        // 与真实 StreamJdbcInput 一致：DBType.DUCKDB.toString()
        table.setDbType("DUCKDB");
        table.addColumn(new ColumnMeta("order_id", "VARCHAR"));
        table.addColumn(new ColumnMeta("store_key", "VARCHAR"));
        ColumnMeta amount = new ColumnMeta("order_amount", "DECIMAL(18,2)");
        amount.setPrecision(18);
        amount.setScale(2);
        table.addColumn(amount);
        return table;
    }

    private JSONObject record(String storeKey, BigDecimal amount) {
        JSONObject record = new JSONObject();
        record.put("order_id", "SO" + System.nanoTime());
        record.put("store_key", storeKey);
        record.put("order_amount", amount);
        return record;
    }
}
