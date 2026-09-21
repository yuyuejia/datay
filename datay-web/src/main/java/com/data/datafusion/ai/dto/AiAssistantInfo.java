package com.data.datafusion.ai.dto;

import java.util.List;

/**
 * 对外暴露的 AI 助手描述，供前端动态渲染标题、示例与能力清单。
 */
public class AiAssistantInfo {

    private String id;
    private String name;
    private String description;
    private boolean allowWrites;
    private List<String> samplePrompts;
    private List<AiToolInfo> tools;

    public AiAssistantInfo(
        String id,
        String name,
        String description,
        boolean allowWrites,
        List<String> samplePrompts,
        List<AiToolInfo> tools
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.allowWrites = allowWrites;
        this.samplePrompts = samplePrompts;
        this.tools = tools;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isAllowWrites() {
        return allowWrites;
    }

    public List<String> getSamplePrompts() {
        return samplePrompts;
    }

    public List<AiToolInfo> getTools() {
        return tools;
    }
}
