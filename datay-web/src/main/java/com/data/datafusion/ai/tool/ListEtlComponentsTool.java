package com.data.datafusion.ai.tool;

import com.data.datafusion.ai.assistant.EtlComponentDocRegistry;
import com.data.datafusion.service.ETLComponentCatalogService;
import com.data.datafusion.service.dto.ETLComponentDTO;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * ETL 组件目录工具（只读）。
 *
 * <p>返回全部可用的 ETL 组件及简要说明，供 AI 在生成任务前选择节点类型。
 * {@code docAvailable=false} 表示该组件暂无详细帮助文档，AI 只能生成节点骨架，
 * 配置需要用户在设计器中手动补全。
 */
@Component
public class ListEtlComponentsTool implements AiTool {

    /** 工具名常量。 */
    public static final String NAME = "list_etl_components";

    private static final int MAX_RESULTS = 500;

    private final ETLComponentCatalogService catalogService;
    private final EtlComponentDocRegistry docRegistry;

    public ListEtlComponentsTool(ETLComponentCatalogService catalogService, EtlComponentDocRegistry docRegistry) {
        this.catalogService = catalogService;
        this.docRegistry = docRegistry;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return """
            列出 DataY 中全部可用的 ETL 组件，返回编码、名称、分组、简短描述以及是否有详细帮助文档。
            - 生成 ETL 任务前，先用本工具确认可用的组件编码；
            - docAvailable=true 的组件可以进一步调用 describe_etl_components 获取功能与配置说明；
            - docAvailable=false 的组件只能生成节点骨架，配置需要用户手动补全；
            - 可通过 keyword 按名称/编码/描述过滤，通过 group 按分类过滤。
            """;
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "关键字，按组件编码、名称或描述模糊过滤，可选");
        properties.put("keyword", keyword);

        Map<String, Object> group = new LinkedHashMap<>();
        group.put("type", "string");
        group.put("description", "组件分组，如 数据输入、数据处理、数据输出、实时输入、调试组件，可选");
        properties.put("group", group);

        Map<String, Object> onlyDocAvailable = new LinkedHashMap<>();
        onlyDocAvailable.put("type", "boolean");
        onlyDocAvailable.put("description", "是否只返回有详细帮助文档的组件，默认 false");
        properties.put("onlyDocAvailable", onlyDocAvailable);

        schema.put("properties", properties);
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments, AiToolContext context) {
        String keyword = asString(arguments.get("keyword"));
        String group = asString(arguments.get("group"));
        boolean onlyDocAvailable = Boolean.parseBoolean(String.valueOf(arguments.getOrDefault("onlyDocAvailable", false)));

        List<Map<String, Object>> components = new ArrayList<>();
        for (ETLComponentDTO dto : catalogService.listCatalog()) {
            if (dto == null || dto.getCode() == null) {
                continue;
            }
            if (keyword != null && !keyword.isBlank() && !matchesKeyword(dto, keyword)) {
                continue;
            }
            if (group != null && !group.isBlank() && !group.equalsIgnoreCase(nullSafe(dto.getGroup()))) {
                continue;
            }
            boolean docAvailable = docRegistry.contains(dto.getCode());
            if (onlyDocAvailable && !docAvailable) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", dto.getCode());
            item.put("name", nullSafe(dto.getName()));
            item.put("group", nullSafe(dto.getGroup()));
            item.put("desc", nullSafe(dto.getDesc()));
            item.put("docAvailable", docAvailable);
            components.add(item);
        }
        components.sort(
            Comparator.comparing((Map<String, Object> item) -> String.valueOf(item.get("group"))).thenComparing(item ->
                String.valueOf(item.get("code"))
            )
        );
        if (components.size() > MAX_RESULTS) {
            components = new ArrayList<>(components.subList(0, MAX_RESULTS));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", components.size());
        result.put("components", components);
        return result;
    }

    private boolean matchesKeyword(ETLComponentDTO dto, String keyword) {
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        return (
            nullSafe(dto.getCode()).toLowerCase(Locale.ROOT).contains(needle) ||
            nullSafe(dto.getName()).toLowerCase(Locale.ROOT).contains(needle) ||
            nullSafe(dto.getDesc()).toLowerCase(Locale.ROOT).contains(needle)
        );
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
