package com.data.job.component.cdc;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.datasource.provider.mongodb.MongoConnection;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.DebugMockUtils;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import org.bson.BsonTimestamp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * MongoDB CDC 采集组件。
 *
 * <p>基于 MongoDB Change Streams 实时捕获集合的 {@code INSERT/UPDATE/DELETE} 变更，
 * 作为流式 ETL 的输入源。组件通过官方驱动的 {@code watch} API 读取变更流，无需额外
 * 部署 Kafka/Debezium 等组件。
 *
 * <p>注意：
 * <ul>
 *   <li>Change Streams 依赖<b>副本集</b>或分片集群，单机模式不可用；</li>
 *   <li>逻辑上仅捕获组件启动之后的增量变更，存量数据可通过 {@code snapshot=true} 先全量后增量；</li>
 *   <li>断点续传使用变更流 resumeToken，随数据流持久化，任务重启自动续传。</li>
 * </ul>
 */
@ComponentRegister(
    value = "MongoCDCInput",
    name = "MongoDB CDC",
    group = "实时输入",
    desc = "基于 MongoDB Change Streams 实时捕获集合的 INSERT/UPDATE/DELETE 变更，作为流式 ETL 输入。",
    order = 30
)
public class MongoCDCInput extends FlowComponent {

    /** 配置参数。 */
    private DatasourceInfo datasource;
    private String database;
    /** 单集合名，与 collectionNamePattern 二选一。 */
    private String collection;
    /** 集合名过滤正则，为空表示数据库下所有集合。 */
    private String collectionNamePattern;
    /** 全文档策略：updateLookup（默认，update 事件返回后镜像）、whenAvailable、required。 */
    private String fullDocument;
    /** 变更流阻塞等待时间（毫秒）。 */
    private Long maxAwaitTimeMs;
    /** 从指定 resumeToken（JSON 字符串）续传，为空则从任务状态或当前位置开始。 */
    private String resumeToken;
    /** 从指定 operationTime（epoch 秒）开始采集。 */
    private Long startAtOperationTime;
    /** 是否首次读取历史全量数据（快照）后再启动增量同步。 */
    private Boolean snapshot = false;
    /** 快照分批大小。 */
    private Integer snapshotFetchSize = 10000;
    private Boolean ignoreError = true;

    private final BlockingQueue<BinlogEvent> queue = new ArrayBlockingQueue<>(1000);

    private MongoChangeStreamCollector collector;

    private volatile boolean started = false;

    public MongoCDCInput() {
        setType(ComponentType.SOURCE);
    }

    @Override
    public void execute(FlowFile flowFile) {
        if (getContext() != null && getContext().isDebugMode()) {
            executeDebugMock();
            return;
        }
        if (!started) {
            startCollector();
        } else {
            logInfo("MongoDB 变更流采集器已经在运行中");
        }
    }

