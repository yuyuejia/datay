package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * StreamJdbcInput 分区谓词与 where/增量条件组合测试。
 */
class StreamJdbcInputPartitionQueryTest extends ComponentTestBase {

    private StreamJdbcInput component;
    private TableMeta table;

    @BeforeEach
    void setUp() {
        component = new StreamJdbcInput();
        initComponent(component, "stream-jdbc-partition-query-test", "StreamJdbcInput");

        table = new TableMeta();
        table.setSchema("demo");
        table.setTable("orders");
    }

    @Test
    void appendsPartitionPredicateWhenNoOtherCondition() {
        assertEquals(
            "SELECT * FROM demo.orders WHERE (id >= 1 AND id < 25)",
            component.buildQuery(table, null, new FlowFile(), "id >= 1 AND id < 25")
        );
    }

    @Test
    void combinesWhereIncrementalAndPartitionConditions() {
        component.setWhere("status = 'PAID'");
        component.setLastIncrValue("orders", 100);

        assertEquals(
            "SELECT * FROM demo.orders WHERE status = 'PAID' AND id > 100 AND (id >= 100 AND id < 200)",
            component.buildQuery(table, "id", new FlowFile(), "id >= 100 AND id < 200")
        );
    }

    @Test
    void ignoresBlankPartitionPredicate() {
        assertEquals(
            "SELECT * FROM demo.orders",
            component.buildQuery(table, null, new FlowFile(), "  ")
        );
    }

    @Test
    void parallelBatchesDoNotCarryIncrStatus() {
        StatusCapturingInput input = new StatusCapturingInput();
        initComponent(input, "status-test", "StreamJdbcInput");
        input.setParallelism(4);
        input.setLastIncrValue("orders", 55);

        input.emitBatch(new java.util.ArrayList<>(), table);

        assertEquals(1, input.written.size());
        assertEquals(0, input.written.get(0).getStatusMap().size(), "并发时数据批次不携带增量水位");
    }

    @Test
    void lastWorkerEmitsGlobalIncrStatus() {
        StatusCapturingInput input = new StatusCapturingInput();
        initComponent(input, "status-test", "StreamJdbcInput");
        input.setParallelism(4);
        input.setLastIncrValue("orders", 55);

        input.persistIncrStatus(table, 100);

        assertEquals(1, input.written.size());
        assertEquals(
            "100",
            input.written.get(0).getStatusMap().get("status-test.lastIncrValue.orders")
        );
    }

    @Test
    void singleThreadBatchCarriesIncrStatus() {
        StatusCapturingInput input = new StatusCapturingInput();
        initComponent(input, "status-test", "StreamJdbcInput");
        input.setParallelism(1);
        input.setLastIncrValue("orders", 55);

        input.emitBatch(new java.util.ArrayList<>(), table);

        assertEquals(
            "55",
            input.written.get(0).getStatusMap().get("status-test.lastIncrValue.orders")
        );
    }

    private static class StatusCapturingInput extends StreamJdbcInput {

        private final java.util.List<FlowFile> written = new java.util.ArrayList<>();

        @Override
        public void writeRecords(FlowFile flowFile) {
            written.add(flowFile);
        }
    }
}
