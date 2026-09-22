package com.data.datafusion.ai.assistant;

import com.data.datafusion.service.dto.DataSourceDTO;
import java.util.Map;

/**
 * 助手会话上下文。
 *
 * <p>承载一次生成请求中与业务相关的运行时信息，供助手构建提示词时参考。
 */
public class AiAssistantContext {

    private final DataSourceDTO dataSource;
    private final String userMessage;
    private final Map<String, Object> contextData;

    public AiAssistantContext(DataSourceDTO dataSource, String userMessage) {
        this(dataSource, userMessage, Map.of());
    }

    public AiAssistantContext(DataSourceDTO dataSource, String userMessage, Map<String, Object> contextData) {
        this.dataSource = dataSource;
        this.userMessage = userMessage;
        this.contextData = contextData == null ? Map.of() : contextData;
    }

    public DataSourceDTO getDataSource() {
        return dataSource;
    }

    public String getUserMessage() {
        return userMessage;
    }

    /**
     * 会话附加上下文，如上游组件的调试采样数据；缺省为空 Map，永不为 null。
     */
    public Map<String, Object> getContextData() {
        return contextData;
    }
}
