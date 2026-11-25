package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;

import java.util.List;

/**
 * 流式 JDBC 输入组件。
 * <p>
 * 从源库流式读取数据，按批下沉为 FlowFile 传递给下游，增量状态随 FlowFile 携带。
 * 公共读取逻辑见 {@link AbstractJdbcInput}。
 */
@ComponentRegister(
    value = "StreamJdbcInput",
    name = "数据源输入",
    group = "数据输入",
    desc = "从关系型数据库流式读取数据，按批下沉为 FlowFile 传递到下游，支持全量/增量同步与多表同步。",
    order = 10
)
public class StreamJdbcInput extends AbstractJdbcInput {

    public StreamJdbcInput() {
        super();
    }

    /**
     * 无数据时也发送空批次，便于下游感知表边界。
     */
    @Override
    protected boolean emitEmptyBatch() {
        return true;
    }

    /**
     * 将批次下沉为 FlowFile 写入下游队列。
     */
    @Override
    protected void emitBatch(List<JSONObject> batchRecords, TableMeta srcTable) {
        JSONArray jsonRecords = new JSONArray();
        jsonRecords.addAll(batchRecords);
        FlowFile flowFile = new FlowFile();
        flowFile.setJsonArray(jsonRecords);
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, srcTable);
        // 添加表名标识，便于后续处理
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, srcTable.getTable());
        flowFile.setAttribute(FlowFile.ATTRIBUTE_DATABASE, srcTable.getSchema());
        // 单线程时每个批次可直接携带增量状态；并发时水位必须等所有分区读完再统一写入，
        // 否则高分区先读完可能提前推进水位，导致低分区数据在下次运行时被增量条件过滤掉。
        if (getParallelism() <= 1) {
            Object lastValue = lastIncrValues.get(srcTable.getTable());
            if (lastValue != null) {
                flowFile.setStatus(getId() + "." + "lastIncrValue." + srcTable.getTable(), lastValue.toString());
            }
        }
        writeRecords(flowFile);
    }

    /**
     * 并发分区读取时，等该表所有线程都完成后，由最后一个线程补发一个空数据批次，
     * 携带全局增量水位，保证下游保存的水位是所有分区中的最大值。
     */
    @Override
    protected void persistIncrStatus(TableMeta srcTable, Object lastIncrValue) {
        if (getParallelism() <= 1 || lastIncrValue == null) {
            // 单线程时每个批次已携带增量状态
            return;
        }
        FlowFile statusFlowFile = new FlowFile();
        statusFlowFile.setJsonArray(new JSONArray());
        statusFlowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, srcTable);
        statusFlowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, srcTable.getTable());
        statusFlowFile.setAttribute(FlowFile.ATTRIBUTE_DATABASE, srcTable.getSchema());
        statusFlowFile.setStatus(getId() + "." + "lastIncrValue." + srcTable.getTable(), lastIncrValue.toString());
        writeRecords(statusFlowFile);
        logInfo("表 " + srcTable.getTable() + " 并发读取完成，写入全局增量水位: " + lastIncrValue);
    }
}
