package com.data.datafusion.ai.dto;

import java.util.List;

/**
 * AI 生成 SQL 的请求体。
 */
public class AiSqlRequest {

    /** 用户本轮的自然语言需求。 */
    private String message;

    /** 当前会话绑定的数据源 ID，可选。 */
    private Long dataSourceId;

    /** 历史对话，按 [{role, content}] 传递，role 仅支持 user/assistant。 */
    private List<HistoryMessage> history;

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

    public List<HistoryMessage> getHistory() {
        return history;
    }

    public void setHistory(List<HistoryMessage> history) {
        this.history = history;
    }
}
