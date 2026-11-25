package com.data.job;

import com.alibaba.fastjson2.JSONArray;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

// 组件接口定义
public abstract class FlowComponent extends Component {

    /**
     * 全局停止标志：由 {@link #stop()} 置位，所有并发线程共享。
     */
    private volatile boolean stopped = false;
    /**
     * 当前线程是否已完成，使用线程本地状态，避免并发线程互相提前结束。
     */
    private final ThreadLocal<Boolean> isEnd = ThreadLocal.withInitial(() -> false);
    private final AtomicInteger endCount = new AtomicInteger();
    public volatile boolean upstreamFinish = false;
    private final ThreadLocal<FlowFile> currentFlowFile = new ThreadLocal<>();

    // 调试模式下当前源组件已输出的行数（按线程统计，避免并发互相干扰）
    private final ThreadLocal<Long> debugEmittedRows = ThreadLocal.withInitial(() -> 0L);

    public abstract void execute(FlowFile flowFile);

    public void execute() {
        logInfo("开始执行组件:" + getName());

        try {
            while (!isEnd.get() && !stopped) {
                List<FlowFile> flowFiles = new LinkedList<>();
                if (getInput().isEmpty()) {
                    //如果是源头组件，需要发送start信号,触发源头组件的执行
                    FlowFile flowFile = new FlowFile();
                    flowFile.setAttribute("_start", true);
                    flowFiles.add(flowFile);
                    upstreamFinish = true;
                } else {
                    flowFiles = readRecords();
                }
                if (flowFiles.isEmpty()) {
                    // 上游已完成且队列已空，当前线程可以退出，避免并发时其他线程空转
                    if (upstreamFinish) {
                        isEnd.set(true);
                        break;
                    }
                    sleep();
                    continue;
                }
                for (FlowFile flowFile : flowFiles) {
                    if (flowFile.getAttribute("_end") != null) {
                        if (endCount.incrementAndGet() >= getInput().size()) {
                            upstreamFinish = true;
                        }
                    }
                    try {
                        currentFlowFile.set(flowFile);
                        execute(flowFile);
                        // 调试模式下，对于像 JdbcInput 这类直接把结果物化到 DuckDB 的组件，尝试从输出表回填采样数据
                        captureMaterializedOutputIfNeeded();
                        //检查当前FlowFile是否有状态，有状态,同时该组件为sink或者没有下游组件，进行状态保存
                        if (!flowFile.getStatusMap().isEmpty() && (isSink() || getOutput().isEmpty())) {
                            getContext().getStatusMap().putAll(flowFile.getStatusMap());
                            getContext().saveStatus();
                        }
                    } catch (DebugLimitReachedException e) {
                        // 调试模式下源组件达到采样上限，属于正常结束
                        logInfo(e.getMessage());
                        isEnd.set(true);
                        break;
                    } catch (Exception e) {
                        logError("组件执行失败：" + getName() + "，异常信息：" + ExceptionUtils.describe(e));
                        e.printStackTrace();
                        if (flowFile.getAttribute(FlowFile.ATTRIBUTE_ERROR_IGNORE) != null) {
                            continue;
                        }
                        throw e;
                    }
                }
                //如果是上游已经都完成发送事件，当前组件可以停止了
                if (upstreamFinish) {
                    isEnd.set(true);
                }
            }
            // 检查所有线程是否都已完成
            if (getActiveThreads().decrementAndGet() == 0) {
                writeEndRecord();
            }
            logInfo("组件执行完成:" + getName());
        } finally {
            isEnd.remove();
            currentFlowFile.remove();
            debugEmittedRows.remove();
        }
    }

