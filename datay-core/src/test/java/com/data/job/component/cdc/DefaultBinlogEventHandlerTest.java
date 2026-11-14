package com.data.job.component.cdc;

import com.alibaba.fastjson2.JSONObject;
import com.data.job.Component;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultBinlogEventHandlerTest {

    private static final String DATABASE = "db";
    private static final String TABLE = "t";

    @Test
    void shouldNotAppendEventMetaWhenDisabled() {
        CapturingComponent component = new CapturingComponent();
        DefaultBinlogEventHandler handler = new DefaultBinlogEventHandler(component, newQueue());
        TableMeta tableMeta = sourceTableMeta();

        BinlogEvent event = insertEvent(1000L, row(1));
        event.setTableMeta(tableMeta);
        handler.handleEvent(event);
        handler.flush();

        assertEquals(1, component.written.size());
        FlowFile flowFile = component.written.get(0);
        JSONObject record = flowFile.getJsonArray().getJSONObject(0);
        assertFalse(record.containsKey(CdcEventMeta.FIELD_EVENT_TYPE));
        assertFalse(record.containsKey(CdcEventMeta.FIELD_TIMESTAMP));

        TableMeta outputMeta = (TableMeta) flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        assertFalse(hasColumn(outputMeta, CdcEventMeta.FIELD_EVENT_TYPE));
        assertFalse(hasColumn(outputMeta, CdcEventMeta.FIELD_TIMESTAMP));
        assertEquals(1, tableMeta.columns().size(), "不应修改采集器缓存的源表元数据");
    }

    @Test
    void shouldAppendEventMetaAndColumnsForInsert() {
        CapturingComponent component = new CapturingComponent();
        DefaultBinlogEventHandler handler = new DefaultBinlogEventHandler(component, newQueue(), true);
        TableMeta tableMeta = sourceTableMeta();

        BinlogEvent event = insertEvent(1000L, row(1));
        event.setTableMeta(tableMeta);
        handler.handleEvent(event);
        handler.flush();

        FlowFile flowFile = component.written.get(0);
        JSONObject record = flowFile.getJsonArray().getJSONObject(0);
        assertEquals("INSERT", record.getString(CdcEventMeta.FIELD_EVENT_TYPE));
        assertEquals(1000L, record.getLongValue(CdcEventMeta.FIELD_TIMESTAMP));

        TableMeta outputMeta = (TableMeta) flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        assertNotNull(outputMeta);
        assertTrue(hasColumn(outputMeta, CdcEventMeta.FIELD_EVENT_TYPE));
        assertTrue(hasColumn(outputMeta, CdcEventMeta.FIELD_TIMESTAMP));
        assertEquals("VARCHAR", column(outputMeta, CdcEventMeta.FIELD_EVENT_TYPE).getType());
        assertEquals("BIGINT", column(outputMeta, CdcEventMeta.FIELD_TIMESTAMP).getType());
        assertEquals(1, tableMeta.columns().size(), "不应修改采集器缓存的源表元数据");
    }

    @Test
    void shouldAppendEventMetaForUpdateAndDelete() {
        CapturingComponent component = new CapturingComponent();
        DefaultBinlogEventHandler handler = new DefaultBinlogEventHandler(component, newQueue(), true);

        BinlogEvent update = new BinlogEvent(BinlogEvent.EventType.UPDATE, DATABASE, TABLE);
        update.setTableMeta(sourceTableMeta());
        update.setTimestamp(2000L);
        update.setBeforeData(row(1));
        update.setAfterData(row(2));
        handler.handleEvent(update);
        handler.flush();

        JSONObject updated = component.written.get(0).getJsonArray().getJSONObject(0);
        assertEquals("UPDATE", updated.getString(CdcEventMeta.FIELD_EVENT_TYPE));
        assertEquals(2000L, updated.getLongValue(CdcEventMeta.FIELD_TIMESTAMP));

        BinlogEvent delete = new BinlogEvent(BinlogEvent.EventType.DELETE, DATABASE, TABLE);
        delete.setTableMeta(sourceTableMeta());
        delete.setTimestamp(3000L);
        delete.setBeforeData(row(3));
        handler.handleEvent(delete);
        handler.flush();

        JSONObject deleted = component.written.get(1).getJsonArray().getJSONObject(0);
        assertEquals("DELETE", deleted.getString(CdcEventMeta.FIELD_EVENT_TYPE));
        assertEquals(3000L, deleted.getLongValue(CdcEventMeta.FIELD_TIMESTAMP));
    }

    @Test
    void shouldUseEachEventTimestampInMergedBatch() {
        CapturingComponent component = new CapturingComponent();
        DefaultBinlogEventHandler handler = new DefaultBinlogEventHandler(component, newQueue(), true);
        TableMeta tableMeta = sourceTableMeta();

        BinlogEvent first = insertEvent(1000L, row(1));
        first.setTableMeta(tableMeta);
        BinlogEvent second = insertEvent(2000L, row(2));
        second.setTableMeta(tableMeta);

        handler.handleEvent(first);
        handler.handleEvent(second);
        handler.flush();

        assertEquals(1, component.written.size());
        assertEquals(2, component.written.get(0).getJsonArray().size());
        assertEquals(1000L, component.written.get(0).getJsonArray().getJSONObject(0).getLongValue(CdcEventMeta.FIELD_TIMESTAMP));
        assertEquals(2000L, component.written.get(0).getJsonArray().getJSONObject(1).getLongValue(CdcEventMeta.FIELD_TIMESTAMP));
    }

    @Test
    void shouldEmitCheckpointWithLastTableMetadata() {
        CapturingComponent component = new CapturingComponent();
        DefaultBinlogEventHandler handler = new DefaultBinlogEventHandler(component, newQueue());

        BinlogEvent event = insertEvent(1000L, row(1));
        event.setTableMeta(sourceTableMeta());
        handler.handleEvent(event);
        handler.flush();
        component.written.clear();

        handler.checkpoint("0x0000002A0000003C0004");

        assertEquals(1, component.written.size());
        FlowFile checkpoint = component.written.get(0);
        assertEquals("0x0000002A0000003C0004", checkpoint.getStatusMap().get("cdc.binlogPosition"));
        assertEquals(TABLE, checkpoint.getAttribute(FlowFile.ATTRIBUTE_TABLE));
        assertEquals(DATABASE, checkpoint.getAttribute(FlowFile.ATTRIBUTE_DATABASE));
        assertTrue(checkpoint.getJsonArray().isEmpty());
    }

    @Test
    void shouldKeepSourceValueWhenColumnNameAlreadyExists() {
        CapturingComponent component = new CapturingComponent();
        DefaultBinlogEventHandler handler = new DefaultBinlogEventHandler(component, newQueue(), true);

        JSONObject sourceRow = row(1);
        sourceRow.put(CdcEventMeta.FIELD_EVENT_TYPE, "CUSTOM");
        sourceRow.put(CdcEventMeta.FIELD_TIMESTAMP, 42L);
        BinlogEvent event = insertEvent(1000L, sourceRow);
        event.setTableMeta(sourceTableMeta());

        handler.handleEvent(event);
        handler.flush();

        JSONObject record = component.written.get(0).getJsonArray().getJSONObject(0);
        assertEquals("CUSTOM", record.getString(CdcEventMeta.FIELD_EVENT_TYPE));
        assertEquals(42L, record.getLongValue(CdcEventMeta.FIELD_TIMESTAMP));
    }

    private static BinlogEvent insertEvent(long timestamp, JSONObject row) {
        BinlogEvent event = new BinlogEvent(BinlogEvent.EventType.INSERT, DATABASE, TABLE);
        event.setTimestamp(timestamp);
        event.setAfterData(row);
        return event;
    }

    private static JSONObject row(int id) {
        JSONObject row = new JSONObject();
        row.put("id", id);
        return row;
    }

    private static TableMeta sourceTableMeta() {
        TableMeta tableMeta = new TableMeta();
        tableMeta.setDbType("mysql");
        tableMeta.setSchema(DATABASE);
        tableMeta.setTable(TABLE);
        tableMeta.addColumn(new ColumnMeta("id", "INT"));
        return tableMeta;
    }

    private static boolean hasColumn(TableMeta tableMeta, String name) {
        return column(tableMeta, name) != null;
    }

    private static ColumnMeta column(TableMeta tableMeta, String name) {
        if (tableMeta == null || tableMeta.columns() == null) {
            return null;
        }
        for (ColumnMeta column : tableMeta.columns()) {
            if (name.equalsIgnoreCase(column.getName())) {
                return column;
            }
        }
        return null;
    }

    private static BlockingQueue<BinlogEvent> newQueue() {
        return new ArrayBlockingQueue<>(100);
    }

    private static class CapturingComponent extends FlowComponent {

        private final List<FlowFile> written = new ArrayList<>();

        CapturingComponent() {
            setId("cdc");
            setType(ComponentType.SOURCE);
        }

        @Override
        public void execute(FlowFile flowFile) {
            // 测试无需实现
        }

        @Override
        public void writeRecords(FlowFile flowFile) {
            written.add(flowFile);
        }
    }
}
