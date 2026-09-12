package com.data.datafusion.ai.tool;

import com.data.datafusion.service.dto.DataSourceDTO;

/**
 * AI 工具执行上下文。
 *
 * <p>承载一次 Agent 会话中与业务相关的运行时信息，例如当前操作的数据源。
 * 工具实现可据此决定查询范围，避免模型自行编造数据源标识。
 */
public class AiToolContext {

    private final Long dataSourceId;
    private final DataSourceDTO dataSource;
    private final String userMessage;

    public AiToolContext(Long dataSourceId, DataSourceDTO dataSource, String userMessage) {
        this.dataSourceId = dataSourceId;
        this.dataSource = dataSource;
        this.userMessage = userMessage;
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
}
