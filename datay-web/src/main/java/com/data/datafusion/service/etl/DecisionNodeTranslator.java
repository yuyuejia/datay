package com.data.datafusion.service.etl;

import com.data.datafusion.ai.decision.DecisionConfig;
import com.data.datafusion.ai.decision.DecisionConfigService;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 「决策模型」组件翻译器。
 * <p>设计器侧 {@code DecisionComponent} 组件只需配置命名问题与 state 取值方式；接口地址、
 * API Key、模型、超时等连接参数统一读取服务配置（{@code dp_service_config} 的 {@code decision} 分组），
 * 在生成任务定义时注入，避免在任务配置中暴露密钥。
 */
@Component
public class DecisionNodeTranslator implements ETLNodeTranslator {

    /** 设计器侧组件类型。 */
    public static final String COMPONENT_TYPE = "DecisionComponent";

    /** 翻译后使用的后端组件名。 */
    public static final String TARGET_COMPONENT = "DecisionComponent";

    private final DecisionConfigService decisionConfigService;

    public DecisionNodeTranslator(DecisionConfigService decisionConfigService) {
        this.decisionConfigService = decisionConfigService;
    }

    @Override
    public String supportedType() {
        return COMPONENT_TYPE;
    }

    @Override
    public void translate(ETLNodeTranslationContext context) {
        DecisionConfig config = decisionConfigService.getConfig();
        if (!config.isConfigured()) {
            throw new IllegalStateException("未配置决策模型服务，请在「服务配置 · 决策模型」中设置接口地址与 API Key");
        }

        String questions = context.getConfigString("questions", null);
        if (questions == null || questions.trim().isEmpty()) {
            throw new IllegalArgumentException("决策模型组件未配置问题（questions）");
        }

        Map<String, Object> unit = context.getUnit();
        unit.put(".name", TARGET_COMPONENT);
        unit.put("baseUrl", config.getBaseUrl());
        unit.put("apiKey", config.getApiKey());
        unit.put("model", config.getModel());
        unit.put("timeoutSeconds", config.getTimeoutSeconds());
        unit.put("questions", questions);
        putIfPresent(context, unit, "state");
        putIfPresent(context, unit, "stateFields");
        putIfPresent(context, unit, "answerPrefix");
        putIfPresent(context, unit, "includeConfidence");
        putIfPresent(context, unit, "includeProbabilities");
        putIfPresent(context, unit, "resultField");
    }

    private void putIfPresent(ETLNodeTranslationContext context, Map<String, Object> unit, String key) {
        Object value = context.getConfig().get(key);
        if (value != null) {
            unit.put(key, value);
        }
    }
}
