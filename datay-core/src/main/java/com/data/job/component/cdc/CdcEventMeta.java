package com.data.job.component.cdc;

import com.data.metadata.ColumnMeta;
import com.data.metadata.IndexMeta;
import com.data.metadata.TableMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * CDC 事件元数据字段工具。
 *
 * <p>当组件开启“输出事件元数据”选项时，在数据行中追加事件类型与事件时间，
 * 并同步扩展表元数据，保证下游按元数据建表时包含对应列。字段名统一使用小写。
 */
public final class CdcEventMeta {

    /** 数据行中的事件类型字段。 */
    public static final String FIELD_EVENT_TYPE = "_event_type";

    /** 数据行中的事件时间字段（毫秒时间戳）。 */
    public static final String FIELD_TIMESTAMP = "_timestamp";

    private static final String EVENT_TYPE_COLUMN_TYPE = "VARCHAR";
    private static final int EVENT_TYPE_COLUMN_LENGTH = 20;
    private static final String TIMESTAMP_COLUMN_TYPE = "BIGINT";

    private CdcEventMeta() {}

    /**
     * 在数据行中追加事件类型和时间戳。
     *
     * <p>如果源表本身已经包含同名字段，则保留源值，不覆盖业务数据。
     *
     * @param record    待输出的记录
     * @param eventType 事件类型，如 INSERT / UPDATE / DELETE
     * @param timestamp Binlog 事件时间（毫秒）；为 null 时不写入该字段
     */
    public static void appendRecord(Map<String, Object> record, String eventType, Long timestamp) {
        if (record == null) {
            return;
        }
        if (eventType != null) {
            record.putIfAbsent(FIELD_EVENT_TYPE, eventType);
        }
        if (timestamp != null) {
            record.putIfAbsent(FIELD_TIMESTAMP, timestamp);
        }
    }

    /**
     * 复制表元数据并追加事件类型、时间戳列。
     *
     * @param source 源表元数据，可为 null
     * @return 扩展后的表元数据；source 为 null 时返回 null
     */
    public static TableMeta appendColumns(TableMeta source) {
        if (source == null) {
            return null;
        }

        TableMeta copy = new TableMeta();
        copy.setDbType(source.getDbType());
        copy.setCatalog(source.getCatalog());
        copy.setSchema(source.getSchema());
        copy.setTable(source.getTable());
        copy.setComment(source.getComment());

        List<ColumnMeta> sourceColumns = source.columns() == null ? Collections.emptyList() : source.columns();
        copy.setColumns(new ArrayList<>(sourceColumns));
        List<IndexMeta> sourceIndexes = source.getIndexes();
        copy.setIndexes(sourceIndexes == null ? new ArrayList<>() : new ArrayList<>(sourceIndexes));

        if (!hasColumn(sourceColumns, FIELD_EVENT_TYPE)) {
            ColumnMeta eventTypeColumn = new ColumnMeta(FIELD_EVENT_TYPE, EVENT_TYPE_COLUMN_TYPE);
            eventTypeColumn.setLength(EVENT_TYPE_COLUMN_LENGTH);
            copy.addColumn(eventTypeColumn);
        }
        if (!hasColumn(sourceColumns, FIELD_TIMESTAMP)) {
            copy.addColumn(new ColumnMeta(FIELD_TIMESTAMP, TIMESTAMP_COLUMN_TYPE));
        }
        return copy;
    }

    private static boolean hasColumn(List<ColumnMeta> columns, String name) {
        if (columns == null) {
            return false;
        }
        for (ColumnMeta column : columns) {
            if (column != null && column.getName() != null && column.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }
}
