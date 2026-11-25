package com.data.job.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.data.metadata.ColumnMeta;
import org.junit.jupiter.api.Test;

/**
 * {@link TablePartitionPlanner} 分区方案测试。
 */
class TablePartitionPlannerTest {

    @Test
    void numericPlanCoversRangeWithoutOverlap() {
        ColumnMeta column = new ColumnMeta("id", "INT");
        TablePartitionPlanner.Plan plan = TablePartitionPlanner.plan(column, 1, 100, 4);

        assertTrue(plan.isPartitionable());
        assertEquals(4, plan.size());
        assertEquals("id >= 1 AND id < 25", plan.predicate(0));
        assertEquals("id >= 25 AND id < 49", plan.predicate(1));
        assertEquals("id >= 49 AND id < 73", plan.predicate(2));
        assertEquals("id >= 73 AND id <= 100", plan.predicate(3));
        assertNull(plan.predicate(4));
        assertNull(plan.predicate(-1));
    }

    @Test
    void numericPlanShrinksWhenRangeSmallerThanPartitionCount() {
        ColumnMeta column = new ColumnMeta("id", "BIGINT");
        TablePartitionPlanner.Plan plan = TablePartitionPlanner.plan(column, 1, 3, 5);

        assertEquals(2, plan.size());
        assertEquals("id >= 1 AND id < 2", plan.predicate(0));
        assertEquals("id >= 2 AND id <= 3", plan.predicate(1));
    }

    @Test
    void numericPlanRejectsSingleValue() {
        ColumnMeta column = new ColumnMeta("id", "INT");
        assertFalse(TablePartitionPlanner.plan(column, 7, 7, 4).isPartitionable());
    }

    @Test
    void charPlanUsesFirstCharacterRange() {
        ColumnMeta column = new ColumnMeta("code", "VARCHAR");
        TablePartitionPlanner.Plan plan = TablePartitionPlanner.plan(column, "a", "z", 4);

        assertEquals(4, plan.size());
        assertEquals("code >= 'a' AND code < 'g'", plan.predicate(0));
        assertEquals("code >= 's' AND code <= 'z'", plan.predicate(3));
    }

    @Test
    void numericStringPlanKeepsOriginalWidth() {
        ColumnMeta column = new ColumnMeta("code", "VARCHAR");
        TablePartitionPlanner.Plan plan = TablePartitionPlanner.plan(column, "0001", "0100", 4);

        assertEquals(4, plan.size());
        assertEquals("code >= '0001' AND code < '0025'", plan.predicate(0));
        assertEquals("code >= '0073' AND code <= '0100'", plan.predicate(3));
    }

    @Test
    void unsupportedTypeIsNotPartitionable() {
        ColumnMeta column = new ColumnMeta("create_time", "TIMESTAMP");
        assertFalse(TablePartitionPlanner.supports(column));
        assertFalse(TablePartitionPlanner.plan(column, "2026-01-01", "2026-12-31", 4).isPartitionable());
    }

    @Test
    void numericAndStringTypesAreSupported() {
        assertTrue(TablePartitionPlanner.supports(new ColumnMeta("id", "INT")));
        assertTrue(TablePartitionPlanner.supports(new ColumnMeta("id", "BIGINT")));
        assertTrue(TablePartitionPlanner.supports(new ColumnMeta("id", "DECIMAL(18,2)")));
        assertTrue(TablePartitionPlanner.supports(new ColumnMeta("id", "VARCHAR(64)")));
        assertTrue(TablePartitionPlanner.supports(new ColumnMeta("id", "CHAR(2)")));
    }
}
