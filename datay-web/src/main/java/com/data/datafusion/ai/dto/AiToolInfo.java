package com.data.datafusion.ai.dto;

import java.util.Map;

/**
 * 对外暴露的已注册工具信息，供前端展示助手能力清单。
 */
public class AiToolInfo {

    private String name;
    private String description;
    private boolean mutating;
    private Map<String, Object> parameters;

    public AiToolInfo(String name, String description, boolean mutating, Map<String, Object> parameters) {
        this.name = name;
        this.description = description;
        this.mutating = mutating;
        this.parameters = parameters;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isMutating() {
        return mutating;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }
}