    /**
     * 调试模式：生成一条模拟的 INSERT 变更事件并结束。
     */
    private void executeDebugMock() {
        TableMeta tableMeta = resolveDebugTableMeta();
        JSONObject row = DebugMockUtils.sampleRow(tableMeta);
        JSONArray rows = new JSONArray();
        rows.add(row);

        FlowFile mockFlowFile = new FlowFile();
        mockFlowFile.setJsonArray(rows);
        mockFlowFile.setAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE, "INSERT");
        mockFlowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, tableMeta);
        mockFlowFile.setAttribute(FlowFile.ATTRIBUTE_DATABASE, tableMeta.getSchema());
        mockFlowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, tableMeta.getTable());
        mockFlowFile.setAttribute(FlowFile.ATTRIBUTE_TIMESTAMP, System.currentTimeMillis());
        mockFlowFile.setAttribute("_debugMock", true);
        writeRecords(mockFlowFile);
        logInfo("调试模式：根据组件能力生成模拟 CDC INSERT 事件，集合 " + tableMeta.getSchema() + "." + tableMeta.getTable());
    }

    /**
     * 尝试读取源库真实集合结构用于生成模拟数据，失败时使用占位结构。
     */
    private TableMeta resolveDebugTableMeta() {
        if (datasource == null || (datasource.getUrl() == null && datasource.getHostname() == null)) {
            return DebugMockUtils.placeholderTableMeta(null, null);
        }
        try (MongoConnection conn = new MongoConnection(datasource)) {
            String db = conn.databaseName(database);
            String target = collection;
            if (target == null || target.trim().isEmpty()) {
                List<String> names = conn.listCollections(database);
                for (String name : names) {
                    if (DebugMockUtils.matchesPattern(collectionNamePattern, name)) {
                        target = name;
                        break;
                    }
                }
                if (target == null && !names.isEmpty()) {
                    target = names.get(0);
                }
            }
            if (target != null) {
                return conn.sampleTableMeta(db, target, 100);
            }
        } catch (Exception e) {
            logWarn("调试模式：读取源集合结构失败，使用内置模拟结构：" + e.getMessage());
        }
        return DebugMockUtils.placeholderTableMeta(database, null);
    }

    /**
     * 启动变更流采集器（阻塞当前源线程）。
     */
    private void startCollector() {
        try {
            String resume = (resumeToken != null && !resumeToken.trim().isEmpty()) ? resumeToken.trim() : null;
            if (resume == null) {
                Object statusToken = getStatus("resumeToken");
                if (statusToken != null && !statusToken.toString().trim().isEmpty()) {
                    resume = statusToken.toString();
                }
            }
            long maxAwait = (maxAwaitTimeMs != null && maxAwaitTimeMs > 0) ? maxAwaitTimeMs : 1000L;

            BinlogEventHandler eventHandler = new DefaultBinlogEventHandler(this, queue);
            collector = new MongoChangeStreamCollector(
                datasource,
                database,
                collection,
                collectionNamePattern,
                fullDocument,
                maxAwait,
                resume,
                eventHandler
            );
            started = true;

            boolean hasCheckpoint = resume != null;
            if (Boolean.TRUE.equals(snapshot) && !hasCheckpoint && getStatus("snapshotDone") == null) {
                org.bson.BsonDocument startToken = collector.prepare();
                if (startToken != null && !startToken.isEmpty()) {
                    collector.setStartResumeToken(startToken);
                } else {
                    logWarn("未能获取变更流起始 resumeToken，全量快照与增量交界处可能存在重复或遗漏");
                }
                logInfo("开始读取历史全量数据（snapshot），集合范围=" + collector.getTargetTables());
                new MongoSnapshotReader(this, datasource, snapshotFetchSize).snapshot(collector.getTargetTables());
                logInfo("历史全量数据读取完成，启动 CDC 增量同步");
            } else if (startAtOperationTime != null) {
                collector.setStartAtOperationTime(new BsonTimestamp(startAtOperationTime.intValue(), 1));
            }

            collector.start();
        } catch (Exception e) {
            started = false;
            if (Boolean.FALSE.equals(ignoreError)) {
                throw new RuntimeException("启动 MongoDB CDC 采集器失败", e);
            }
            logError("启动 MongoDB CDC 采集器失败: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        if (collector != null && collector.isRunning()) {
            collector.stop();
            started = false;
        }
        super.stop();
    }

    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    /** 兼容前端数据源选择器输出的 {@code schema} 字段。 */
    public void setSchema(String schema) {
        this.database = schema;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public void setCollectionNamePattern(String collectionNamePattern) {
        this.collectionNamePattern = collectionNamePattern;
    }

    public void setFullDocument(String fullDocument) {
        this.fullDocument = fullDocument;
    }

    public void setMaxAwaitTimeMs(Long maxAwaitTimeMs) {
        this.maxAwaitTimeMs = maxAwaitTimeMs;
    }

    public void setResumeToken(String resumeToken) {
        this.resumeToken = resumeToken;
    }

    public void setStartAtOperationTime(Long startAtOperationTime) {
        this.startAtOperationTime = startAtOperationTime;
    }

    public void setSnapshot(Boolean snapshot) {
        this.snapshot = snapshot;
    }

    public void setSnapshotFetchSize(Integer snapshotFetchSize) {
        this.snapshotFetchSize = snapshotFetchSize;
    }

    public void setIgnoreError(Boolean ignoreError) {
        this.ignoreError = ignoreError;
    }

    public boolean isStarted() {
        return started;
    }
}
