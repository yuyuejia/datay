package com.data.job.component.arrow;

import com.alibaba.fastjson2.JSONObject;
import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.BitVector;
import org.apache.arrow.vector.DateDayVector;
import org.apache.arrow.vector.DecimalVector;
import org.apache.arrow.vector.IntVector;
import org.apache.arrow.vector.TimeMicroVector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.util.Text;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Arrow RecordBatch 类型转换测试。
 */
class ArrowRecordConverterTest {

    @Test
    void convertsScalarDateAndTimeValues() {
        try (BufferAllocator allocator = new RootAllocator(64L * 1024 * 1024)) {
            IntVector id = new IntVector("id", allocator);
            VarCharVector name = new VarCharVector("name", allocator);
            DecimalVector amount = new DecimalVector("amount", allocator, 10, 2);
            DateDayVector bizDate = new DateDayVector("biz_date", allocator);
            TimeMicroVector bizTime = new TimeMicroVector("biz_time", allocator);
            BitVector enabled = new BitVector("enabled", allocator);

            try (VectorSchemaRoot root = new VectorSchemaRoot(
                Arrays.asList(id, name, amount, bizDate, bizTime, enabled)
            )) {
                id.allocateNew(2);
                name.allocateNew(2);
                amount.allocateNew(2);
                bizDate.allocateNew(2);
                bizTime.allocateNew(2);
                enabled.allocateNew(2);

                id.setSafe(0, 42);
                name.setSafe(0, new Text("订单"));
                amount.setSafe(0, new BigDecimal("12.34"));
                bizDate.setSafe(0, (int) LocalDate.of(2026, 11, 21).toEpochDay());
                bizTime.setSafe(0, LocalTime.of(10, 30, 15).toNanoOfDay() / 1000L);
                enabled.setSafe(0, 1);

                id.setSafe(1, 43);
                name.setNull(1);
                amount.setSafe(1, new BigDecimal("0.50"));
                bizDate.setSafe(1, (int) LocalDate.of(2026, 11, 22).toEpochDay());
                bizTime.setNull(1);
                enabled.setSafe(1, 0);

                root.setRowCount(2);

                JSONObject first = ArrowRecordConverter.toRecord(root, 0);
                assertEquals(42, first.get("id"));
                assertEquals("订单", first.get("name"));
                assertEquals(new BigDecimal("12.34"), first.get("amount"));
                assertEquals(LocalDate.of(2026, 11, 21), first.get("biz_date"));
                assertEquals(LocalTime.of(10, 30, 15), first.get("biz_time"));
                assertEquals(Boolean.TRUE, first.get("enabled"));

                JSONObject second = ArrowRecordConverter.toRecord(root, 1);
                assertNull(second.get("name"));
                assertNull(second.get("biz_time"));
                assertEquals(Boolean.FALSE, second.get("enabled"));
            }
        }
    }

    @Test
    void normalizesTextAndNestedValues() {
        assertEquals("hello", ArrowRecordConverter.normalize(new Text("hello")));
        assertEquals(
            Arrays.asList("a", "b"),
            ArrowRecordConverter.normalize(Arrays.asList(new Text("a"), new Text("b")))
        );
    }
}
