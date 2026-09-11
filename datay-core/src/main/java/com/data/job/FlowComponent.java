package com.data.job;

import com.alibaba.fastjson2.JSONArray;
import com.data.metadata.util.DBUtils;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

// 组件接口定义
public abstract class FlowComponent extends Component {

    private boolean isEnd = false;
    private int endCount = 0;
    public boolean upstreamFinish = false;
    private FlowFile currentFlowFile;

    // 调试模式下当前源组件已输出的行数
    private long debugEmittedRows = 0;

    public abstract void execute(FlowFile flowFile);

    public void execute() {
        logInfo("开始执行组件:" + getName());

        while (!isEnd) {
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
                sleep();
                continue;
            }
            for (FlowFile flowFile : flowFiles) {
                if (flowFile.getAttribute("_end") != null) {
                    endCount++;
                    if (endCount >= getInput().size()) {
                        upstreamFinish = true;
                    }
                }
                try {
                    currentFlowFile = flowFile;
                    execute(flowFile);
                    // 调试模式下，对于像 JdbcInput 这类直接把结果物化到 DuckDB 的组件，尝试从输出表回填采样数据
                    captureMaterializedOutputIfNeeded();
                    //检查当前FlowFile是否有状态，有状态,同时该组件为sink或者没有下游组件，进行状态保存
                    if (!flowFile.getStatusMap().isEmpty()&&(isSink()||getOutput().isEmpty())) {
                        getContext().getStatusMap().putAll(flowFile.getStatusMap());
                        getContext().saveStatus();
                        logInfo("组件" + getName() + "状态保存成功：" + flowFile.getStatusMap());
                    }
                } catch (DebugLimitReachedException e) {
                    // 调试模式下源组件达到采样上限，属于正常结束
                    logInfo(e.getMessage());
                    isEnd = true;
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
                isEnd = true;
            }
        }
        // 检查所有线程是否都已完成
        if (getActiveThreads().decrementAndGet() == 0) {
            writeEndRecord();
        }
        logInfo("组件执行完成:" + getName());
    }

    public void sleep() {
        try {
//            logInfo("组件" + getName() + " sleep 100ms");
            Thread.sleep(100);
        } catch (InterruptedException e) {
            isEnd = true;
            logError("组件执行被中断:" + getName());
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public void writeRecords(FlowFile flowFile) {
        // 在每次循环开始前检查中断状态
        if (Thread.currentThread().isInterrupted()) {
            isEnd = true;
            logError("组件执行被中断:" + getName());
            throw new RuntimeException("组件执行被中断:" + getName());
        }
        // 合并当前FlowFile的状态到新的FlowFile，将状态传递下去
        if (!getOutput().isEmpty() && currentFlowFile != null && !currentFlowFile.getStatusMap().isEmpty()) {
            flowFile.getStatusMap().putAll(currentFlowFile.getStatusMap());
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
                debugEmittedRows += countRows(flowFile);
                if (debugEmittedRows >= getContext().getDebugRowLimit()) {
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
     * 调试模式下，对于把结果物化到 DuckDB 临时表的组件（如 JdbcInput），
     * 从输出表读取采样数据，便于下游组件配置时查看。
     */
    private void captureMaterializedOutputIfNeeded() {
        if (getContext() == null || !getContext().isDebugMode()) {
            return;
        }
        if (getContext().hasDebugOutput(getId())) {
            return;
        }
        String dbFile = getContext().getJobInstanceCode();
        if (dbFile == null || dbFile.trim().isEmpty()) {
            return;
        }
        String tableName = getOutputTableName(0);
        try (java.sql.Connection conn = DuckDBEngine.getInstance().getConnection(dbFile)) {
            if (!DBUtils.tableExists(conn, "main", tableName)) {
                return;
            }
            int limit = getContext().getDebugRowLimit();
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(
                "SELECT * FROM main." + tableName + " LIMIT " + (limit + 1))) {
                ResultSetMetaData metaData = rs.getMetaData();
                int columnCount = metaData.getColumnCount();
                List<String> columns = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    columns.add(metaData.getColumnName(i));
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
                        row.add(rs.getObject(i));
                    }
                    rows.add(row);
                }
                getContext().captureDebugOutputRows(getId(), columns, rows, truncated);
            }
        } catch (Exception e) {
            logDebug("调试模式：从 DuckDB 输出表 " + tableName + " 采集数据失败：" + e.getMessage());
        }
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
            isEnd = true;
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
        isEnd = true;
    }

    public FlowFile getCurrentFlowFile() {
        return currentFlowFile;
    }

    public void setCurrentFlowFile(FlowFile currentFlowFile) {
        this.currentFlowFile = currentFlowFile;
    }
}