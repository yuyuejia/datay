package com.data.job.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.alibaba.fastjson2.JSONObject;
import com.data.metadata.TableMeta;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 验证增量水位合并：增量字段为 {@code null} 时不能写入 ConcurrentHashMap 水位。
 */
class AbstractJdbcInputIncrWatermarkTest {

    private final TestInput input = new TestInput();

    @Test
    void nullIncrValueDoesNotUpdateWatermark() {
        input.mergeLastIncrValue("t_order", null, Types.BIGINT);

        assertNull(input.getLastIncrValue("t_order"));
    }

    @Test
    void watermarkKeepsMaxValueAndIgnoresNull() {
        input.mergeLastIncrValue("t_order", 5L, Types.BIGINT);
        assertEquals(5L, input.getLastIncrValue("t_order"));

        // 较小值不回退水位
        input.mergeLastIncrValue("t_order", 3L, Types.BIGINT);
        assertEquals(5L, input.getLastIncrValue("t_order"));

        // null 不更新水位
        input.mergeLastIncrValue("t_order", null, Types.BIGINT);
        assertEquals(5L, input.getLastIncrValue("t_order"));

        // 较大值推进水位
        input.mergeLastIncrValue("t_order", 9L, Types.BIGINT);
        assertEquals(9L, input.getLastIncrValue("t_order"));
    }

    private static final class TestInput extends AbstractJdbcInput {

        @Override
        protected void emitBatch(List<JSONObject> batchRecords, TableMeta srcTable) throws SQLException {
            // 本测试只验证水位合并逻辑
        }
    }
}
