package com.data.datafusion.ai.tool;

import com.data.datafusion.ai.assistant.AiAssistant;
import com.data.ai.llm.ToolDefinition;
import java.util.ArrayList;
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
 * AI 工具注册中心。
 *
 * <p>收集容器中所有 {@link AiTool} 实现，并按工具名索引，向上层提供：
 * <ul>
 *     <li>指定助手可用的工具集合（{@link #allowed(boolean, AiAssistant)}）与可下发给模型的工具定义（{@link #definitions(boolean, AiAssistant)}）</li>
 *     <li>按名查找与执行（{@link #find(String)}）</li>
 * </ul>
 *
 * <p>Spring 构造注入 {@code List<AiTool>} 会带上容器内全部实现，
 * 因此接入新工具是纯粹的「加一个 Bean」，不涉及任何中心化改动。
 */
@Component
public class AiToolRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(AiToolRegistry.class);

    private final Map<String, AiTool> tools = new LinkedHashMap<>();

    public AiToolRegistry(List<AiTool> aiTools) {
        if (aiTools != null) {
            for (AiTool tool : aiTools) {
                register(tool);
            }
        }
        LOG.info("AI tool registry initialized, {} tool(s) registered: {}", tools.size(), tools.keySet());
    }

    /**
     * 注册单个工具，同名工具会被拒绝，避免模型端出现歧义。
     */
    public void register(AiTool tool) {
        if (tool == null || tool.name() == null || tool.name().isBlank()) {
            LOG.warn("Skip registering AI tool with blank name: {}", tool);
            return;
        }
        AiTool existing = tools.putIfAbsent(tool.name(), tool);
        if (existing != null) {
            throw new IllegalStateException("Duplicate AI tool name detected: " + tool.name());
        }
    }

    public Optional<AiTool> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(tools.get(name));
    }

    public Collection<AiTool> all() {
        return Collections.unmodifiableCollection(tools.values());
    }

    /**
     * 解析指定助手在当前安全边界下可用的工具集合。
     *
     * @param allowMutating 为 false 时只暴露只读工具，防止模型触发写操作
     * @param assistant 目标助手，仅暴露其在 {@link AiAssistant#toolNames()} 中显式声明的工具
     */
    public List<AiTool> allowed(boolean allowMutating, AiAssistant assistant) {
        List<AiTool> allowed = new ArrayList<>();
        for (AiTool tool : tools.values()) {
            if (tool.mutating() && !allowMutating) {
                continue;
            }
            if (!assistant.supportsTool(tool)) {
                continue;
            }
            allowed.add(tool);
        }
        return allowed;
    }

    /**
     * 构建可直接注入 LLM 请求的工具定义，等价于对 {@link #allowed(boolean, AiAssistant)} 的映射。
     */
    public List<ToolDefinition> definitions(boolean allowMutating, AiAssistant assistant) {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (AiTool tool : allowed(allowMutating, assistant)) {
            definitions.add(new ToolDefinition(tool.name(), tool.description(), tool.parametersSchema()));
        }
        return definitions;
    }
}
