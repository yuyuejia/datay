package com.data.datafusion.ai.assistant;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI 助手注册中心。
 *
 * <p>收集容器内所有 {@link AiAssistant} 实现并按 id 索引。Spring 构造注入
 * {@code List<AiAssistant>} 会带上全部实现，因此接入新助手是纯粹的「加一个 Bean」。
 * 列表顺序遵循 {@code @Order}，序号最小者作为缺省助手。
 */
@Component
public class AiAssistantRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(AiAssistantRegistry.class);

    private final Map<String, AiAssistant> assistants = new LinkedHashMap<>();

    public AiAssistantRegistry(List<AiAssistant> discovered) {
        if (discovered != null) {
            for (AiAssistant assistant : discovered) {
                register(assistant);
            }
        }
        LOG.info("AI assistant registry initialized, {} assistant(s) registered: {}", assistants.size(), assistants.keySet());
    }

    private void register(AiAssistant assistant) {
        if (assistant == null || assistant.id() == null || assistant.id().isBlank()) {
            LOG.warn("Skip registering AI assistant with blank id: {}", assistant);
            return;
        }
        AiAssistant existing = assistants.putIfAbsent(assistant.id(), assistant);
        if (existing != null) {
            throw new IllegalStateException("Duplicate AI assistant id detected: " + assistant.id());
        }
    }

    public Optional<AiAssistant> find(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(assistants.get(id.trim()));
    }

    public Collection<AiAssistant> all() {
        return Collections.unmodifiableCollection(assistants.values());
    }

    /**
     * 缺省助手：注册顺序中的第一个，通常为只读查询助手。
     *
     * @throws IllegalStateException 容器中没有任何助手时抛出
     */
    public AiAssistant defaultAssistant() {
        return assistants
            .values()
            .stream()
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("未注册任何 AI 助手"));
    }
}
