package com.data.datafusion.ai.assistant;

import com.data.datafusion.ai.SqlExtractor;
import com.data.datafusion.ai.tool.AiTool;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 助手契约。
 *
 * <p>一个助手代表一类面向特定场景的对话能力，自行定义：角色提示词、可用工具、
 * 输出抽取规则以及安全边界（是否允许写操作、是否允许调用有副作用的工具）。
 *
 * <p>实现类通过 {@code @Component} 注册到 Spring 容器后会被
 * {@link AiAssistantRegistry} 自动收集，新增助手无需改动 Agent 或接口层。
 * 多助手共存时可通过 {@link org.springframework.core.annotation.Order @Order}
 * 指定展示顺序，序号最小者作为缺省助手。
 */
public interface AiAssistant {

    /**
     * 助手唯一标识，前端通过该值选择助手，建议小写中划线风格。
     */
    String id();

    /**
     * 助手展示名称。
     */
    String displayName();

    /**
     * 助手能力简介，会展示在前端空状态中。
     */
    default String description() {
        return displayName();
    }

    /**
     * 是否允许模型产出 DDL/DML 等写操作 SQL。默认只读。
     */
    default boolean allowWrites() {
        return false;
    }

    /**
     * 是否允许模型调用具备副作用的工具。默认关闭；即便返回 true，
     * 仍需全局配置 {@code datay.ai.allow-mutating-tools} 一并放行。
     */
    default boolean allowMutatingTools() {
        return false;
    }

    /**
     * 示例提问，供前端引导用户；返回空表示不展示示例。
     */
    default List<String> samplePrompts() {
        return List.of();
    }

    /**
     * 构建本轮会话的系统提示词。
     *
     * @param context 会话上下文，携带当前数据源与用户诉求
     */
    String buildSystemPrompt(AiAssistantContext context);

    /**
     * 工具轮次耗尽时的收敛提示词，用于强制模型给出最终答案。
     */
    String finalizeInstruction();

    /**
     * 从模型回复中抽取最终 SQL，抽取失败返回 null。
     * 默认按 {@link #allowWrites()} 决定是否接受写操作语句。
     */
    default String extractSql(String content) {
        return SqlExtractor.extract(content, allowWrites());
    }

    /**
     * 从模型回复中抽取结构化产物（如看板 spec），不支持该产物的助手返回 null。
     */
    default Map<String, Object> extractSpec(String content) {
        return null;
    }

    /**
     * 该助手可使用的工具名集合。
     *
     * <p>必须显式声明，作为该助手工具能力的唯一来源：只有出现在此集合中的工具，
     * 才会在会话中暴露给模型。工具名需与 {@link AiTool#name()} 完全一致，
     * 建议直接引用工具类上的 {@code NAME} 常量，避免拼写漂移。
     */
    Set<String> toolNames();

    /**
     * 该助手是否可使用指定工具。默认依据 {@link #toolNames()} 判断。
     */
    default boolean supportsTool(AiTool tool) {
        return tool != null && tool.name() != null && toolNames().contains(tool.name());
    }
}
