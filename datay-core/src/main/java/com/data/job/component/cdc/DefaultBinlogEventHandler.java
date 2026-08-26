package com.data.job.component.cdc;

import com.alibaba.fastjson2.JSONArray;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * 默认的Binlog事件处理器
 * 将Binlog事件转换为FlowFile并发送到下游组件
 * 支持事件合并机制：当相邻的两个事件操作的表和操作类型相同时，合并afterData到一个flowfile
 */
public class DefaultBinlogEventHandler implements BinlogEventHandler {

    private static final Logger log = LoggerFactory.getLogger(DefaultBinlogEventHandler.class);

    private final FlowComponent component;
    private final BlockingQueue<BinlogEvent> queue;

    // 事件合并缓冲区
    private List<BinlogEvent> eventBuffer = new ArrayList<>();
    private static final int MAX_MERGE_RECORDS = 10000; // 最大合并记录数

    // 当前缓冲区的表信息和操作类型
    private String currentDatabase;
    private String currentTable;
    private BinlogEvent.EventType currentEventType;

    public DefaultBinlogEventHandler(FlowComponent component, BlockingQueue<BinlogEvent> queue) {
        this.component = component;
        this.queue = queue;
    }

    @Override
    public void handleEvent(BinlogEvent event) {
        try {
            // 检查是否可以合并到当前缓冲区
            if (canMergeWithBuffer(event)) {
                // 合并到缓冲区
                eventBuffer.add(event);

                // 检查是否达到最大合并记录数
                if (eventBuffer.size() >= MAX_MERGE_RECORDS) {
                    flushBuffer();
                }
            } else {
                // 先刷新缓冲区中的事件
                if (!eventBuffer.isEmpty()) {
                    flushBuffer();
                }

                // 如果是可合并的事件类型，开始新的缓冲区
                if (isMergeableEventType(event.getEventType())) {
                    startNewBuffer(event);
                    eventBuffer.add(event);
                } else {
                    // 不可合并的事件类型，直接发送
                    FlowFile flowFile = convertToFlowFile(event);
                    component.writeRecords(flowFile);
//                    component.setStatus("binlogPosition", flowFile.getAttribute("_binlogPosition"));
//                    component.setStatus("binlogFile", flowFile.getAttribute("_binlogFile"));
//                    component.getContext().saveStatus();
                }
            }
        } catch (Exception e) {
            handleError(event, e);
        }
    }

    /**
     * 检查事件是否可以合并到当前缓冲区
     */
    private boolean canMergeWithBuffer(BinlogEvent event) {
        // 检查事件类型是否可合并
        if (!isMergeableEventType(event.getEventType())) {
            return false;
        }

        // 检查表信息和操作类型是否匹配
        return (
            event.getDatabase().equals(currentDatabase) && event.getTable().equals(currentTable) && event.getEventType() == currentEventType
        );
    }

    /**
     * 检查事件类型是否可合并
     * INSERT和UPDATE事件可以合并，其他类型不可合并
     */
    private boolean isMergeableEventType(BinlogEvent.EventType eventType) {
        return (
            eventType == BinlogEvent.EventType.INSERT ||
            eventType == BinlogEvent.EventType.UPDATE ||
            eventType == BinlogEvent.EventType.DELETE
        );
    }

    /**
     * 开始新的事件缓冲区
     */
    private void startNewBuffer(BinlogEvent firstEvent) {
        currentDatabase = firstEvent.getDatabase();
        currentTable = firstEvent.getTable();
        currentEventType = firstEvent.getEventType();
        eventBuffer.clear();
    }

    /**
     * 刷新缓冲区，将合并的事件发送到下游
     */
    private void flushBuffer() {
        if (eventBuffer.isEmpty()) {
            return;
        }

        try {
            // 创建合并后的FlowFile
            FlowFile mergedFlowFile = createMergedFlowFile();
            // 发送到下游组件
            component.writeRecords(mergedFlowFile);
            log.debug(
                "成功发送合并事件，合并记录数: {}, 表: {}.{}, 操作类型: {}",
                eventBuffer.size(),
                currentDatabase,
                currentTable,
                currentEventType
            );
//            component.setStatus("binlogPosition", mergedFlowFile.getAttribute("_binlogPosition"));
//            component.setStatus("binlogFile", mergedFlowFile.getAttribute("_binlogFile"));
//            component.getContext().saveStatus();

            // 清空缓冲区
            eventBuffer.clear();
        } catch (Exception e) {
            log.error("发送合并事件失败: {}", e.getMessage(), e);
            // 这里可以添加重试逻辑或错误处理
        }
    }

