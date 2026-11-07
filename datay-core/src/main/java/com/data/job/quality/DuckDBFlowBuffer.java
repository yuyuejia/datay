package com.data.job.quality;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.FlowFile;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import com.zaxxer.hikari.pool.HikariProxyConnection;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;

/**
 * 将上游 FlowFile 批次持续追加到一张 DuckDB 表。
 *
 * <p>与 {@code DataTransform} 的逐批次临时表不同，本类只在首次写入时建表/清空，后续批次持续追加，
 * 以支持行数、唯一性等需要跨批次统计的数据质量规则。</p>
 */
public class DuckDBFlowBuffer {

    private final String schema;

    private final String table;

    private TableMeta tableMeta;

    private boolean initialized;

    public DuckDBFlowBuffer(String sourceTable) {
        String normalized = sourceTable == null ? "" : sourceTable.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("数据质量检核源表不能为空");
        }
        int index = normalized.lastIndexOf('.');
        if (index > 0) {
            this.schema = normalized.substring(0, index);
            this.table = normalized.substring(index + 1);
        } else {
            this.schema = "main";
            this.table = normalized;
        }
    }

    /**
     * 追加一个 FlowFile 批次。空批次且没有表元数据时会被忽略。
     *
     * @param conn     DuckDB 连接
     * @param flowFile 上游数据
     * @return 是否已创建/初始化目标表
     */
    public synchronized boolean append(Connection conn, FlowFile flowFile) throws SQLException {
        JSONArray records = flowFile == null ? null : flowFile.getJsonArray();
        if (!initialized) {
            if ((records == null || records.isEmpty()) && !hasTableMeta(flowFile)) {
                return false;
            }
            initialize(conn, flowFile);
        }
        if (records == null || records.isEmpty()) {
            return true;
        }
        write(conn, records);
        return true;
    }

    public String qualifiedTable() {
        return schema + "." + table;
    }

    private void initialize(Connection conn, FlowFile flowFile) throws SQLException {
        tableMeta = buildTableMeta(flowFile);
        tableMeta.setDbType("duckdb");
        tableMeta.setSchema(schema);
        tableMeta.setTable(table);
        if (!DBUtils.schemaExists(conn, schema)) {
            DBUtils.execute(conn, DatabaseConverter.generateSchemaDDL(tableMeta));
        }
        if (!DBUtils.tableExists(conn, schema, table)) {
            DBUtils.execute(conn, DatabaseConverter.generateTableDDL("duckdb", tableMeta));
        } else {
            tableMeta = DBUtils.getTableMetaData(conn, schema, table);
            tableMeta.setSchema(schema);
            tableMeta.setTable(table);
            DBUtils.execute(conn, "TRUNCATE TABLE " + qualifiedTable());
        }
        initialized = true;
    }

    private void write(Connection conn, JSONArray records) throws SQLException {
        DuckDBConnection duckDBConnection = (DuckDBConnection) DBUtils.getRawConnection((HikariProxyConnection) conn);
        try (DuckDBAppender appender = duckDBConnection.createAppender(tableMeta.getSchema(), tableMeta.getTable())) {
            for (Object item : records) {
                appender.beginRow();
                JSONObject record = (JSONObject) item;
                for (ColumnMeta column : tableMeta.columns()) {
                    DBUtils.appendValue(appender, column, record.get(column.getName()));
                }
                appender.endRow();
            }
        }
    }

    private boolean hasTableMeta(FlowFile flowFile) {
        if (flowFile == null) {
            return false;
        }
        Object meta = flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        return meta instanceof TableMeta && !((TableMeta) meta).columns().isEmpty();
    }

    private TableMeta buildTableMeta(FlowFile flowFile) {
        Object meta = flowFile.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        TableMeta tableMeta;
        if (meta instanceof TableMeta && !((TableMeta) meta).columns().isEmpty()) {
            TableMeta source = (TableMeta) meta;
            if (source.getDbType() != null && !"duckdb".equalsIgnoreCase(source.getDbType())) {
                tableMeta = DatabaseConverter.convert(source.getDbType(), "duckdb", source);
            } else {
                tableMeta = new TableMeta(source.getTable(), new ArrayList<>(source.columns()));
            }
        } else {
            tableMeta = inferTableMeta(flowFile.getJsonArray());
        }
        return tableMeta;
    }

    private TableMeta inferTableMeta(JSONArray records) {
        TableMeta tableMeta = new TableMeta();
        tableMeta.setDbType("duckdb");
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("上游数据没有表元数据，无法创建数据质量检核临时表");
        }
        JSONObject firstRecord = records.getJSONObject(0);
        Set<String> columnNames = new LinkedHashSet<>(firstRecord.keySet());
        for (String columnName : columnNames) {
            tableMeta.addColumn(new ColumnMeta(columnName, inferColumnType(firstRecord.get(columnName))));
        }
        return tableMeta;
    }

    private String inferColumnType(Object value) {
        if (value == null) {
            return "VARCHAR";
        }
        if (value instanceof Integer || value instanceof Short || value instanceof Byte) {
            return "INTEGER";
        }
        if (value instanceof Long) {
            return "BIGINT";
        }
        if (value instanceof Double || value instanceof Float) {
            return "DOUBLE";
        }
        if (value instanceof Boolean) {
            return "BOOLEAN";
        }
        if (value instanceof java.util.Date) {
            return "TIMESTAMP";
        }
        return "VARCHAR";
    }
}