    public void sleep() {
        try {
//            logInfo("组件" + getName() + " sleep 100ms");
            Thread.sleep(100);
        } catch (InterruptedException e) {
            isEnd.set(true);
            logError("组件执行被中断:" + getName());
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public void writeRecords(FlowFile flowFile) {
        // 在每次循环开始前检查中断状态
        if (Thread.currentThread().isInterrupted()) {
            isEnd.set(true);
            logError("组件执行被中断:" + getName());
            throw new RuntimeException("组件执行被中断:" + getName());
        }
        // 合并当前FlowFile的状态到新的FlowFile，将状态传递下去
        FlowFile sourceFlowFile = currentFlowFile.get();
        if (!getOutput().isEmpty() && sourceFlowFile != null && !sourceFlowFile.getStatusMap().isEmpty()) {
            flowFile.getStatusMap().putAll(sourceFlowFile.getStatusMap());
        }

        for (Connection connection : getOutput()) {
            BlockingQueue<Object> queue = getContext().getConnections().get(this.getId() + "_" + connection.getTargetId());
            if (queue != null) {
                try {
                    queue.put(flowFile);
                } catch (InterruptedException e) {
                    // 恢复中断状态
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        }

        // 调试模式：采集组件输出，并限制源组件读取的数据量
        if (getContext() != null && getContext().isDebugMode()) {
            getContext().captureDebugOutput(getId(), flowFile);
            if (getInput().isEmpty()) {
                long emitted = debugEmittedRows.get() + countRows(flowFile);
                debugEmittedRows.set(emitted);
                if (emitted >= getContext().getDebugRowLimit()) {
                    throw new DebugLimitReachedException(
                        "调试模式：源组件 " + getName() + " 已达到采样行数上限 " + getContext().getDebugRowLimit()
                    );
                }
            }
        }
    }

    private int countRows(FlowFile flowFile) {
        Object data = flowFile.getData();
        if (data instanceof JSONArray) {
            return ((JSONArray) data).size();
        }
        return data == null ? 0 : 1;
    }

    /**
     * 判断异常链中是否包含调试采样上限异常。
     */
    private boolean isDebugLimitReached(Throwable throwable) {
        while (throwable != null) {
            if (throwable instanceof DebugLimitReachedException) {
                return true;
            }
            throwable = throwable.getCause();
        }
        return false;
    }

    /**
     * 组件是否将结果物化到 DuckDB。
     * <p>调试模式据此决定是否采集 DuckDB 表元数据：仅物化到 DuckDB 的组件（如 JdbcInput、SqlUnit）
     * 展示 DuckDB 表信息，其余组件仍展示 FlowFile 采样数据。
     */
    protected boolean materializesToDuckDB() {
        return false;
    }

    /**
     * 调试模式下，对于把结果物化到 DuckDB 的组件（如 JdbcInput），
     * 记录当前节点执行后 DuckDB 中存在的所有表的元数据与采样数据，便于下游组件配置时查看。
     */
    private void captureMaterializedOutputIfNeeded() {
        if (getContext() == null || !getContext().isDebugMode()) {
            return;
        }
        // 仅物化到 DuckDB 的组件才采集 DuckDB 表元数据，避免覆盖 FlowFile 采样数据
        if (!materializesToDuckDB()) {
            return;
        }
        // 有输入源的组件需等输入全部处理完成后再采集，避免提前采集上游表
        if (!getInput().isEmpty() && !upstreamFinish) {
            return;
        }
        if (getContext().hasDebugOutput(getId())) {
            return;
        }
        String dbFile = getContext().getJobInstanceCode();
        if (dbFile == null || dbFile.trim().isEmpty()) {
            return;
        }
        try (java.sql.Connection conn = DuckDBEngine.getInstance().getConnection(dbFile)) {
            int limit = getContext().getDebugRowLimit();
            List<DebugResult.DebugTable> tables = new ArrayList<>();
            for (TableMeta table : DBUtils.getTableList(conn, "main")) {
                String tableName = table.getTable();
                if (tableName == null || tableName.trim().isEmpty()) {
                    continue;
                }
                try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(
                    "SELECT * FROM main." + tableName + " LIMIT " + (limit + 1))) {
                    ResultSetMetaData metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    List<String> columns = new ArrayList<>(columnCount);
                    List<String> columnTypes = new ArrayList<>(columnCount);
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(metaData.getColumnName(i));
                        columnTypes.add(metaData.getColumnTypeName(i));
                    }
                    List<List<Object>> rows = new ArrayList<>();
                    boolean truncated = false;
                    while (rs.next()) {
                        if (rows.size() >= limit) {
                            truncated = true;
                            break;
                        }
                        List<Object> row = new ArrayList<>(columnCount);
                        for (int i = 1; i <= columnCount; i++) {
                            row.add(readDebugValue(rs, i));
                        }
                        rows.add(row);
                    }
                    DebugResult.DebugTable debugTable = new DebugResult.DebugTable();
                    debugTable.setTableName(tableName);
                    debugTable.setColumns(columns);
                    debugTable.setColumnTypes(columnTypes);
                    debugTable.setRows(rows);
                    debugTable.setTruncated(truncated);
                    tables.add(debugTable);
                }
            }
            getContext().captureDebugOutputTables(getId(), tables);
        } catch (Exception e) {
            logDebug("调试模式：从 DuckDB 采集表元数据失败：" + e.getMessage());
        }
    }

    /**
     * 读取一行中的一个字段用于调试展示：二进制字段转为 Base64，其余转换为可 JSON 序列化的值。
     */
    private Object readDebugValue(ResultSet rs, int columnIndex) throws SQLException {
        int sqlType = rs.getMetaData().getColumnType(columnIndex);
        if (sqlType == Types.BINARY || sqlType == Types.VARBINARY || sqlType == Types.LONGVARBINARY || sqlType == Types.BLOB) {
            byte[] bytes = rs.getBytes(columnIndex);
            return bytes == null ? null : Base64.getEncoder().encodeToString(bytes);
        }
        return DebugResult.toSerializable(rs.getObject(columnIndex));
    }

    public void writeEndRecord() {
        FlowFile flowFile = new FlowFile();
        flowFile.setAttribute("_end", true);
        for (Connection connection : getOutput()) {
            BlockingQueue<Object> queue = getContext().getConnections().get(this.getId() + "_" + connection.getTargetId());
            if (queue != null) {
                try {
                    queue.put(flowFile);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public List<FlowFile> readRecords() {
        // 在每次循环开始前检查中断状态
        if (Thread.currentThread().isInterrupted()) {
            isEnd.set(true);
            logError("组件执行被中断:" + getName());
            throw new RuntimeException("组件执行被中断:" + getName());
        }
        List<FlowFile> records = new LinkedList<>();
        // 批量读取数据
        int maxBatchSize = 1;
        for (Connection connection : getInput()) {
            while (records.size() < maxBatchSize) {
                FlowFile recordsFromQueue = (FlowFile) getContext()
                    .getConnections()
                    .get(connection.getSourceId() + "_" + this.getId())
                    .poll();
                if (recordsFromQueue != null) {
                    records.add(recordsFromQueue);
                } else {
                    break;
                }
            }
        }

        return records;
    }

    public void stop() {
        stopped = true;
    }

    public FlowFile getCurrentFlowFile() {
        return currentFlowFile.get();
    }

    public void setCurrentFlowFile(FlowFile currentFlowFile) {
        this.currentFlowFile.set(currentFlowFile);
    }
}