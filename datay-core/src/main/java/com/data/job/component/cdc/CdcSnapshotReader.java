package com.data.job.component.cdc;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * CDC 历史全量快照读取器。
 *
 * <p>在 CDC 增量同步启动之前，对**指定的表范围**做一次一致性读取，并按 {@code INSERT}
 * 事件分批下发到下游，实现“先全量、后增量”的同步。表范围由调用方根据 CDC 的库/表过滤条件解析后传入，
 * 确保快照范围与增量同步范围一致。
 *
 * <p>采用简单一致性策略：读快照前已记录 CDC 起点位点，快照读取在单个
 * {@code REPEATABLE READ} 事务内完成；快照窗口内的变更由后续 CDC 流补放。
 */
public class CdcSnapshotReader {

    /** 快照表引用（schema/database + table）。 */
    public static class TableRef {

        private final String schema;
        private final String table;

        public TableRef(String schema, String table) {
            this.schema = schema;
            this.table = table;
        }

        public String getSchema() {
            return schema;
        }

        public String getTable() {
            return table;
        }
    }

    private final FlowComponent component;
    private final DatasourceInfo datasource;
    private final int fetchSize;

    public CdcSnapshotReader(FlowComponent component, DatasourceInfo datasource, Integer fetchSize) {
        this.component = component;
        this.datasource = datasource;
        this.fetchSize = (fetchSize == null || fetchSize <= 0) ? 10000 : fetchSize;
    }

    /**
     * 按指定表范围执行历史全量快照读取并下发。
     *
     * @param tables 需要快照的表（与 CDC 增量同步范围一致）
     */
    public void snapshot(List<TableRef> tables) throws SQLException {
        if (tables == null || tables.isEmpty()) {
            throw new SQLException("快照表范围为空，请检查数据库/schema 与表过滤配置");
        }
        String dbType = DBUtils.getDBType(datasource.getUrl());

        try (Connection conn = DBUtils.getConnection(datasource)) {
            conn.setAutoCommit(false);
            try {
                conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            } catch (SQLException ignored) {
            }

            TableMeta lastTable = null;
            try {
                for (TableRef ref : tables) {
                    lastTable = snapshotTable(conn, dbType, ref.getSchema(), ref.getTable());
                }
                conn.rollback();
            } catch (SQLException e) {
                try {
                    conn.rollback();
                } catch (SQLException ignored) {
                }
                throw e;
            }

            markSnapshotDone(lastTable);
        }
    }

    private TableMeta snapshotTable(Connection conn, String dbType, String schema, String table) throws SQLException {
        component.logInfo("读取历史全量数据，表 " + schema + "." + table);
        TableMeta tableMeta = DBUtils.getTableMetaData(conn, schema, table);
        tableMeta.setDbType(dbType);

        long total = 0;
        String sql = "SELECT * FROM " + schema + "." + table;
        try (
            Statement stmt = DBUtils.createStreamStatement(dbType, conn);
            ResultSet rs = stmt.executeQuery(sql)
        ) {
            int columnCount = tableMeta.columns().size();
            List<JSONObject> batch = new ArrayList<>(fetchSize);
            while (rs.next()) {
                JSONObject record = new JSONObject();
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = rs.getMetaData().getColumnName(i);
                    record.put(columnName, normalizeValue(rs.getObject(i)));
                }
                batch.add(record);
                total++;
                if (batch.size() >= fetchSize) {
                    emitBatch(tableMeta, batch);
                    batch = new ArrayList<>(fetchSize);
                }
            }
            if (!batch.isEmpty()) {
                emitBatch(tableMeta, batch);
            }
        }
        component.logInfo("历史全量数据读取完成，表 " + schema + "." + table + "，共 " + total + " 条");
        return tableMeta;
    }

    private void emitBatch(TableMeta tableMeta, List<JSONObject> batch) {
        JSONArray records = new JSONArray();
        records.addAll(batch);
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(records);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE, "INSERT");
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, tableMeta);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, tableMeta.getTable());
        flowFile.setAttribute(FlowFile.ATTRIBUTE_DATABASE, tableMeta.getSchema());
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TIMESTAMP, System.currentTimeMillis());
        component.writeRecords(flowFile);
    }

    /**
     * 快照完成后标记状态，避免任务重启重复全量。
     * <p>携带表信息以便下游组件正常处理并持久化该状态。
     */
    private void markSnapshotDone(TableMeta tableMeta) {
        FlowFile marker = new FlowFile();
        marker.setJsonArray(new JSONArray());
        if (tableMeta != null) {
            marker.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, tableMeta);
            marker.setAttribute(FlowFile.ATTRIBUTE_TABLE, tableMeta.getTable());
            marker.setAttribute(FlowFile.ATTRIBUTE_DATABASE, tableMeta.getSchema());
        }
        marker.setStatus(component.getId() + ".snapshotDone", true);
        component.writeRecords(marker);
    }

    /**
     * 将 JDBC 读取值规范化为下游可写入的类型。
     * <p>主要处理 PG 的 {@code uuid}/{@code jsonb}/{@code interval} 等非基础对象，转为其文本表示。
     */
    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof org.postgresql.util.PGobject) {
            return ((org.postgresql.util.PGobject) value).getValue();
        }
        if (value instanceof java.util.UUID) {
            return value.toString();
        }
        if (
            value instanceof String ||
            value instanceof Number ||
            value instanceof Boolean ||
            value instanceof byte[] ||
            value instanceof java.util.Date ||
            value instanceof java.time.temporal.Temporal
        ) {
            return value;
        }
        return value.toString();
    }
}
