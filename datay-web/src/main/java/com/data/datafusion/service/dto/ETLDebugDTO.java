package com.data.datafusion.service.dto;

import java.io.Serializable;

/**
 * 调试运行 ETL 任务的请求参数。
 */
public class ETLDebugDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    // 待调试的任务图（可以是未保存的草稿）
    private ETLTaskDTO task;

    // 每个源组件的采样行数上限
    private Integer rowLimit = 100;

    // 调试目标节点，为空时表示整图运行
    private String targetNodeId;

    public ETLTaskDTO getTask() {
        return task;
    }

    public void setTask(ETLTaskDTO task) {
        this.task = task;
    }

    public Integer getRowLimit() {
        return rowLimit;
    }

    public void setRowLimit(Integer rowLimit) {
        this.rowLimit = rowLimit;
    }

    public String getTargetNodeId() {
        return targetNodeId;
    }

    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }
}
