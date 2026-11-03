package com.data.datafusion.ai.tool;

import com.data.datafusion.service.DataModelService;
import com.data.datafusion.service.dto.DataModelDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 数据模型清单工具（只读）。
 *
 * <p>生成「模型写入」节点前，用本工具选择真实的数据模型。
 * 返回模型绑定的数据源、schema 与物理表，避免 AI 编造 modelId。
 */
@Component
public class ListDataModelsTool implements AiTool {

    /** 工具名常量。 */
    public static final String NAME = "list_data_models";

    private static final int MAX_RESULTS = 300;

    private final DataModelService dataModelService;

    public ListDataModelsTool(DataModelService dataModelService) {
        this.dataModelService = dataModelService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return """
            列出当前租户下已创建的数据模型，返回 id、名称、编码、绑定的数据源、schema 与物理表。
            生成「模型写入」节点前必须先调用本工具选择真实模型，禁止编造 modelId。
            可通过 keyword 按名称或编码过滤。
            """;
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> keyword = new LinkedHashMap<>();
        keyword.put("type", "string");
        keyword.put("description", "按模型名称或编码模糊过滤，可选");
        properties.put("keyword", keyword);
        schema.put("properties", properties);
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments, AiToolContext context) {
        String keyword = arguments.get("keyword") == null ? null : String.valueOf(arguments.get("keyword")).trim();
        List<Map<String, Object>> models = new ArrayList<>();
        for (DataModelDTO dto : dataModelService.findAll()) {
            if (dto == null) {
                continue;
            }
            if (keyword != null && !keyword.isBlank() && !matches(dto, keyword)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", dto.getId());
            item.put("name", nullSafe(dto.getName()));
            item.put("code", nullSafe(dto.getCode()));
            item.put("modelType", nullSafe(dto.getModelType()));
            item.put("dataSourceId", nullSafe(dto.getDataSourceId()));
            item.put("schemaName", nullSafe(dto.getSchemaName()));
            item.put("tableName", nullSafe(dto.getTableName()));
            models.add(item);
            if (models.size() >= MAX_RESULTS) {
                break;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", models.size());
        result.put("models", models);
        return result;
    }

    private boolean matches(DataModelDTO dto, String keyword) {
        String needle = keyword.toLowerCase(Locale.ROOT);
        return (
            nullSafe(dto.getName()).toLowerCase(Locale.ROOT).contains(needle) ||
            nullSafe(dto.getCode()).toLowerCase(Locale.ROOT).contains(needle)
        );
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }
}
