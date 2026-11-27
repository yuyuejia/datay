package com.data.job.component.cdc;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.datasource.provider.JdbcDataSourceProvider;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.job.util.TablePartitionPlanner;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.TableMeta;
import com.data.metadata.TypeConverter;
import com.data.metadata.util.DBUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * CDC 历史全量快照读取器。
 *
 * <p>在 CDC 增量同步启动之前，对**指定的表范围**做一次全量读取，并按 {@code INSERT}
 * 事件分批下发到下游，实现“先全量、后增量”的同步。表范围由调用方根据 CDC 的库/表过滤条件解析后传入，
 * 确保快照范围与增量同步范围一致。
 *
 * <p>读取策略：
 * <ul>
 *   <li>并发数大于 1 时，优先按单列主键使用 {@link TablePartitionPlanner} 把单表切分为多个区间，
 *       由多个线程并发读取；没有可用分区字段时按表分发，多个表也可以并行读取。
 *       实际工作线程数不超过 {@value #MAX_SNAPSHOT_WORKERS}，避免超过连接池上限。</li>
 *   <li>每个工作线程使用独立连接，按任务提交，不再把所有表包在一个长事务里，
 *       避免 SQL Server 等数据库在快照期间长时间持有共享锁。</li>
 *   <li>快照读取的是“当前已提交数据”的近似快照；读取期间发生的变更由 CDC 从预先记录的
 *       增量起点补放，因此可能出现少量重复，下游按幂等写入处理。</li>
 * </ul>
 */
public class CdcSnapshotReader {

    /** 快照最大工作线程数，避免超过连接池上限后大量线程等待连接超时。 */
    private static final int MAX_SNAPSHOT_WORKERS = 10;

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
    /** 是否在输出数据行中追加事件类型和时间戳。 */
    private final boolean includeEventMeta;
    /** 快照读取并发数，<=1 时串行读取。 */
    private final int parallelism;

    public CdcSnapshotReader(FlowComponent component, DatasourceInfo datasource, Integer fetchSize) {
        this(component, datasource, fetchSize, false, 1);
    }

    public CdcSnapshotReader(FlowComponent component, DatasourceInfo datasource, Integer fetchSize, boolean includeEventMeta) {
        this(component, datasource, fetchSize, includeEventMeta, 1);
    }

    public CdcSnapshotReader(
        FlowComponent component,
        DatasourceInfo datasource,
        Integer fetchSize,
        boolean includeEventMeta,
        int parallelism
    ) {
        this.component = component;
        this.datasource = datasource;
        this.fetchSize = (fetchSize == null || fetchSize <= 0) ? 10000 : fetchSize;
        this.includeEventMeta = includeEventMeta;
        this.parallelism = Math.max(1, parallelism);
    }

    /**
     * 按指定表范围执行历史全量快照读取并下发。
     *
     * @param tables 需要快照的表（与 CDC 增量同步范围一致）
     */
    public void snapshot(List<TableRef> tables) throws SQLException {
        snapshot(tables, Collections.emptyMap());
    }

    /**
     * 按指定表范围执行历史全量快照读取并下发，并把增量起始位点与 {@code snapshotDone}
     * 放进同一个 FlowFile 状态中持久化。
     *
     * <p>快照完成标记与起始位点必须一次保存：否则任务在“快照已完成、但增量位点尚未落盘”
     * 的时间窗口崩溃后，重启会误判为没有位点而从当前位置开始，导致快照完成到重启之间的
     * 变更丢失。</p>
     *
     * @param tables      需要快照的表（与 CDC 增量同步范围一致）
     * @param startStatus 快照对应的增量起始位点，key 不带组件 id 前缀，可为空
     */
    public void snapshot(List<TableRef> tables, Map<String, Object> startStatus) throws SQLException {
        if (tables == null || tables.isEmpty()) {
            throw new SQLException("快照表范围为空，请检查数据库/schema 与表过滤配置");
        }
        String dbType = DBUtils.getDBType(datasource.getUrl());
        JdbcDataSourceProvider provider = DBUtils.resolveJdbcProvider(datasource);
        TypeConverter sourceConverter = DatabaseConverter.getConverter(dbType);

        SnapshotPlan plan;
        // 规划阶段只读取元数据和分区边界，连接可以立即释放
        try (Connection conn = DBUtils.getConnection(datasource)) {
            plan = buildPlan(conn, dbType, provider, sourceConverter, tables);
        }

        executePlan(plan, dbType, provider, sourceConverter);
        markSnapshotDone(plan.lastTable, startStatus);
    }

    /**
     * 读取表元数据并生成快照任务。可分区表按主键切分为多个任务，不可分区表整表一个任务。
     */
    private SnapshotPlan buildPlan(
        Connection conn,
        String dbType,
        JdbcDataSourceProvider provider,
        TypeConverter sourceConverter,
        List<TableRef> tables
    ) throws SQLException {
        List<SnapshotTask> tasks = new ArrayList<>();
        TableMeta lastTable = null;
        for (TableRef ref : tables) {
            TableMeta tableMeta = loadTableMeta(conn, dbType, sourceConverter, ref);
            lastTable = tableMeta;
            TablePartitionPlanner.Plan partitionPlan = resolvePartitionPlan(conn, provider, ref, tableMeta);
            if (!partitionPlan.isPartitionable()) {
                tasks.add(new SnapshotTask(ref.getSchema(), ref.getTable(), tableMeta, null, 0, 0));
                continue;
            }
            component.logInfo(
                "表 " + ref.getSchema() + "." + ref.getTable() + " 使用字段 " +
                partitionPlan.getColumnName() + " 分区，实际分区数 " + partitionPlan.size()
            );
            for (int i = 0; i < partitionPlan.size(); i++) {
                String predicate = partitionPlan.predicate(i);
                if (predicate != null) {
                    tasks.add(new SnapshotTask(ref.getSchema(), ref.getTable(), tableMeta, predicate, i + 1, partitionPlan.size()));
                }
            }
        }
        return new SnapshotPlan(tasks, lastTable);
    }

    private TableMeta loadTableMeta(
        Connection conn,
        String dbType,
        TypeConverter sourceConverter,
        TableRef ref
    ) throws SQLException {
        TableMeta tableMeta = DBUtils.getTableMetaData(conn, ref.getSchema(), ref.getTable());
        tableMeta.setDbType(dbType);
        if (tableMeta.getSchema() == null || tableMeta.getSchema().trim().isEmpty()) {
            tableMeta.setSchema(ref.getSchema());
        }
        if (sourceConverter != null) {
            sourceConverter.enrichSourceTableMeta(conn, ref.getSchema(), ref.getTable(), tableMeta);
        }
        return tableMeta;
    }

    /**
     * 选择快照分区字段：只使用单列主键，避免复合主键在并发区间上切分错误。
     */
    private TablePartitionPlanner.Plan resolvePartitionPlan(
        Connection conn,
        JdbcDataSourceProvider provider,
        TableRef ref,
        TableMeta tableMeta
    ) {
        if (parallelism <= 1) {
            return TablePartitionPlanner.Plan.empty();
        }
        ColumnMeta partitionColumn = resolvePartitionColumn(tableMeta);
        if (partitionColumn == null) {
            component.logInfo("表 " + ref.getTable() + " 没有可用的单列主键，按整表读取");
            return TablePartitionPlanner.Plan.empty();
        }
        try {
            String qualifiedTable = qualifiedTableName(provider, ref.getSchema(), ref.getTable());
            Object[] minMax = TablePartitionPlanner.queryMinMax(conn, qualifiedTable, partitionColumn.getName());
            if (minMax == null) {
                component.logInfo("表 " + ref.getTable() + " 的分区字段 " + partitionColumn.getName() + " 范围为空，按整表读取");
                return TablePartitionPlanner.Plan.empty();
            }
            return TablePartitionPlanner.plan(partitionColumn, minMax[0], minMax[1], parallelism);
        } catch (SQLException e) {
            component.logWarn("获取表 " + ref.getTable() + " 分区范围失败，按整表读取: " + e.getMessage());
            return TablePartitionPlanner.Plan.empty();
        }
    }

    private ColumnMeta resolvePartitionColumn(TableMeta tableMeta) {
        ColumnMeta singlePrimaryKey = null;
        for (ColumnMeta column : tableMeta.columns()) {
            if (!column.isPrimaryKey()) {
                continue;
            }
            if (singlePrimaryKey != null) {
                // 复合主键不分区，避免区间条件无法完整覆盖
                return null;
            }
            singlePrimaryKey = column;
        }
        if (singlePrimaryKey == null || !TablePartitionPlanner.supports(singlePrimaryKey)) {
            return null;
        }
        return singlePrimaryKey;
    }

    /**
     * 执行快照任务。每个工作线程持有独立连接，任务完成后提交，不跨表持有长事务。
     */
    private void executePlan(
        SnapshotPlan plan,
        String dbType,
        JdbcDataSourceProvider provider,
        TypeConverter sourceConverter
    ) throws SQLException {
        if (plan.tasks.isEmpty()) {
            return;
        }

        int workerCount = Math.min(Math.min(parallelism, plan.tasks.size()), MAX_SNAPSHOT_WORKERS);
        if (workerCount <= 1) {
            try (Connection conn = DBUtils.getConnection(datasource)) {
                for (SnapshotTask task : plan.tasks) {
                    executeTask(conn, dbType, provider, sourceConverter, task, null);
                    commitIfNeeded(conn);
                }
            }
            return;
        }

        Queue<SnapshotTask> taskQueue = new ConcurrentLinkedQueue<>(plan.tasks);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        List<Future<?>> futures = new ArrayList<>(workerCount);
        for (int i = 0; i < workerCount; i++) {
            futures.add(executor.submit(() -> {
                try (Connection conn = DBUtils.getConnection(datasource)) {
                    SnapshotTask task;
                    while (failure.get() == null && (task = taskQueue.poll()) != null) {
                        executeTask(conn, dbType, provider, sourceConverter, task, failure);
                        commitIfNeeded(conn);
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            }));
        }
        executor.shutdown();

        try {
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
            throw new SQLException("历史全量数据读取被中断", e);
        } catch (ExecutionException e) {
            failure.compareAndSet(null, e.getCause() == null ? e : e.getCause());
        } finally {
            executor.shutdownNow();
        }

        Throwable error = failure.get();
        if (error == null) {
            return;
        }
        if (error instanceof SQLException sqlException) {
            throw sqlException;
        }
        if (error instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        throw new SQLException("历史全量数据读取失败: " + error.getMessage(), error);
    }

    private void executeTask(
        Connection conn,
        String dbType,
        JdbcDataSourceProvider provider,
        TypeConverter sourceConverter,
        SnapshotTask task,
        AtomicReference<Throwable> failure
    ) throws SQLException {
        TableMeta tableMeta = task.tableMeta;
        String label = task.schema + "." + task.table +
            (task.predicate == null ? "" : "（分区 " + task.partitionIndex + "/" + task.partitionCount + "）");
        component.logInfo("读取历史全量数据，表 " + label);
        long startedAt = System.currentTimeMillis();
        long total = 0;

        String sql = "SELECT * FROM " + qualifiedTableName(provider, task.schema, task.table);
        if (task.predicate != null && !task.predicate.trim().isEmpty()) {
            sql = sql + " WHERE " + task.predicate;
        }

        try (
            Statement stmt = DBUtils.createStreamStatement(dbType, conn);
            ResultSet rs = stmt.executeQuery(sql)
        ) {
            int columnCount = tableMeta.columns().size();
            ResultSetMetaData rsMeta = rs.getMetaData();
            String[] columnNames = new String[columnCount + 1];
            int[] jdbcTypes = new int[columnCount + 1];
            String[] typeNames = new String[columnCount + 1];
            for (int i = 1; i <= columnCount; i++) {
                columnNames[i] = rsMeta.getColumnName(i);
                jdbcTypes[i] = rsMeta.getColumnType(i);
                typeNames[i] = rsMeta.getColumnTypeName(i);
            }
            List<JSONObject> batch = new ArrayList<>(fetchSize);
            while (rs.next()) {
                if (failure != null && failure.get() != null) {
                    throw new SQLException("历史全量数据读取已中止");
                }
                JSONObject record = new JSONObject(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = columnNames[i];
                    ColumnMeta column = tableMeta.columns().get(i - 1);
                    Object value = provider == null
                        ? rs.getObject(i)
                        : provider.readSourceValue(rs, i, jdbcTypes[i], typeNames[i], column);
                    if (sourceConverter != null) {
                        value = sourceConverter.normalizeSourceValue(column, value);
                    }
                    record.put(columnName, normalizeValue(value));
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
        component.logInfo(
            "历史全量数据读取完成，表 " + label + "，共 " + total + " 条，耗时 " +
            (System.currentTimeMillis() - startedAt) + " ms"
        );
    }

    /**
     * 提交当前任务的只读查询事务（部分驱动如 PostgreSQL 会被 {@code createStreamStatement}
     * 自动关闭 autoCommit）。自动提交模式不需要额外处理。
     */
    private void commitIfNeeded(Connection conn) throws SQLException {
        if (!conn.getAutoCommit()) {
            conn.commit();
        }
    }

    private void emitBatch(TableMeta tableMeta, List<JSONObject> batch) {
        // 快照数据不是 Binlog 事件，没有对应的 Binlog 事件时间，因此不写入 _timestamp。
        // FlowFile 的 _timestamp 属性仅表示本次下发时间，供日志/调试使用。
        long processedAt = System.currentTimeMillis();
        if (includeEventMeta) {
            for (JSONObject record : batch) {
                CdcEventMeta.appendRecord(record, "INSERT", null);
            }
        }
        JSONArray records = new JSONArray();
        records.addAll(batch);
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(records);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE, "INSERT");
        flowFile.setAttribute(
            FlowFile.ATTRIBUTE_TABLE_METADATA,
            includeEventMeta ? CdcEventMeta.appendColumns(tableMeta) : tableMeta
        );
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, tableMeta.getTable());
        flowFile.setAttribute(FlowFile.ATTRIBUTE_DATABASE, tableMeta.getSchema());
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TIMESTAMP, processedAt);
        component.writeRecords(flowFile);
    }

    /**
     * 快照完成后标记状态，避免任务重启重复全量。
     * <p>携带表信息以便下游组件正常处理并持久化该状态。起始位点与
     * {@code snapshotDone} 写入同一个 FlowFile，确保下游一次状态保存中同时落盘。
     */
    private void markSnapshotDone(TableMeta tableMeta, Map<String, Object> startStatus) {
        FlowFile marker = new FlowFile();
        marker.setJsonArray(new JSONArray());
        if (tableMeta != null) {
            marker.setAttribute(
                FlowFile.ATTRIBUTE_TABLE_METADATA,
                includeEventMeta ? CdcEventMeta.appendColumns(tableMeta) : tableMeta
            );
            marker.setAttribute(FlowFile.ATTRIBUTE_TABLE, tableMeta.getTable());
            marker.setAttribute(FlowFile.ATTRIBUTE_DATABASE, tableMeta.getSchema());
        }
        if (startStatus != null && !startStatus.isEmpty()) {
            for (Map.Entry<String, Object> entry : startStatus.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    marker.setStatus(component.getId() + "." + entry.getKey(), entry.getValue());
                }
            }
        }
        marker.setStatus(component.getId() + ".snapshotDone", true);
        component.writeRecords(marker);
    }

    /**
     * 构造带数据库方言的表名。数据库特定规则由 {@link JdbcDataSourceProvider#qualifiedTableName}
     * 实现（如 SQL Server 的方括号、DuckLake 的 catalog 前缀），通用组件不按数据库类型分支。
     */
    private String qualifiedTableName(JdbcDataSourceProvider provider, String schema, String table) {
        if (provider != null) {
            return provider.qualifiedTableName(datasource, schema, table);
        }
        return schema == null || schema.trim().isEmpty() ? table : schema + "." + table;
    }

    /**
     * 将 JDBC 读取值规范化为下游可写入的类型。
     * <p>主要处理 PG 的 {@code uuid}/{@code jsonb}/{@code interval} 等非基础对象，转为其文本表示。
     */
    private Object normalizeValue(Object value) {
        if (value == null) {
            return null;
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

    /** 快照计划：任务列表与最后一张表的元数据（用于写 snapshotDone 标记）。 */
    private static final class SnapshotPlan {

        private final List<SnapshotTask> tasks;
        private final TableMeta lastTable;

        private SnapshotPlan(List<SnapshotTask> tasks, TableMeta lastTable) {
            this.tasks = tasks;
            this.lastTable = lastTable;
        }
    }

    /** 单个快照任务：整表或某个主键区间。 */
    private static final class SnapshotTask {

        private final String schema;
        private final String table;
        private final TableMeta tableMeta;
        private final String predicate;
        private final int partitionIndex;
        private final int partitionCount;

        private SnapshotTask(
            String schema,
            String table,
            TableMeta tableMeta,
            String predicate,
            int partitionIndex,
            int partitionCount
        ) {
            this.schema = schema;
            this.table = table;
            this.tableMeta = tableMeta;
            this.predicate = predicate;
            this.partitionIndex = partitionIndex;
            this.partitionCount = partitionCount;
        }
    }
}
