package com.data.job.component;

import com.alibaba.fastjson2.JSONObject;
import com.data.datasource.provider.JdbcDataSourceProvider;
import com.data.expression.ParameterUtil;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.job.util.TablePartitionPlanner;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JDBC 输入组件抽象基类。
 * <p>
 * 负责源库流式读取的公共逻辑：多表解析（含 schema 全表发现）、增量字段与状态、查询构建、
 * 只进游标流式读取与分批、增量值类型化比较。子类只需通过 {@link #emitBatch} 决定每批记录的去向
 * （下沉为 FlowFile 或物化到 DuckDB），并按需覆写增量状态持久化与物化资源准备。
 */
public abstract class AbstractJdbcInput extends FlowComponent {

    protected String schema;
    protected String table;
    protected String incrColumn;
    protected String where;
    protected String ytenantId;
    protected DatasourceInfo datasource;

    // 源端 Provider 缓存，避免逐行解析 JDBC URL
    private JdbcDataSourceProvider sourceProvider;
    private boolean sourceProviderResolved;

    // 流式读取配置
    protected static final int FETCH_SIZE = 10000;

    // 增量同步状态存储（多表支持）：本次运行观察到的最新值
    protected final Map<String, Object> lastIncrValues = new ConcurrentHashMap<>();

    // 本次运行启动时加载的历史增量水位，用于构建查询条件；运行期间不随读取推进
    protected final Map<String, Object> loadedIncrValues = new ConcurrentHashMap<>();

    // 分区方案缓存，避免每个并发线程重复查询 MIN/MAX
    private final Map<String, TablePartitionPlanner.Plan> partitionPlanCache = new ConcurrentHashMap<>();

    // 每张表尚未完成的并发线程数，最后一个完成的线程负责落盘增量水位
    private final Map<String, AtomicInteger> tablePendingWorkers = new ConcurrentHashMap<>();

    // 增量水位是否已加载：并发线程只加载一次，避免读到本次运行中途推进的水位
    private boolean incrStatusLoaded = false;

    // 任一并发线程读取失败时，禁止推进增量水位
    private volatile boolean partitionReadFailed = false;

    protected AbstractJdbcInput() {
        setType(ComponentType.SOURCE);
    }

    /**
     * 子类实现：处理一批记录。
     */
    protected abstract void emitBatch(List<JSONObject> batchRecords, TableMeta srcTable) throws SQLException;

    /**
     * 子类实现：读取完成后持久化增量状态。默认不处理（由子类在下沉数据时随 FlowFile 携带状态）。
     */
    protected void persistIncrStatus(TableMeta srcTable, Object lastIncrValue) {
    }

    /**
     * 无数据时是否发送一个空批次。流式模式需要，以便下游感知表边界；物化模式不需要。
     */
    protected boolean emitEmptyBatch() {
        return false;
    }

    /**
     * 处理开始前的钩子，便于子类准备额外资源（如打开 DuckDB 连接）。
     */
    protected void beforeProcess() throws SQLException {
    }

    /**
     * 处理结束后的钩子，便于子类释放额外资源。
     */
    protected void afterProcess() throws SQLException {
    }

    /**
     * 读取单表前的钩子，便于子类准备目标资源（如解析 DuckDB 目标表元数据）。
     */
    protected void beforeMigrateTable(TableMeta srcTable) throws SQLException {
    }

    @Override
    public void execute(FlowFile flowFile) {
        List<String> tables = parseTables();
        Map<String, String> incrColumns = parseIncrColumns(tables);

        // 加载增量同步状态
        loadIncrStatus(incrColumns);

        try (Connection srcConn = getSourceConnection()) {
            beforeProcess();
            try {
                String sourceDBType = DBUtils.getDBType(datasource.getType(), datasource.getUrl());
                for (int tableIndex = 0; tableIndex < tables.size(); tableIndex++) {
                    String tableName = tables.get(tableIndex);
                    TableMeta srcTable = DBUtils.getTableMetaData(srcConn, resolveSchema(), tableName.trim());
                    srcTable.setDbType(sourceDBType);

                    String tableIncrColumn = incrColumns.get(tableName.trim());
                    boolean success = false;
                    try {
                        // 调试模式仅保留 0 号线程采样，避免并发线程重复采样
                        if (isDebugMode() && getParallelism() > 1 && getWorkerIndex() != 0) {
                            logInfo("调试模式：当前线程 " + getWorkerIndex() + " 不参与采样，表 " + tableName);
                            success = true;
                            continue;
                        }
                        TablePartitionPlanner.Plan plan = resolvePartitionPlan(srcConn, srcTable, tableIncrColumn);
                        if (plan.isPartitionable()) {
                            String partitionPredicate = plan.predicate(getWorkerIndex());
                            if (partitionPredicate == null) {
                                logInfo("表 " + tableName + " 实际分区数 " + plan.size()
                                    + " 小于并发数 " + getParallelism()
                                    + "，当前线程 " + getWorkerIndex() + " 没有对应分区，跳过");
                                success = true;
                                continue;
                            }
                            beforeMigrateTable(srcTable);
                            migrateData(srcConn, srcTable, tableIncrColumn, flowFile, partitionPredicate);
                        } else if (shouldHandleWholeTable(tableIndex)) {
                            beforeMigrateTable(srcTable);
                            migrateData(srcConn, srcTable, tableIncrColumn, flowFile);
                        } else {
                            logInfo("表 " + tableName + " 不可分区，由线程 "
                                + (tableIndex % Math.max(1, getParallelism())) + " 读取，当前线程 "
                                + getWorkerIndex() + " 跳过");
                        }
                        success = true;
                    } catch (SQLException e) {
                        logInfo("处理表 " + tableName + " 时发生错误: " + e.getMessage());
                        throw new RuntimeException("数据迁移失败", e);
                    } finally {
                        finishTable(srcTable, success);
                    }
                }
            } finally {
                afterProcess();
            }
        } catch (SQLException e) {
            logInfo("数据库连接失败: " + e.getMessage());
            throw new RuntimeException("数据迁移失败", e);
        }
    }

    /**
     * 判断当前线程是否负责整表读取（表不可分区时的兜底：按表序号分发）。
     */
    private boolean shouldHandleWholeTable(int tableIndex) {
        int parallelism = Math.max(1, getParallelism());
        return parallelism <= 1 || tableIndex % parallelism == getWorkerIndex();
    }

    /**
     * 解析当前表的分区方案。
     * <p>并发数大于 1 时自动尝试分区，分区字段按默认策略选择：优先增量字段，其次单列主键；
     * 并发数为 1、调试模式或没有可用分区字段时返回空方案（由调用方回退为普通查询 / 按表分发）。</p>
     */
    private TablePartitionPlanner.Plan resolvePartitionPlan(Connection conn, TableMeta srcTable, String tableIncrColumn) {
        if (getParallelism() <= 1 || isDebugMode()) {
            return TablePartitionPlanner.Plan.empty();
        }
        ColumnMeta column = resolvePartitionColumn(srcTable, tableIncrColumn);
        if (column == null) {
            logInfo("表 " + srcTable.getTable() + " 没有可用的分区字段，使用普通查询");
            return TablePartitionPlanner.Plan.empty();
        }
        String qualifiedTable = qualifyTable(srcTable);
        String cacheKey = qualifiedTable + "|" + column.getName() + "|" + getParallelism();
        return partitionPlanCache.computeIfAbsent(cacheKey, key -> {
            try {
                Object[] minMax = TablePartitionPlanner.queryMinMax(conn, qualifiedTable, column.getName());
                if (minMax == null) {
                    logInfo("表 " + srcTable.getTable() + " 的分区字段 " + column.getName() + " 范围为空，使用普通查询");
                    return TablePartitionPlanner.Plan.empty();
                }
                TablePartitionPlanner.Plan plan =
                    TablePartitionPlanner.plan(column, minMax[0], minMax[1], getParallelism());
                logInfo("表 " + srcTable.getTable() + " 使用字段 " + column.getName()
                    + " 分区，范围 " + minMax[0] + " - " + minMax[1]
                    + "，实际分区数 " + plan.size());
                return plan;
            } catch (SQLException e) {
                logInfo("获取表 " + srcTable.getTable() + " 分区范围失败: " + e.getMessage());
                return TablePartitionPlanner.Plan.empty();
            }
        });
    }

    /**
     * 按默认策略选择分区字段：优先增量字段，其次单列主键；没有可用字段时返回 null。
     */
    private ColumnMeta resolvePartitionColumn(TableMeta srcTable, String tableIncrColumn) {
        if (tableIncrColumn != null && !tableIncrColumn.trim().isEmpty()) {
            ColumnMeta incr = findColumn(srcTable, tableIncrColumn.trim());
            if (incr != null && TablePartitionPlanner.supports(incr)) {
                return incr;
            }
        }
        List<ColumnMeta> primaryKeys = new ArrayList<>();
        for (ColumnMeta column : srcTable.columns()) {
            if (column.isPrimaryKey()) {
                primaryKeys.add(column);
            }
        }
        if (primaryKeys.size() == 1 && TablePartitionPlanner.supports(primaryKeys.get(0))) {
            return primaryKeys.get(0);
        }
        return null;
    }

    private ColumnMeta findColumn(TableMeta table, String columnName) {
        for (ColumnMeta column : table.columns()) {
            if (column.getName() != null && column.getName().equalsIgnoreCase(columnName)) {
                return column;
            }
        }
        return null;
    }

    private String qualifyTable(TableMeta table) {
        String tableSchema = table.getSchema();
        if (tableSchema == null || tableSchema.trim().isEmpty()) {
            tableSchema = resolveSchema();
        }
        if (tableSchema == null || tableSchema.trim().isEmpty()) {
            return table.getTable();
        }
        return tableSchema + "." + table.getTable();
    }

    /**
     * 当前线程处理完一张表后调用：等所有并发线程都完成后，由最后一个线程落盘增量水位。
     */
    private void finishTable(TableMeta srcTable, boolean success) {
        if (!success) {
            partitionReadFailed = true;
        }
        int parallelism = Math.max(1, getParallelism());
        if (parallelism <= 1) {
            if (success) {
                persistTableStatus(srcTable);
            }
            return;
        }
        AtomicInteger pending = tablePendingWorkers.computeIfAbsent(
            srcTable.getTable(), key -> new AtomicInteger(parallelism));
        if (pending.decrementAndGet() == 0) {
            tablePendingWorkers.remove(srcTable.getTable(), pending);
            if (!partitionReadFailed) {
                persistTableStatus(srcTable);
            }
        }
    }

    private void persistTableStatus(TableMeta srcTable) {
        Object lastValue = lastIncrValues.get(srcTable.getTable());
        if (lastValue != null) {
            persistIncrStatus(srcTable, lastValue);
        }
    }

    /**
     * 打开源数据库连接。
     */
    protected Connection getSourceConnection() throws SQLException {
        // 通过数据源类型解析连接（支持通用 JDBC 等需要按类型加载自定义驱动的数据源）
        return DBUtils.getConnection(datasource);
    }

    /**
     * 解析本次读取使用的 schema：优先使用配置的 schema，否则回退到数据源的 dbschema。
     */
    protected String resolveSchema() {
        if (schema != null && !schema.trim().isEmpty()) {
            return schema;
        }
        return datasource.getDbschema();
    }

    /**
     * 解析多表配置。
     * 当 table 为空但 schema 不为空时，获取当前 schema 下的所有表进行同步。
     */
    protected List<String> parseTables() {
        if ((table == null || table.trim().isEmpty())
                && schema != null && !schema.trim().isEmpty()) {
            try (Connection conn = getSourceConnection()) {
                List<TableMeta> tableList = DBUtils.getTableList(conn, schema);
                if (tableList.isEmpty()) {
                    throw new IllegalArgumentException("schema " + schema + " 下没有找到任何表");
                }
                List<String> tables = new ArrayList<>();
                for (TableMeta tableMeta : tableList) {
                    String tableName = tableMeta.getTable();
                    // 备份表、临时表不参与同步
                    if (tableName.endsWith("_backup") || tableName.endsWith("_tmp")) {
                        continue;
                    }
                    tables.add(tableName);
                }
                // 并发线程需要一致的顺序，保证不可分区时按表序号分发不会重复或遗漏
                tables.sort(String::compareToIgnoreCase);
                logInfo("获取schema " + schema + " 下的所有表，共 " + tables.size() + " 个表.");
                return tables;
            } catch (SQLException e) {
                throw new RuntimeException("获取schema下表列表失败: " + e.getMessage(), e);
            }
        }

        if (table == null || table.trim().isEmpty()) {
            throw new IllegalArgumentException("table参数不能为空");
        }
        String[] tables = table.split(",");
        if (tables.length == 0) {
            throw new IllegalArgumentException("table参数格式错误，应包含至少一个表名");
        }
        List<String> result = new ArrayList<>(tables.length);
        for (String tableName : tables) {
            result.add(tableName.trim());
        }
        return result;
    }

    /**
     * 解析增量字段配置（JSON格式）。
     * 格式示例：{"table1": "id", "table2": "update_time"}
     */
    protected Map<String, String> parseIncrColumns(List<String> tables) {
        Map<String, String> incrColumns = new HashMap<>();
        if (incrColumn != null && !incrColumn.trim().isEmpty()) {
            try {
                // 尝试解析为JSON
                JSONObject json = JSONObject.parseObject(incrColumn);
                for (String key : json.keySet()) {
                    incrColumns.put(key, json.getString(key));
                }
            } catch (Exception e) {
                // 如果不是JSON格式，则按旧方式处理（单表）
                if (tables.size() == 1) {
                    incrColumns.put(tables.get(0), incrColumn.trim());
                } else {
                    logInfo("警告：多表同步时incrColumn应为JSON格式，当前使用默认配置");
                }
            }
        }
        return incrColumns;
    }

    /**
     * 加载增量同步状态。
     * <p>并发读取时多个线程共享同一个组件实例，只允许加载一次，避免线程读到本次运行中途推进的水位。</p>
     */
    protected synchronized void loadIncrStatus(Map<String, String> incrColumns) {
        if (incrStatusLoaded) {
            return;
        }
        for (String tableName : incrColumns.keySet()) {
            String statusKey = "lastIncrValue." + tableName;
            Object lastValue = getStatus(statusKey);
            if (lastValue != null) {
                loadedIncrValues.put(tableName, lastValue);
                lastIncrValues.put(tableName, lastValue);
                logInfo("加载表 " + tableName + " 的上次增量值: " + lastValue);
            }
        }
        incrStatusLoaded = true;
    }

    /**
     * 流式读取单表数据并分批交给子类处理。
     */
    protected void migrateData(Connection srcConn, TableMeta srcTable, String tableIncrColumn) throws SQLException {
        migrateData(srcConn, srcTable, tableIncrColumn, null);
    }

    /**
     * 流式读取单表数据并分批交给子类处理。
     *
     * @param flowFile 触发本次读取的 FlowFile，用于解析 WHERE 条件中的动态参数
     */
    protected void migrateData(Connection srcConn, TableMeta srcTable, String tableIncrColumn, FlowFile flowFile)
            throws SQLException {
        migrateData(srcConn, srcTable, tableIncrColumn, flowFile, null);
    }

    /**
     * 流式读取单表数据并分批交给子类处理。
     *
     * @param flowFile          触发本次读取的 FlowFile，用于解析 WHERE 条件中的动态参数
     * @param partitionPredicate 当前线程负责的分区谓词，为 {@code null} 时读取整表
     */
    protected void migrateData(Connection srcConn, TableMeta srcTable, String tableIncrColumn, FlowFile flowFile,
                               String partitionPredicate) throws SQLException {
        String srcQuery = buildQuery(srcTable, tableIncrColumn, flowFile, partitionPredicate);
        logInfo("执行查询: " + srcQuery);

        try (Statement srcStmt = DBUtils.createStreamStatement(srcTable.getDbType(), srcConn);
             ResultSet rs = srcStmt.executeQuery(srcQuery)) {
            // 列名/类型在查询期间固定，提前读一次，避免每行每列都调用 getMetaData()
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            String[] columnNames = new String[columnCount + 1];
            int[] columnTypes = new int[columnCount + 1];
            int incrColumnIndex = -1;
            for (int i = 1; i <= columnCount; i++) {
                columnNames[i] = metaData.getColumnName(i);
                columnTypes[i] = metaData.getColumnType(i);
                if (incrColumnIndex < 0 && tableIncrColumn != null && tableIncrColumn.equals(columnNames[i])) {
                    incrColumnIndex = i;
                }
            }

            int batchCount = 0;
            List<JSONObject> batchRecords = new ArrayList<>();
            Object currentTableLastIncrValue = lastIncrValues.get(srcTable.getTable());

            while (rs.next()) {
                JSONObject jsonRecord = new JSONObject(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    int incrColumnSqlType = columnTypes[i];
                    Object value = readColumnValue(rs, i, incrColumnSqlType, srcTable);
                    jsonRecord.put(columnNames[i], value);

                    // 记录增量字段的最大值；并发读取时共享水位只增不减
                    if (i == incrColumnIndex) {
                        if (currentTableLastIncrValue == null || compareIncrValue(value, currentTableLastIncrValue, incrColumnSqlType) > 0) {
                            currentTableLastIncrValue = value;
                            final int sqlType = incrColumnSqlType;
                            lastIncrValues.merge(srcTable.getTable(), value, (oldValue, newValue) ->
                                compareIncrValue(newValue, oldValue, sqlType) > 0 ? newValue : oldValue);
                        }
                    }
                }
                batchRecords.add(jsonRecord);
                batchCount++;

                // 每 FETCH_SIZE 条记录作为一组交给子类处理
                if (batchCount % FETCH_SIZE == 0) {
                    emitBatch(batchRecords, srcTable);
                    batchRecords.clear();
                    logInfo("已处理 " + batchCount + " 条记录, table: " + srcTable.getTable());
                }

                // 调试模式：达到采样行数上限后停止读取
                if (getContext() != null && getContext().isDebugMode() && batchCount >= getContext().getDebugRowLimit()) {
                    logInfo("调试模式：表 " + srcTable.getTable() + " 达到采样行数上限 " + getContext().getDebugRowLimit());
                    break;
                }
            }

            // 处理剩余记录；流式模式下记录为 0 时也发送一个空数据事件
            if (!batchRecords.isEmpty() || (emitEmptyBatch() && batchCount == 0)) {
                emitBatch(batchRecords, srcTable);
            }

            logInfo("表 " + srcTable.getTable() + " 同步完成，共处理 " + batchCount + " 条记录");
        }
    }

    /**
     * 读取列值。
     *
     * <p>SQL Server 的 {@code TIME} 通过 {@code getObject} 返回 {@link java.sql.Time}（本地墙上时间），
     * 后续按 UTC 还原会发生时区偏移（如 12:34 变 04:34），因此直接读取为 {@link LocalTime}。
     */
    protected Object readColumnValue(ResultSet rs, int index, int sqlType, TableMeta table) throws SQLException {
        Object value;
        if (sqlType == Types.TIME) {
            // JDBC 的 TIME 是本地墙上时间，直接读取为 LocalTime，避免按 UTC 还原导致时区偏移
            value = null;
            try {
                value = rs.getObject(index, LocalTime.class);
            } catch (SQLException | AbstractMethodError | UnsupportedOperationException e) {
                // 驱动不支持 java.time 时退回 java.sql.Time
            }
            if (value == null) {
                java.sql.Time time = rs.getTime(index);
                value = time == null ? null : time.toLocalTime();
            }
        } else if (table != null && "SQLSERVER".equalsIgnoreCase(table.getDbType())) {
            String typeName = rs.getMetaData().getColumnTypeName(index);
            if ("datetimeoffset".equalsIgnoreCase(typeName)) {
                value = rs.getObject(index, OffsetDateTime.class);
            } else {
                value = rs.getObject(index);
            }
        } else {
            value = rs.getObject(index);
        }
        return normalizeReadValue(table, index, value);
    }

    /**
     * 按源端 Provider 归一化列值（如 ClickHouse 数组 -> JSONArray、无符号整数 -> BigInteger）。
     */
    private Object normalizeReadValue(TableMeta table, int index, Object value) {
        JdbcDataSourceProvider provider = resolveSourceProvider();
        if (provider == null) {
            return value;
        }
        ColumnMeta column = null;
        if (table != null && index > 0 && index <= table.columns().size()) {
            column = table.columns().get(index - 1);
        }
        return provider.normalizeReadValue(column, value);
    }

    private JdbcDataSourceProvider resolveSourceProvider() {
        if (!sourceProviderResolved) {
            sourceProvider = DBUtils.resolveJdbcProvider(datasource);
            sourceProviderResolved = true;
        }
        return sourceProvider;
    }

    /**
     * 根据增量字段的 JDBC 类型比较两个值的大小，用于确定最大值。
     * <p>
     * 整数、浮点、定点、时间类型分别走对应的快速比较，避免统一 toString 后字符串/字典序比较导致的
     * 错误（如「99999 > 100000」）以及每行重复的字符串解析开销。
     */
    protected int compareIncrValue(Object a, Object b, int sqlType) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }

        switch (sqlType) {
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
            case Types.BIGINT:
                return Long.compare(toLong(a), toLong(b));
            case Types.FLOAT:
            case Types.REAL:
            case Types.DOUBLE:
                return Double.compare(toDouble(a), toDouble(b));
            case Types.NUMERIC:
            case Types.DECIMAL:
                return toBigDecimal(a).compareTo(toBigDecimal(b));
            case Types.DATE:
            case Types.TIME:
            case Types.TIME_WITH_TIMEZONE:
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return Long.compare(toEpochMillis(a), toEpochMillis(b));
            default:
                return a.toString().compareTo(b.toString());
        }
    }

    private long toLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : Long.parseLong(value.toString());
    }

    private double toDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : Double.parseDouble(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        return value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(value.toString());
    }

    private long toEpochMillis(Object value) {
        if (value instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) value).getTime();
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).getTime();
        }
        if (value instanceof java.sql.Time) {
            return ((java.sql.Time) value).getTime();
        }
        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).getTime();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }

        // 增量状态通过 FlowFile 持久化后时间类型会变成字符串（如 2026-11-09 10:00:49.0），
        // 需要先按日期时间解析，不能直接 Long.parseLong，否则时间字段的增量同步会失败。
        String text = value.toString().trim();
        try {
            return Timestamp.valueOf(text).getTime();
        } catch (IllegalArgumentException ignored) {
            // 非 "yyyy-MM-dd HH:mm:ss" 格式，继续尝试 ISO 格式
        }
        try {
            return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        } catch (Exception ignored) {
            // 非日期格式，按数字时间戳解析
        }
        return Long.parseLong(text);
    }

    /**
     * 构建查询语句，支持增量同步与 where 动态参数替换（多表版本）。
     */
    protected String buildQuery(TableMeta srcTable, String tableIncrColumn) {
        return buildQuery(srcTable, tableIncrColumn, null, null);
    }

    /**
     * 构建查询语句，支持增量同步与 where 动态参数替换（多表版本）。
     *
     * @param flowFile 触发本次读取的 FlowFile，用于解析 where 中的动态参数；为 null 时仍会解析
     *                 {@code #{...}} 内置参数，属性类参数保持原样
     */
    protected String buildQuery(TableMeta srcTable, String tableIncrColumn, FlowFile flowFile) {
        return buildQuery(srcTable, tableIncrColumn, flowFile, null);
    }

    /**
     * 构建查询语句，支持增量同步、where 动态参数替换和分区谓词（多表版本）。
     *
     * @param partitionPredicate 当前线程负责的分区谓词，为 {@code null} 时不追加分区条件
     */
    protected String buildQuery(TableMeta srcTable, String tableIncrColumn, FlowFile flowFile, String partitionPredicate) {
        StringBuilder query = new StringBuilder();
        query.append("SELECT * FROM ");
        String tableSchema = srcTable.getSchema();
        if (tableSchema != null && !tableSchema.trim().isEmpty()) {
            query.append(tableSchema).append(".");
        }
        query.append(srcTable.getTable());

        boolean hasCondition = false;

        // 处理 where 条件，支持 ${ytenantid} 以及 ${attr:属性名}、${属性名}、${FLOW_FILE_DATA}、#{...} 等动态参数
        String processedWhere = resolveWhere(where, flowFile);
        if (processedWhere != null && !processedWhere.trim().isEmpty()) {
            query.append(" WHERE ").append(processedWhere);
            hasCondition = true;
        }

        // 如果增量字段不为空，添加增量条件
        if (tableIncrColumn != null && !tableIncrColumn.trim().isEmpty()) {
            Object lastIncrValue = loadedIncrValues.get(srcTable.getTable());
            if (lastIncrValue != null) {
                query.append(hasCondition ? " AND " : " WHERE ");
                // 如果上次有保存的增量值，查询大于该值的记录
                if (lastIncrValue instanceof String) {
                    query.append(tableIncrColumn).append(" > '").append(lastIncrValue).append("'");
                } else {
                    query.append(tableIncrColumn).append(" > ").append(lastIncrValue);
                }
                hasCondition = true;
                logInfo("表 " + srcTable.getTable() + " 使用增量同步，查询条件: " + tableIncrColumn + " > " + lastIncrValue);
            } else {
                // 第一次执行，查询所有记录
                logInfo("表 " + srcTable.getTable() + " 首次增量同步，查询所有记录");
            }
        }

        // 追加当前线程负责的分区条件
        if (partitionPredicate != null && !partitionPredicate.trim().isEmpty()) {
            query.append(hasCondition ? " AND " : " WHERE ");
            query.append("(").append(partitionPredicate).append(")");
        }

        String sql = query.toString();
        if (getContext() != null && getContext().isDebugMode()) {
            // 调试模式：把采样行数上限下推到 SQL，具体语法由数据源 Provider 决定
            return DBUtils.applyRowLimit(sql, datasource, getContext().getDebugRowLimit());
        }
        return sql;
    }

    /**
     * 解析 where 条件中的动态参数。
     *
     * <p>支持的参数语法与其他组件保持一致：{@code ${ytenantid}}（兼容旧配置）、
     * {@code ${attr:属性名}}、{@code ${属性名}}、{@code ${FLOW_FILE_DATA}} 以及 {@code #{...}}
     * 内置参数。参数值来自触发本次读取的 FlowFile；无法解析的参数保持原样并记录告警。</p>
     *
     * <p>注意：参数值直接拼接进 SQL，字符串/日期需要自行加引号，例如
     * {@code stat_date >= '${attr:start_date}'}；请确保参数来源可信。</p>
     *
     * @param where    配置的 where 条件
     * @param flowFile 触发本次读取的 FlowFile，可为 null
     * @return 替换参数后的 where 条件
     */
    protected String resolveWhere(String where, FlowFile flowFile) {
        if (where == null || where.trim().isEmpty()) {
            return where;
        }

        String resolved = where;
        // 兼容旧的租户占位符，取值来自组件配置的 ytenantId
        if (ytenantId != null) {
            resolved = resolved.replace("${ytenantid}", ytenantId);
        }

        try {
            ParameterUtil.ReplacementResult result = ParameterUtil.replaceParametersStrict(resolved, flowFile, null);
            if (result.hasUnresolved()) {
                logWarn("where 条件中存在未解析的动态参数，将按原样拼接: " + result.getUnresolvedParameters());
            }
            return result.getText();
        } catch (Exception e) {
            logWarn("where 条件动态参数替换失败，按原条件执行: " + e.getMessage());
            return resolved;
        }
    }

    /**
     * 当前是否处于调试模式。
     */
    protected boolean isDebugMode() {
        return getContext() != null && getContext().isDebugMode();
    }

    // 自动注入参数的setters
    public void setTable(String table) {
        this.table = table;
    }

    public void setIncrColumn(String incrColumn) {
        this.incrColumn = incrColumn;
    }

    public void setWhere(String where) {
        this.where = where;
    }

    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    public void setSourceId(DatasourceInfo sourceId) {
        this.datasource = sourceId;
    }

    public void setYtenantId(String ytenantId) {
        this.ytenantId = ytenantId;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getSchema() {
        return schema;
    }

    /**
     * 设置上次增量值（用于测试或手动设置）- 多表版本
     */
    public void setLastIncrValue(String tableName, Object lastIncrValue) {
        this.lastIncrValues.put(tableName, lastIncrValue);
        this.loadedIncrValues.put(tableName, lastIncrValue);
    }

    /**
     * 获取上次增量值 - 多表版本
     */
    public Object getLastIncrValue(String tableName) {
        return lastIncrValues.get(tableName);
    }

    /**
     * 获取所有表的增量状态
     */
    public Map<String, Object> getLastIncrValues() {
        return new HashMap<>(lastIncrValues);
    }
}
