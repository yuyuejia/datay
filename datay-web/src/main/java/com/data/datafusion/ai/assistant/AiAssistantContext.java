package com.data.datafusion.ai.assistant;

import com.data.datafusion.service.dto.DataSourceDTO;

/**
 * 助手会话上下文。
 *
 * <p>承载一次生成请求中与业务相关的运行时信息，供助手构建提示词时参考。
 */
public class AiAssistantContext {

    private final DataSourceDTO dataSource;
    private final String userMessage;

    public AiAssistantContext(DataSourceDTO dataSource, String userMessage) {
        this.dataSource = dataSource;
        this.userMessage = userMessage;
    }

    public DataSourceDTO getDataSource() {
        return dataSource;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
