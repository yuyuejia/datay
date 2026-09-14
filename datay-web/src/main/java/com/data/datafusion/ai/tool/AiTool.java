package com.data.datafusion.ai.tool;

import com.data.datafusion.ai.AiSqlMode;
import java.util.Map;

/**
 * 可被 AI Agent 调用（function calling）的工具契约。
 *
 * <p>实现类通过 {@code @Component} 注册到 Spring 容器后，会被
 * {@link AiToolRegistry} 自动收集，无需手工登记。新增工具只需实现本接口，
 * 并保证 {@link #name()} 全局唯一即可。
 */
public interface AiTool {

    /**
     * 工具名，需与 LLM function calling 协议中的 name 完全一致（建议小写下划线风格）。
     */
    String name();

    /**
     * 工具用途描述，直接提供给模型判断「什么时候调用」。
     */
    String description();

    /**
     * 入参 JSON Schema（OpenAI function.parameters 结构），描述模型应如何构造参数。
     */
    Map<String, Object> parametersSchema();

    /**
     * 是否具备副作用（写操作）。具备副作用的工具默认不会暴露给模型，除非显式开启写操作。
     */
    default boolean mutating() {
        return false;
    }

    /**
     * 该工具是否适用于指定的生成模式。默认对所有模式开放，
     * 工具可重写以限制仅在特定场景（如只读查询）下暴露给模型。
     */
    default boolean supports(AiSqlMode mode) {
        return true;
    }

    /**
     * 执行工具。
     *
     * @param arguments 模型给出的参数（已反序列化为 Map）
     * @param context 执行上下文，携带当前数据源、会话等运行时信息
     * @return 工具执行结果，会被序列化后回传给模型
     */
    Object execute(Map<String, Object> arguments, AiToolContext context) throws Exception;
}
