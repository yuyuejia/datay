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
        // 保存当前表的增量状态
        Object lastValue = lastIncrValues.get(srcTable.getTable());
        if (lastValue != null) {
            flowFile.setStatus(getId() + "." + "lastIncrValue." + srcTable.getTable(), lastValue.toString());
        }
        writeRecords(flowFile);
    }
}
