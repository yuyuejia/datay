package com.data.datafusion.ai.tool;

import com.data.datafusion.ai.assistant.EtlComponentDoc;
import com.data.datafusion.ai.assistant.EtlComponentDocRegistry;
import com.data.datafusion.service.ETLComponentCatalogService;
import com.data.datafusion.service.dto.ETLComponentDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * ETL 组件详细说明工具（只读）。
 *
 * <p>从一个或多个帮助文档中提取组件功能、适用场景、配置参数、示例与注意事项。
 * 一次支持多个组件类型，减少 Agent 的工具调用轮次；没有帮助文档的组件会返回骨架提示。
 */
@Component
public class DescribeEtlComponentsTool implements AiTool {

    /** 工具名常量。 */
    public static final String NAME = "describe_etl_components";

    private static final int MAX_TYPES = 10;
    private static final int MAX_TABLES = 8;
    private static final int MAX_TABLE_ROWS = 40;
    private static final int MAX_RAW_DOC_CHARS = 20000;

    /** 设计器节点 config 中可直接使用的字段。 */
    private static final Set<String> DESIGNER_FIELDS = Set.of(
        "sourceId",
        "schema",
        "table",
        "tables",
        "outputTable",
        "where",
        "incrColumn",
        "incrColumns",
        "model",
        "modelId",
        "sql",
        "rules",
        "filters",
        "filterLogic",
        "selectColumns",
        "joins",
        "fromTable",
        "header_map",
        "updateColumn",
        "maxRows",
        "filePath",
        "format",
        "csvDelimiter",
        "csvHasHeader",
        "excelSheet",
        "matchMode",
        "attributeName",
        "dataField",
        "defaultLabel",
        "logLevel",
        "logDataContent",
        "logAttributes",
        "logDataFormat",
        "logTimestamp",
        "maxDataLength",
        "dataFormat",
        "inputData",
        "delimiter",
        "hasHeader",
        "fieldMapping",
        "loopCount",
        "waitTime",
        "batchSize",
        "transaction",
        "ignoreError",
        "eventTypes",
        "queryTimeout",
        "systemPrompt",
        "userPrompt",
        "questions",
        "decisionModel",
        "collection",
        "database",
        "topic",
        "topics",
        "index",
        "tableName",
        "tableNamePattern",
        "databaseNamePattern",
        "startPosition",
        "snapshot",
        "fetchSize",
        "timeout",
        "allowModifySchema"
    );

    /** 帮助文档中的引擎级字段，不能写入设计器节点 config。 */
    private static final Set<String> ENGINE_FIELDS = Set.of(
        ".id",
        ".name",
        ".class",
        "parallelism",
        "status",
        "datasource",
        "url",
        "driver",
        "username",
        "password",
        "dbschema",
        "classname"
    );

    private static final List<String> GLOBAL_DESIGNER_NOTES = List.of(
        "DataY 设计器中数据源统一使用 sourceId（数据源 ID）+ schema；文档中的 datasource、sourceId.url、sourceId.username、sourceId.password 属于引擎级配置，不要写入节点 config。",
        "不要输出 .id、.name、.class、parallelism、status 等引擎元数据字段。",
        "文档示例中的 password、secret、token 等敏感值已脱敏，不要把示例值写入配置。"
    );

    private final ETLComponentCatalogService catalogService;
    private final EtlComponentDocRegistry docRegistry;

