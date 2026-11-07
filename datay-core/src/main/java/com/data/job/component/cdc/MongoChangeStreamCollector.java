package com.data.job.component.cdc;

import com.alibaba.fastjson2.JSONObject;
import com.data.datasource.provider.mongodb.MongoConnection;
import com.data.job.DatasourceInfo;
import com.data.metadata.TableMeta;
import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoChangeStreamCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.mongodb.client.model.changestream.FullDocument;
import org.bson.BsonDocument;
import org.bson.BsonTimestamp;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * MongoDB 变更流（Change Streams）采集器。
 *
 * <p>基于官方驱动的 {@code watch} API 实时读取副本集/分片集群的原子级数据变更，
 * 并把 {@code insert/update/replace/delete} 事件转换为通用 {@link BinlogEvent}
 * 交给 {@link BinlogEventHandler} 下发下游。
 *
 * <p>断点续传使用变更流 {@code resumeToken}（以 JSON 字符串持久化），
 * 首次全量快照场景可先用 {@link #prepare()} 获取 {@code operationTime} 作为增量起点。
 *
 * <p>注意：Change Streams 依赖副本集，单机模式（未开启 replSet）不可用。
 */
public class MongoChangeStreamCollector {

    private static final Logger log = LoggerFactory.getLogger(MongoChangeStreamCollector.class);

    private static final int DEFAULT_META_SAMPLE = 100;

    /** 默认最大重连次数：连续失败超过该次数后终止任务。 */
    public static final int DEFAULT_RECONNECT_MAX_ATTEMPTS = 5;

    /** 默认重连间隔（毫秒）。 */
    public static final long DEFAULT_RECONNECT_INTERVAL_MS = 3000L;

    /**
     * 连接稳定判定阈值：连接存活达到该时长后，下一次断线会重新累计重连次数。
     */
    private static final long STABLE_CONNECTION_MILLIS = 10_000L;

    private final DatasourceInfo datasourceInfo;
    private final String database;
    private final String collection;
    private final Pattern collectionNamePattern;
    private final String fullDocument;
    private final long maxAwaitTimeMs;
    private final BinlogEventHandler eventHandler;

    /** 增量起点：优先 resumeAfter（断点续传），否则 startAtOperationTime（快照起点）。 */
    private BsonDocument resumeToken;
    private BsonTimestamp startAtOperationTime;

    /** publication 覆盖的集合（快照范围以此为准）。 */
    private final List<CdcSnapshotReader.TableRef> targetTables = new ArrayList<>();

    /** 集合 -> 表元数据缓存，避免每条事件都采样。 */
    private final Map<String, TableMeta> tableMetaCache = new ConcurrentHashMap<>();

    private volatile boolean running = false;
    private volatile boolean prepared = false;

    /** 是否收到停止请求；用于区分「手动停止」和「连接异常」。 */
    private volatile boolean stopped = false;

    /** 最近一次成功建立变更流的时间；0 表示当前这轮连接未成功建立。 */
    private volatile long connectedAt = 0L;

    /** 连续重连失败的最大次数，超过后任务失败。 */
    private volatile int reconnectMaxAttempts = DEFAULT_RECONNECT_MAX_ATTEMPTS;

    /** 两次重连之间的等待时间（毫秒）。 */
    private volatile long reconnectIntervalMs = DEFAULT_RECONNECT_INTERVAL_MS;

    private MongoConnection connection;
    private MongoChangeStreamCursor<ChangeStreamDocument<Document>> cursor;

    public MongoChangeStreamCollector(
        DatasourceInfo datasourceInfo,
        String database,
        String collection,
        String collectionNamePattern,
        String fullDocument,
        long maxAwaitTimeMs,
        String resumeToken,
        BinlogEventHandler eventHandler
    ) {
        this.datasourceInfo = datasourceInfo;
        this.database = database;
        this.collection = (collection == null || collection.trim().isEmpty()) ? null : collection.trim();
        this.collectionNamePattern = (collectionNamePattern == null || collectionNamePattern.trim().isEmpty())
            ? null
            : Pattern.compile(collectionNamePattern);
        this.fullDocument = fullDocument;
        this.maxAwaitTimeMs = maxAwaitTimeMs <= 0 ? 1000L : maxAwaitTimeMs;
        this.eventHandler = eventHandler;
        if (resumeToken != null && !resumeToken.trim().isEmpty()) {
            try {
                this.resumeToken = BsonDocument.parse(resumeToken.trim());
            } catch (Exception e) {
                log.warn("解析 MongoDB resumeToken 失败，将从当前位置开始采集：{}", e.getMessage());
            }
        }
    }

    /**
     * 预先校验副本集、解析快照集合范围，并返回当前变更流的 resumeToken 作为增量起点。
     * <p>用于「先全量快照、后增量」场景：必须在读取快照前取得起点，保证增量不丢、
     * 且不会重放快照点之前的变更。
     */
    public BsonDocument prepare() {
        try (MongoConnection conn = openConnection()) {
            Document hello = conn.mongoClient().getDatabase("admin").runCommand(new Document("hello", 1));
            if (hello.getString("setName") == null) {
                throw new IllegalStateException(
                    "MongoDB 未启用副本集（replica set），无法使用 Change Streams，请使用副本集或分片集群"
                );
            }
            resolveTargetTables(conn);
            BsonDocument token = captureResumeToken(conn);
            prepared = true;
            log.info("MongoDB CDC 准备完成，集合范围={}，起始 resumeToken={}", targetTables, token);
            return token;
        }
    }

    /**
     * 打开一个临时变更流并推进到当前点，取得可作为增量起点的 resumeToken（优先 postBatchResumeToken）。
     */
    private BsonDocument captureResumeToken(MongoConnection conn) {
        try (MongoChangeStreamCursor<ChangeStreamDocument<Document>> probe = buildWatch(conn, null, null).cursor()) {
            probe.tryNext();
            return probe.getResumeToken();
        } catch (Exception e) {
            log.warn("获取 MongoDB 变更流起始 resumeToken 失败：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 启动变更流采集，该方法会阻塞当前线程直到 {@link #stop()} 被调用。
     *
     * <p>连接失败（初始连接失败或变更流读取中断）会按 {@link #reconnectMaxAttempts}
     * 自动重连；连续失败次数超过上限后抛出 {@link CdcConnectionException}，
     * 由上层 ETL 流程把任务状态置为 {@code FAILED}。手动停止不会触发重连。
     */
    public void start() {
        if (running) {
            log.warn("MongoDB 变更流采集器已经在运行中");
            return;
        }
        if (stopped) {
            return;
        }

        int failedAttempts = 0;
        while (!stopped && !Thread.currentThread().isInterrupted()) {
            running = true;
            connectedAt = 0L;
            try {
                runOnce();
            } catch (Exception e) {
                if (stopped) {
                    break;
                }
                if (!CdcConnectionErrors.isConnectionFailure(e)) {
                    running = false;
                    throw new RuntimeException("启动 MongoDB 变更流采集器失败: " + e.getMessage(), e);
                }
                log.error("MongoDB 变更流连接异常: {}", e.getMessage(), e);
            }

            if (stopped) {
                break;
            }

            long connectedFor = connectedAt > 0 ? System.currentTimeMillis() - connectedAt : 0L;
            if (connectedAt > 0 && connectedFor >= STABLE_CONNECTION_MILLIS) {
                failedAttempts = 0;
            }
            failedAttempts++;

            if (failedAttempts > reconnectMaxAttempts) {
                running = false;
                int performedReconnects = Math.max(0, failedAttempts - 1);
                String reason =
                    "MongoDB 变更流连接异常，重连 " +
                    performedReconnects +
                    " 次后仍未恢复（最大重连次数 " +
                    reconnectMaxAttempts +
                    "），任务终止";
                log.error(reason);
                throw new CdcConnectionException(reason);
            }

            long waitMillis = Math.max(0L, reconnectIntervalMs);
            log.warn(
                "MongoDB 变更流连接已断开，{} ms 后进行第 {}/{} 次重连",
                waitMillis,
                failedAttempts,
                reconnectMaxAttempts
            );
            if (!sleepBeforeReconnect(waitMillis)) {
                break;
            }
        }

        if (stopped || Thread.currentThread().isInterrupted()) {
            running = false;
        }
    }

    /**
     * 建立一次变更流并读取事件，直到连接中断或收到停止请求。
     */
    private void runOnce() {
        try {
            connection = openConnection();
            if (!prepared) {
                resolveTargetTables(connection);
                prepared = true;
            }
            cursor = buildWatch(connection).cursor();
            connectedAt = System.currentTimeMillis();
            if (resumeToken != null) {
                log.info("MongoDB 变更流已启动，从 resumeToken 续传，集合范围={}", targetTables);
            } else if (startAtOperationTime != null) {
                log.info("MongoDB 变更流已启动，从 operationTime={} 开始，集合范围={}", startAtOperationTime, targetTables);
            } else {
                log.info("MongoDB 变更流已启动，从当前位置开始，集合范围={}", targetTables);
            }
            readLoop();
        } finally {
            closeQuietly();
        }
    }

    /**
     * 重连等待，期间可被 {@link #stop()} 快速打断。
     *
     * @return true 表示等待结束且未收到停止请求
     */
    private boolean sleepBeforeReconnect(long millis) {
        long remaining = millis;
        while (remaining > 0 && !stopped) {
            long sleep = Math.min(remaining, 200L);
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            remaining -= sleep;
        }
        return !stopped;
    }

    private void readLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            ChangeStreamDocument<Document> change = cursor.tryNext();
            if (change == null) {
                // 变更流无事务提交概念，空闲时刷新缓冲区，把已合并的事件下发下游
                eventHandler.flush();
                continue;
            }
            try {
                dispatch(change);
            } catch (Exception e) {
                log.error("处理 MongoDB 变更事件失败：{}，事件类型={}", e.getMessage(), change.getOperationTypeString(), e);
                eventHandler.handleError(null, e);
            }
        }
        log.info("MongoDB 变更流读取循环结束");
    }

    private void dispatch(ChangeStreamDocument<Document> change) {
        String operationType = change.getOperationTypeString();
        if (change.getNamespace() == null) {
            log.warn("捕获到无命名空间的变更事件，类型={}，已跳过", operationType);
            return;
        }
        String db = change.getNamespace().getDatabaseName();
        String coll = change.getNamespace().getCollectionName();
        String token = change.getResumeToken() == null ? null : change.getResumeToken().toJson();

        switch (operationType) {
            case "insert":
                handleEvent(BinlogEvent.EventType.INSERT, db, coll, change.getFullDocument(), null, change, token);
                break;
            case "update":
            case "replace":
                handleEvent(BinlogEvent.EventType.UPDATE, db, coll, change.getFullDocument(), null, change, token);
                break;
            case "delete":
                Document key = change.getDocumentKey() == null ? null : Document.parse(change.getDocumentKey().toJson());
                handleEvent(BinlogEvent.EventType.DELETE, db, coll, null, key, change, token);
                break;
            case "drop":
            case "rename":
            case "dropDatabase":
            case "invalidate":
                log.warn("捕获到结构类变更事件 {}.{}，类型={}，Change Streams 模式下暂不自动同步该操作", db, coll, operationType);
                break;
            default:
                log.debug("忽略未支持的 MongoDB 变更事件类型：{}", operationType);
        }
        // 记录已消费事件的 resumeToken，连接中断后可从该位点继续，避免重复消费
        if (change.getResumeToken() != null && !change.getResumeToken().isEmpty()) {
            resumeToken = change.getResumeToken();
        }
    }

    private void handleEvent(
        BinlogEvent.EventType eventType,
        String db,
        String coll,
        Document after,
        Document before,
        ChangeStreamDocument<Document> change,
        String token
    ) {
        BinlogEvent event = new BinlogEvent(eventType, db, coll);
        event.setTimestamp(resolveTimestamp(change));
        event.setResumeToken(token);
        event.setTableMeta(resolveTableMeta(db, coll));
        if (after != null) {
            event.setAfterData(new JSONObject(MongoConnection.normalizeDocument(after)));
        }
        if (before != null) {
            event.setBeforeData(new JSONObject(MongoConnection.normalizeDocument(before)));
        }
        eventHandler.handleEvent(event);
    }

    private long resolveTimestamp(ChangeStreamDocument<Document> change) {
        BsonTimestamp clusterTime = change.getClusterTime();
        if (clusterTime != null) {
            return clusterTime.getTime() * 1000L;
        }
        return System.currentTimeMillis();
    }

    private TableMeta resolveTableMeta(String db, String coll) {
        return tableMetaCache.computeIfAbsent(db + "." + coll, key -> {
            try {
                return connection.sampleTableMeta(db, coll, DEFAULT_META_SAMPLE);
            } catch (Exception e) {
                log.warn("采样集合 {}.{} 元数据失败：{}", db, coll, e.getMessage());
                return null;
            }
        });
    }

    /**
     * 构建变更流：单集合使用 {@code collection.watch}，多集合使用 {@code database.watch}
     * 并按 {@code ns.coll} 正则过滤。
     */
    private ChangeStreamIterable<Document> buildWatch(MongoConnection conn) {
        return buildWatch(conn, resumeToken, startAtOperationTime);
    }

    private ChangeStreamIterable<Document> buildWatch(MongoConnection conn, BsonDocument resumeAfter, BsonTimestamp startAt) {
        ChangeStreamIterable<Document> iterable;
        if (collection != null) {
            iterable = conn.collection(database, collection).watch();
        } else {
            MongoDatabase db = conn.mongoClient().getDatabase(conn.databaseName(database));
            List<Bson> pipeline = new ArrayList<>();
            if (collectionNamePattern != null) {
                pipeline.add(Aggregates.match(Filters.regex("ns.coll", collectionNamePattern.pattern())));
            }
            iterable = db.watch(pipeline);
        }

        if (fullDocument == null || fullDocument.trim().isEmpty() || "updateLookup".equalsIgnoreCase(fullDocument.trim())) {
            iterable = iterable.fullDocument(FullDocument.UPDATE_LOOKUP);
        } else if ("whenAvailable".equalsIgnoreCase(fullDocument.trim())) {
            iterable = iterable.fullDocument(FullDocument.WHEN_AVAILABLE);
        } else if ("required".equalsIgnoreCase(fullDocument.trim())) {
            iterable = iterable.fullDocument(FullDocument.REQUIRED);
        }
        iterable = iterable.maxAwaitTime(maxAwaitTimeMs, TimeUnit.MILLISECONDS);
        if (resumeAfter != null) {
            iterable = iterable.resumeAfter(resumeAfter);
        } else if (startAt != null) {
            iterable = iterable.startAtOperationTime(startAt);
        }
        return iterable;
    }

    /**
     * 解析快照集合范围，与增量同步范围保持一致。
     */
    private void resolveTargetTables(MongoConnection conn) {
        targetTables.clear();
        String db = conn.databaseName(database);
        if (collection != null) {
            targetTables.add(new CdcSnapshotReader.TableRef(db, collection));
            return;
        }
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String name : conn.listCollections(database)) {
            if (collectionNamePattern == null || collectionNamePattern.matcher(name).matches()) {
                names.add(name);
            }
        }
        for (String name : names) {
            targetTables.add(new CdcSnapshotReader.TableRef(db, name));
        }
        if (targetTables.isEmpty()) {
            throw new IllegalStateException("未找到匹配的集合，请检查 database / collection / collectionNamePattern 配置");
        }
    }

    /** 设置快照后的增量起点（resumeToken）；仅当当前尚无断点时生效。 */
    public void setStartResumeToken(BsonDocument token) {
        if (this.resumeToken == null && token != null && !token.isEmpty()) {
            this.resumeToken = token;
        }
    }

    /** 设置增量起点（operationTime）；仅当当前尚无断点时生效。 */
    public void setStartAtOperationTime(BsonTimestamp startAtOperationTime) {
        if (this.resumeToken == null) {
            this.startAtOperationTime = startAtOperationTime;
        }
    }

    /**
     * 返回快照覆盖的集合范围（须先调用 {@link #prepare()}）。
     */
    public List<CdcSnapshotReader.TableRef> getTargetTables() {
        return new ArrayList<>(targetTables);
    }

    public boolean isRunning() {
        return running;
    }

    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        running = false;
        try {
            eventHandler.flush();
        } catch (Exception e) {
            log.warn("停止时刷新事件缓冲失败：{}", e.getMessage());
        }
        closeQuietly();
        log.info("MongoDB 变更流采集器已停止");
    }

    /**
     * 设置连续重连失败的最大次数，超过后 {@link #start()} 抛出连接异常。
     */
    public void setReconnectMaxAttempts(int maxAttempts) {
        if (maxAttempts < 0) {
            throw new IllegalArgumentException("reconnectMaxAttempts 不能小于 0，当前值: " + maxAttempts);
        }
        this.reconnectMaxAttempts = maxAttempts;
    }

    /**
     * 设置两次重连之间的等待时间（毫秒），小于 0 时按 0 处理。
     */
    public void setReconnectIntervalMs(long intervalMs) {
        this.reconnectIntervalMs = Math.max(0L, intervalMs);
    }

    public int getReconnectMaxAttempts() {
        return reconnectMaxAttempts;
    }

    public long getReconnectIntervalMs() {
        return reconnectIntervalMs;
    }

    private MongoConnection openConnection() {
        return new MongoConnection(datasourceInfo);
    }

    private void closeQuietly() {
        if (cursor != null) {
            try {
                cursor.close();
            } catch (Exception ignored) {
            }
            cursor = null;
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }
            connection = null;
        }
    }
}
