package com.data.job.component.cdc;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.datasource.provider.mongodb.MongoConnection;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import com.mongodb.client.MongoCursor;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MongoDB CDC 历史全量快照读取器。
 *
 * <p>在变更流增量同步启动之前，对指定集合范围做一次全量读取，并按 {@code INSERT}
 * 事件分批下发到下游，实现「先全量、后增量」。表范围由调用方根据
 * {@code database / collection / collectionNamePattern} 解析后传入，与增量范围一致。
 *
 * <p>与关系型快照不同，MongoDB 无固定 schema，这里对每个集合采样推断字段元数据，
 * 供下游自动建表使用。
 */
public class MongoSnapshotReader {

    private final FlowComponent component;
    private final DatasourceInfo datasource;
    private final int fetchSize;
    private final int sampleSize;

    public MongoSnapshotReader(FlowComponent component, DatasourceInfo datasource, Integer fetchSize) {
        this.component = component;
        this.datasource = datasource;
        this.fetchSize = (fetchSize == null || fetchSize <= 0) ? 10000 : fetchSize;
        this.sampleSize = 100;
    }

    /**
     * 按指定集合范围执行历史全量快照读取并下发。
     */
    public void snapshot(List<CdcSnapshotReader.TableRef> tables) {
        if (tables == null || tables.isEmpty()) {
            throw new IllegalStateException("快照集合范围为空，请检查 database / collection / collectionNamePattern 配置");
        }
        TableMeta lastTable = null;
        try (MongoConnection conn = new MongoConnection(datasource)) {
            for (CdcSnapshotReader.TableRef ref : tables) {
                lastTable = snapshotCollection(conn, ref.getSchema(), ref.getTable());
            }
        }
        markSnapshotDone(lastTable);
    }

    private TableMeta snapshotCollection(MongoConnection conn, String database, String collection) {
        component.logInfo("读取历史全量数据，集合 " + database + "." + collection);
        TableMeta tableMeta = conn.sampleTableMeta(database, collection, sampleSize);

        long total = 0;
        List<JSONObject> batch = new ArrayList<>(fetchSize);
        try (MongoCursor<Document> cursor = conn.collection(database, collection).find().batchSize(fetchSize).iterator()) {
            while (cursor.hasNext()) {
                Map<String, Object> row = MongoConnection.normalizeDocument(cursor.next());
                batch.add(new JSONObject(row));
                total++;
                if (batch.size() >= fetchSize) {
                    emitBatch(tableMeta, database, collection, batch);
                    batch = new ArrayList<>(fetchSize);
                }
            }
        }
        if (!batch.isEmpty()) {
            emitBatch(tableMeta, database, collection, batch);
        }
        component.logInfo("历史全量数据读取完成，集合 " + database + "." + collection + "，共 " + total + " 条");
        return tableMeta;
    }

    private void emitBatch(TableMeta tableMeta, String database, String collection, List<JSONObject> batch) {
        JSONArray records = new JSONArray();
        records.addAll(batch);
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(records);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE, "INSERT");
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, tableMeta);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, collection);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_DATABASE, database);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TIMESTAMP, System.currentTimeMillis());
        component.writeRecords(flowFile);
    }

    /**
     * 快照完成后标记状态，避免任务重启重复全量。
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
}
