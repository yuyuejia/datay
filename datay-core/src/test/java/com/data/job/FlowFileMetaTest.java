package com.data.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.Test;

class FlowFileMetaTest {

    @Test
    void upsertAppendsNewColumnAndUpdatesExisting() {
        TableMeta meta = new TableMeta("t");
        meta.addColumn(new ColumnMeta("id", "INTEGER"));
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, meta);

        flowFile.upsertColumnMeta("amount", "DOUBLE");
        flowFile.upsertColumnMeta("id", "BIGINT");

        TableMeta result = (TableMeta) flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        assertEquals(2, result.columns().size());
        assertEquals("id", result.columns().get(0).getName());
        assertEquals("BIGINT", result.columns().get(0).getType());
        assertEquals("amount", result.columns().get(1).getName());
        assertEquals("DOUBLE", result.columns().get(1).getType());
    }

    @Test
    void upsertIgnoredWhenNoMetadata() {
        FlowFile flowFile = new FlowFile();

        flowFile.upsertColumnMeta("amount", "DOUBLE");

        assertNull(flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA));
    }

    @Test
    void renameAndRemoveColumnMeta() {
        TableMeta meta = new TableMeta("t");
        meta.addColumn(new ColumnMeta("a", "VARCHAR"));
        meta.addColumn(new ColumnMeta("b", "VARCHAR"));
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, meta);

        flowFile.renameColumnMeta("a", "c");
        flowFile.removeColumnMeta("b");

        TableMeta result = (TableMeta) flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        assertEquals(1, result.columns().size());
        assertEquals("c", result.columns().get(0).getName());
    }
}