    /**
     * 创建合并后的FlowFile
     */
    private FlowFile createMergedFlowFile() {
        if (eventBuffer.isEmpty()) {
            throw new IllegalStateException("事件缓冲区为空");
        }

        // 使用第一个事件作为基础信息
        BinlogEvent firstEvent = eventBuffer.get(0);
        FlowFile mergedFlowFile = new FlowFile();

        // 设置合并事件的基本信息
        mergedFlowFile.setAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE, firstEvent.getEventType().name());
        mergedFlowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, firstEvent.getTableMeta());
        mergedFlowFile.setAttribute(FlowFile.ATTRIBUTE_DATABASE, firstEvent.getDatabase());
        mergedFlowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, firstEvent.getTable());
        mergedFlowFile.setAttribute(FlowFile.ATTRIBUTE_ERROR_IGNORE, "true");
        mergedFlowFile.setAttribute(FlowFile.ATTRIBUTE_TIMESTAMP, firstEvent.getTimestamp());

        // 合并所有事件的afterData
        JSONArray mergedData = new JSONArray();
        for (BinlogEvent event : eventBuffer) {
            if (event.getAfterData() != null) {
                // 如果是UPDATE操作，还需要处理beforeData
                if (firstEvent.getEventType() == BinlogEvent.EventType.UPDATE) {
                    event.getBeforeData().put("__before", event.getBeforeData());
                }

                mergedData.add(event.getAfterData());
            }
            mergedFlowFile.setStatus(component.getId() + "." + "binlogFile", event.getBinlogFileName());
            mergedFlowFile.setStatus(component.getId() + "." + "binlogPosition", event.getBinlogPosition());
        }
        mergedFlowFile.setJsonArray(mergedData);
        return mergedFlowFile;
    }

    /**
     * 将单个Binlog事件转换为FlowFile（用于不可合并的事件）
     */
    private FlowFile convertToFlowFile(BinlogEvent event) {
        FlowFile flowFile = new FlowFile();

        // 设置事件基本信息
        flowFile.setAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE, event.getEventType().name());
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, event.getTableMeta());
        flowFile.setAttribute(FlowFile.ATTRIBUTE_DATABASE, event.getDatabase());
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TABLE, event.getTable());
        flowFile.setAttribute(FlowFile.ATTRIBUTE_ERROR_IGNORE, "true");
        flowFile.setAttribute(FlowFile.ATTRIBUTE_TIMESTAMP, event.getTimestamp());
        flowFile.setAttribute("_xid", event.getXid());
        flowFile.setStatus(component.getId() + "." + "binlogFile", event.getBinlogFileName());
        flowFile.setStatus(component.getId() + "." + "binlogPosition", event.getBinlogPosition());
//        flowFile.setAttribute("_binlogFile", event.getBinlogFileName());
//        flowFile.setAttribute("_binlogPosition", event.getBinlogPosition());


        // 根据事件类型设置数据
        switch (event.getEventType()) {
            case INSERT:
                flowFile.setJsonArray(new JSONArray(event.getAfterData()));
                break;
            case UPDATE:
                event.getAfterData().put("__before", event.getBeforeData());
                flowFile.setJsonArray(new JSONArray(event.getAfterData()));
                break;
            case DELETE:
                flowFile.setJsonArray(new JSONArray(event.getBeforeData()));
                break;
            case QUERY:
                flowFile.setTextData(event.getQuery());
                flowFile.setAttribute(FlowFile.ATTRIBUTE_QUERY, event.getQuery());
                break;
            case XID:
                flowFile.setAttribute(FlowFile.ATTRIBUTE_EVENT_TYPE, "QUERY");
                flowFile.setAttribute(FlowFile.ATTRIBUTE_QUERY, "COMMIT");
                flowFile.setTextData("COMMIT");
                break;
            default:
                flowFile.setTextData(event.getQuery());
        }

        return flowFile;
    }

    @Override
    public void handleError(BinlogEvent event, Exception exception) {
        log.error("处理Binlog事件失败: {}, 错误信息: {}", event, exception.getMessage(), exception);
        // 可以根据需要实现重试逻辑或错误处理策略
    }

    /**
     * 强制刷新缓冲区（用于程序退出或异常情况）
     */
    public void flush() {
        if (!eventBuffer.isEmpty()) {
            flushBuffer();
        }
    }
}
