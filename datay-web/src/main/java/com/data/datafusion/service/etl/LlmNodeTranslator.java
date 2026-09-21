package com.data.datafusion.service.etl;

import com.data.datafusion.ai.llm.AiProperties;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 「大模型」组件翻译器。
 * <p>设计器侧 {@code LlmComponent} 组件只需配置系统提示词与用户输入；接口地址、API Key、
 * 模型、温度、超时等连接参数统一读取系统配置（{@code datay.ai.*}），在生成任务定义时注入，
 * 避免在任务配置中暴露密钥。
 */
@Component
public class LlmNodeTranslator implements ETLNodeTranslator {

    /** 设计器侧组件类型。 */
    public static final String COMPONENT_TYPE = "LlmComponent";

    /** 翻译后使用的后端组件名。 */
    public static final String TARGET_COMPONENT = "LlmComponent";

    private final AiProperties aiProperties;

    public LlmNodeTranslator(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @Override
    public String supportedType() {
        return COMPONENT_TYPE;
    }

    @Override
    public void translate(ETLNodeTranslationContext context) {
        if (!aiProperties.isConfigured()) {
            throw new IllegalStateException("未配置大模型服务，请在系统配置中设置 datay.ai.api-key");
        }

        String userPrompt = context.getConfigString("userPrompt", null);
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            throw new IllegalArgumentException("大模型组件未配置用户输入");
        }

        Map<String, Object> unit = context.getUnit();
        unit.put(".name", TARGET_COMPONENT);
        unit.put("baseUrl", aiProperties.getBaseUrl());
        unit.put("apiKey", aiProperties.getApiKey());
        unit.put("model", aiProperties.getModel());
        unit.put("temperature", aiProperties.getTemperature());
        unit.put("timeoutSeconds", aiProperties.getTimeoutSeconds());
        unit.put("systemPrompt", context.getConfigString("systemPrompt", ""));
        unit.put("userPrompt", userPrompt);
    }
}
