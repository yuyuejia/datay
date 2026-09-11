package com.data.datafusion.service.dto;

import com.data.job.DebugResult;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调试运行的返回结果，按节点 id 保存各组件输出的采样数据。
 */
public class ETLDebugResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Map<String, DebugResult> nodes = new LinkedHashMap<>();

    // 调试运行失败时的错误信息
    private String error;

    // 调试运行耗时（毫秒）
    private long elapsedMs;

    public Map<String, DebugResult> getNodes() {
        return nodes;
    }

    public void setNodes(Map<String, DebugResult> nodes) {
        this.nodes = nodes == null ? new LinkedHashMap<>() : nodes;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = elapsedMs;
    }
}
