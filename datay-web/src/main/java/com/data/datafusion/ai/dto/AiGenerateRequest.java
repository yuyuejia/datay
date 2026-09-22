package com.data.datafusion.ai.dto;

import java.util.List;
import java.util.Map;

/**
 * AI 生成请求体。
 */
public class AiGenerateRequest {

    /** 用户本轮的自然语言需求。 */
    private String message;

    /** 当前会话绑定的数据源 ID，可选。 */
    private Long dataSourceId;

    /** 目标助手 id，缺省时回退到注册顺序中的第一个助手。 */
    private String assistantId;

    /**
     * 兼容旧调用的场景标识：query / task。
     * 仅在 {@link #assistantId} 为空时用于解析助手。
     */
    private String mode;

    /** 历史对话，按 [{role, content}] 传递，role 仅支持 user/assistant。 */
    private List<HistoryMessage> history;

    /**
     * 会话附加上下文，如上游组件的调试采样数据。
     * 由助手在构建提示词时按需取用，缺省为空。
     */
    private Map<String, Object> contextData;

    public static class HistoryMessage {

        private String role;
        private String content;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getAssistantId() {
        return assistantId;
    }

    public void setAssistantId(String assistantId) {
        this.assistantId = assistantId;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<HistoryMessage> getHistory() {
        return history;
    }

    public void setHistory(List<HistoryMessage> history) {
        this.history = history;
    }

    public Map<String, Object> getContextData() {
        return contextData;
    }

    public void setContextData(Map<String, Object> contextData) {
        this.contextData = contextData;
    }
}