    public DescribeEtlComponentsTool(ETLComponentCatalogService catalogService, EtlComponentDocRegistry docRegistry) {
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
            获取一个或多个 ETL 组件的详细功能说明、适用场景、配置参数、示例与注意事项。
            - 生成任何节点配置前必须先调用本工具，不要凭记忆编造组件配置；
            - 一次可传多个组件类型（建议不超过 10 个），减少工具调用轮次；
            - 返回字段中的 scope=designer 表示可直接写入设计器节点 config，scope=engine 表示引擎级字段、不要写入；
            - docAvailable=false 表示该组件暂无详细帮助文档，只能生成节点骨架，config 留空。
            """;
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> types = new LinkedHashMap<>();
        types.put("type", "array");
        types.put("description", "组件编码列表，例如 [\"StreamJdbcInput\",\"Join\",\"StreamJdbcOutput\"]，单次最多 10 个");
        Map<String, Object> items = new LinkedHashMap<>();
        items.put("type", "string");
        types.put("items", items);
        properties.put("types", types);

        Map<String, Object> includeRawDoc = new LinkedHashMap<>();
        includeRawDoc.put("type", "boolean");
        includeRawDoc.put("description", "是否返回 Markdown 原文，默认 false；仅在结构化字段不足时使用");
        properties.put("includeRawDoc", includeRawDoc);

        schema.put("properties", properties);
        schema.put("required", List.of("types"));
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments, AiToolContext context) {
        List<String> requested = toStringList(arguments.get("types"));
        boolean includeRawDoc = Boolean.parseBoolean(String.valueOf(arguments.getOrDefault("includeRawDoc", false)));

        Map<String, ETLComponentDTO> catalog = catalogIndex();
        List<String> notProcessed = new ArrayList<>();
        if (requested.size() > MAX_TYPES) {
            notProcessed.addAll(requested.subList(MAX_TYPES, requested.size()));
            requested = requested.subList(0, MAX_TYPES);
        }

        List<Map<String, Object>> components = new ArrayList<>();
        List<Map<String, Object>> unknown = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String rawType : requested) {
            if (rawType == null || rawType.isBlank()) {
                continue;
            }
            String type = rawType.trim();
            String key = type.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) {
                continue;
            }
            ETLComponentDTO dto = catalog.get(key);
            if (dto == null) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("type", type);
                item.put("message", "组件不存在，请先用 list_etl_components 查询可用的组件编码");
                unknown.add(item);
                continue;
            }
            components.add(toComponentMap(dto, docRegistry.find(dto.getCode()).orElse(null), includeRawDoc));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("components", components);
        result.put("unknown", unknown);
        if (!notProcessed.isEmpty()) {
            result.put("notProcessed", notProcessed);
            result.put("message", "单次最多处理 10 个组件，剩余类型请再次调用本工具");
        }
        return result;
    }

    private Map<String, Object> toComponentMap(ETLComponentDTO dto, EtlComponentDoc doc, boolean includeRawDoc) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", dto.getCode());
        item.put("name", nullSafe(dto.getName()));
        item.put("group", nullSafe(dto.getGroup()));
        item.put("docAvailable", doc != null);
        item.put(
            "description",
            doc != null && doc.overview() != null && !doc.overview().isBlank() ? doc.overview() : nullSafe(dto.getDesc())
        );

        if (doc == null) {
            item.put("features", List.of());
            item.put("configTables", List.of());
            item.put("designerConfigTables", List.of());
            item.put("designerNotes", designerNotes(List.of("该组件暂无详细帮助文档，AI 只生成节点骨架，请手动配置。")));
            item.put("examples", List.of());
            item.put("notes", List.of());
            item.put("skeletonOnly", true);
            return item;
        }

        item.put("features", doc.features());
        item.put("configTables", toConfigTables(doc.configTables()));
        item.put("designerConfigTables", toConfigTables(doc.designerConfigTables()));
        item.put("designerNotes", designerNotes(doc.designerNotes()));
        item.put("examples", toExamples(doc.examples()));
        item.put("notes", doc.notes());
        item.put("docExcerpt", doc.docExcerpt());
        item.put("truncated", doc.truncated());
        item.put("skeletonOnly", doc.configTables().isEmpty() && doc.designerConfigTables().isEmpty());
        if (includeRawDoc) {
            item.put("rawDoc", truncate(doc.rawMarkdown(), MAX_RAW_DOC_CHARS));
        }
        return item;
    }

    private List<Map<String, Object>> toConfigTables(List<EtlComponentDoc.ConfigTable> tables) {
        List<Map<String, Object>> result = new ArrayList<>();
        int tableIndex = 0;
        for (EtlComponentDoc.ConfigTable table : tables) {
            if (tableIndex++ >= MAX_TABLES) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("section", table.section());
            item.put("headers", table.headers());
            List<Map<String, String>> rows = new ArrayList<>();
            int rowIndex = 0;
            for (Map<String, String> row : table.rows()) {
                if (rowIndex++ >= MAX_TABLE_ROWS) {
                    break;
                }
                Map<String, String> enriched = new LinkedHashMap<>(row);
                String field = fieldName(row, table.headers());
                if (!field.isBlank()) {
                    enriched.put("scope", scopeOf(table.section(), field));
                }
                rows.add(enriched);
            }
            item.put("rows", rows);
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> toExamples(List<EtlComponentDoc.CodeExample> examples) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (EtlComponentDoc.CodeExample example : examples) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("language", example.language());
            item.put("content", example.content());
            result.add(item);
        }
        return result;
    }

    private List<String> designerNotes(List<String> docNotes) {
        LinkedHashSet<String> notes = new LinkedHashSet<>(GLOBAL_DESIGNER_NOTES);
        if (docNotes != null) {
            notes.addAll(docNotes);
        }
        return new ArrayList<>(notes);
    }

    private String fieldName(Map<String, String> row, List<String> headers) {
        for (String header : headers) {
            String lower = header.toLowerCase(Locale.ROOT);
            if (lower.contains("参数名") || lower.contains("字段") || lower.contains("参数")) {
                String value = row.get(header);
                if (value != null && !value.isBlank()) {
                    return value.replace("`", "").trim();
                }
            }
        }
        // 没有明确的字段列时，取第一列
        if (!headers.isEmpty()) {
            String value = row.get(headers.get(0));
            if (value != null) {
                return value.replace("`", "").trim();
            }
        }
        return "";
    }

    private String scopeOf(String section, String field) {
        String lowerField = field.toLowerCase(Locale.ROOT);
        String lowerSection = section == null ? "" : section.toLowerCase(Locale.ROOT);
        if (lowerSection.contains("设计器")) {
            return "designer";
        }
        if (
            lowerField.startsWith(".") ||
            lowerField.startsWith("sourceid.") ||
            lowerField.startsWith("datasource.") ||
            lowerField.startsWith("extraparams.") ||
            ENGINE_FIELDS.contains(lowerField)
        ) {
            return "engine";
        }
        if (DESIGNER_FIELDS.contains(field) || DESIGNER_FIELDS.contains(lowerField)) {
            return "designer";
        }
        return "unknown";
    }

    private Map<String, ETLComponentDTO> catalogIndex() {
        Map<String, ETLComponentDTO> index = new LinkedHashMap<>();
        for (ETLComponentDTO dto : catalogService.listCatalog()) {
            if (dto != null && dto.getCode() != null) {
                index.put(dto.getCode().trim().toLowerCase(Locale.ROOT), dto);
            }
        }
        return index;
    }

    private List<String> toStringList(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    values.add(String.valueOf(item).trim());
                }
            }
        } else if (raw != null && !String.valueOf(raw).isBlank()) {
            values.add(String.valueOf(raw).trim());
        }
        return values;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n...(已截断)";
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
