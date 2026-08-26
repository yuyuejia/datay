package com.data.job;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

// 组件接口定义
public abstract class FlowComponent extends Component {

    private boolean isEnd = false;
    private int endCount = 0;
    public boolean upstreamFinish = false;
    private FlowFile currentFlowFile;

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
                    //检查当前FlowFile是否有状态，有状态,同时该组件为sink或者没有下游组件，进行状态保存
                    if (!flowFile.getStatusMap().isEmpty()&&(isSink()||getOutput().isEmpty())) {
                        getContext().getStatusMap().putAll(flowFile.getStatusMap());
                        getContext().saveStatus();
                        logInfo("组件" + getName() + "状态保存成功：" + flowFile.getStatusMap());
                    }
                } catch (Exception e) {
                    logError("组件执行失败：" + getName() + "，异常信息：" + e.getMessage());
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