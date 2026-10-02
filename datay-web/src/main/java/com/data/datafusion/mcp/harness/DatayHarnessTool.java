package com.data.datafusion.mcp.harness;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * DataY 管理 Harness 的工具契约。
 *
 * <p>与面向内置智能问数助手的 {@code com.data.datafusion.ai.tool.AiTool} 不同，
 * 本接口服务于「平台管理」场景：工具直接对数据源、ETL 任务、数据模型等领域对象做增删改查与运行类操作。
 * 两者故意分开，避免管理类写操作混入助手可下发给模型的工具清单。
 *
 * <p>实现类只需注册成 Spring Bean，{@link DatayHarnessToolRegistry} 会自动收集，
 * 并按名暴露到 {@code /mcp/datay} MCP 端点，无需改动中心化代码。
 *
 * @see DatayHarnessToolRegistry
 * @see DatayHarnessExecutor
 */
public interface DatayHarnessTool {

    /**
     * 工具名，需全局唯一，建议小写下划线风格（如 {@code datasource_create}）。
     */
    String name();

    /**
     * 工具用途描述，直接提供给模型判断「什么时候调用」。
     */
    String description();

    /**
     * 入参 JSON Schema（MCP {@code inputSchema} 结构）。
     */
    Map<String, Object> parametersSchema();

    /**
     * 是否具备副作用（写操作）。具备副作用的工具需要显式开启 {@code datay.mcp.harness.allow-mutating-tools} 才会执行。
     */
    default boolean mutating() {
        return false;
    }

    /**
     * 工具所属能力分组，用于 {@code datay_harness_catalog} 归类展示。
     *
     * <p>默认取工具名第一个下划线之前的前缀（如 {@code datasource_list} → {@code datasource}）；
     * 当工具名不以对象名开头时（如指标问数直接沿用 {@code list_metrics} 这类既有命名），
     * 实现类应显式覆盖本方法，避免在目录里散落成无意义的分组。
     */
    default String group() {
        String toolName = name();
        if (toolName == null) {
            return "other";
        }
        int separator = toolName.indexOf('_');
        return separator > 0 ? toolName.substring(0, separator) : "other";
    }

    /**
     * 执行该工具所需的 Spring Security 权限，为空表示只要通过令牌鉴权即可。
     *
     * <p>取值与 REST 层的 {@code @PreAuthorize} 保持一致，例如 {@code ROLE_ADMIN}。
     */
    default Set<String> requiredAuthorities() {
        return Set.of();
    }

    /**
     * 执行工具。
     *
     * @param arguments 调用方给出的参数（已反序列化为 Map，永不为 null）
     * @param context   执行上下文，携带已鉴权用户与租户信息
     * @return 工具执行结果，会被序列化为 JSON 回传
     */
    Object execute(Map<String, Object> arguments, DatayHarnessContext context) throws Exception;

    /**
     * 工具执行体，便于用 lambda 声明式地定义工具。
     */
    @FunctionalInterface
    interface Handler {
        Object handle(Map<String, Object> arguments, DatayHarnessContext context) throws Exception;
    }

    /**
     * 声明一个只读工具（无副作用）。
     */
    static DatayHarnessTool read(String name, String description, Map<String, Object> schema, Handler handler) {
        return of(name, description, schema, false, Set.of(), handler);
    }

    /**
     * 声明一个写工具（有副作用）。
     */
    static DatayHarnessTool write(String name, String description, Map<String, Object> schema, Handler handler) {
        return of(name, description, schema, true, Set.of(), handler);
    }

    /**
     * 声明一个需要额外权限的写工具。
     */
    static DatayHarnessTool write(
        String name,
        String description,
        Map<String, Object> schema,
        Set<String> requiredAuthorities,
        Handler handler
    ) {
        return of(name, description, schema, true, requiredAuthorities, handler);
    }

    /**
     * 完整形态的工厂方法。
     */
    static DatayHarnessTool of(
        String name,
        String description,
        Map<String, Object> schema,
        boolean mutating,
        Set<String> requiredAuthorities,
        Handler handler
    ) {
        return of(name, null, description, schema, mutating, requiredAuthorities, handler);
    }

    /**
     * 完整形态的工厂方法，并显式指定能力分组（分组为空时回退到按名字前缀推导）。
     */
    static DatayHarnessTool of(
        String name,
        String group,
        String description,
        Map<String, Object> schema,
        boolean mutating,
        Set<String> requiredAuthorities,
        Handler handler
    ) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Harness 工具名不能为空");
        }
        Map<String, Object> effectiveSchema = schema == null ? Map.of("type", "object") : schema;
        Set<String> authorities = requiredAuthorities == null ? Set.of() : Set.copyOf(requiredAuthorities);
        return new DatayHarnessTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return description == null ? name : description;
            }

            @Override
            public Map<String, Object> parametersSchema() {
                return effectiveSchema;
            }

            @Override
            public boolean mutating() {
                return mutating;
            }

            @Override
            public Set<String> requiredAuthorities() {
                return authorities;
            }

            @Override
            public String group() {
                return group == null || group.isBlank() ? DatayHarnessTool.super.group() : group;
            }

            @Override
            public Object execute(Map<String, Object> arguments, DatayHarnessContext context) throws Exception {
                return handler.handle(arguments == null ? Map.of() : arguments, context);
            }

            @Override
            public String toString() {
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("name", name);
                view.put("group", group());
                view.put("mutating", mutating);
                view.put("requiredAuthorities", authorities);
                return view.toString();
            }
        };
    }
}
