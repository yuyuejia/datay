package com.data.datafusion.ai.tool;

import com.data.datafusion.ai.assistant.AiAssistant;
import com.data.datafusion.service.dto.DataSourceDTO;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 工具执行上下文。
 *
 * <p>承载一次 Agent 会话中与业务相关的运行时信息，例如当前操作的数据源与所选助手。
 * 工具实现可据此决定查询范围与安全边界，避免模型自行编造数据源标识。
 *
 * <p>工具还可在执行过程中通过 {@link #putArtifact(String, Object)} 记录结构化产物
 * （如指标查询的数据表），Agent 会在会话结束时将其透传给前端展示。
 */
public class AiToolContext {

    private final Long dataSourceId;
    private final DataSourceDTO dataSource;
    private final String userMessage;
    private final AiAssistant assistant;
    private final Map<String, Object> artifacts = new LinkedHashMap<>();

    public AiToolContext(Long dataSourceId, DataSourceDTO dataSource, String userMessage, AiAssistant assistant) {
        this.dataSourceId = dataSourceId;
        this.dataSource = dataSource;
        this.userMessage = userMessage;
        this.assistant = assistant;
    }

    /**
     * 记录一个结构化产物，供 Agent 在会话结束时透传。同名产物以最后一次写入为准。
     */
    public void putArtifact(String key, Object value) {
        if (key != null) {
            artifacts.put(key, value);
        }
    }

    public Object getArtifact(String key) {
        return artifacts.get(key);
    }

    public Map<String, Object> getArtifacts() {
        return artifacts;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }

    public DataSourceDTO getDataSource() {
        return dataSource;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public AiAssistant getAssistant() {
        return assistant;
    }
}
