package com.data.datafusion.ai.tool;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.DataSourceQueryService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.dto.DataSourceDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 维度取值抽样工具（只读）。
 *
 * <p>当用户给出具体取值（如「北京」）时，模型可抽样维度字段的真实取值，
 * 以确认该值应映射到哪个维度字段（如 city 还是 province），从而构造准确的业务限定。
 */
@Component
public class MetricDimensionValueTool implements AiTool {

    /** 工具名常量，供助手显式声明工具集合时引用，避免字符串拼写漂移。 */
    public static final String NAME = "sample_dimension_values";

    private static final Logger LOG = LoggerFactory.getLogger(MetricDimensionValueTool.class);

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 50;

    private final DataModelRepository dataModelRepository;
    private final ModelFieldRepository modelFieldRepository;
    private final DataSourceService dataSourceService;
    private final DataSourceQueryService dataSourceQueryService;

    public MetricDimensionValueTool(
        DataModelRepository dataModelRepository,
        ModelFieldRepository modelFieldRepository,
        DataSourceService dataSourceService,
        DataSourceQueryService dataSourceQueryService
    ) {
        this.dataModelRepository = dataModelRepository;
        this.modelFieldRepository = modelFieldRepository;
        this.dataSourceService = dataSourceService;
        this.dataSourceQueryService = dataSourceQueryService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return """
            抽样某个维度字段的去重取值，用于把用户给出的具体值（如「北京」）映射到正确的维度字段。
            入参：dimensionModelCode（维度模型编码，来自 describe_metrics）、fieldName（维度字段名）、
            keyword（可选，按取值模糊过滤）、limit（可选，默认 20，最大 50）。
            当不确定业务限定取值属于城市、省份还是大区时，可分别抽样确认后再构造 conditions。
            """;
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("dimensionModelCode", Map.of("type", "string", "description", "维度模型编码"));
        props.put("fieldName", Map.of("type", "string", "description", "维度字段名"));
        props.put("keyword", Map.of("type", "string", "description", "可选，取值模糊过滤关键字"));
        props.put("limit", Map.of("type", "integer", "description", "返回条数上限，默认 20"));
        schema.put("properties", props);
        schema.put("required", List.of("dimensionModelCode", "fieldName"));
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments, AiToolContext context) throws Exception {
        String code = asString(arguments.get("dimensionModelCode"));
        String fieldName = asString(arguments.get("fieldName"));
        if (code == null || code.isBlank() || fieldName == null || fieldName.isBlank()) {
            return Map.of("error", "缺少参数 dimensionModelCode 或 fieldName");
        }

        DataModel dimensionModel = dataModelRepository.findFirstByCode(code).orElse(null);
        if (dimensionModel == null) {
            return Map.of("error", "维度模型不存在: " + code);
        }
        if (dimensionModel.getDataSourceId() == null) {
            return Map.of("error", "维度模型未绑定数据源: " + code);
        }
        boolean fieldExists = modelFieldRepository
            .findByModelIdOrderBySortOrderAsc(dimensionModel.getId())
            .stream()
            .map(ModelField::getFieldName)
            .anyMatch(fieldName::equals);
        if (!fieldExists) {
            return Map.of("error", "维度字段不存在: " + code + "." + fieldName);
        }

        DataSourceDTO dataSource = dataSourceService.findOne(dimensionModel.getDataSourceId()).orElse(null);
        if (dataSource == null) {
            return Map.of("error", "数据源不存在: " + dimensionModel.getDataSourceId());
        }

        String keyword = asString(arguments.get("keyword"));
        int limit = parseLimit(arguments.get("limit"));
        String column = fieldName;
        StringBuilder sql = new StringBuilder("SELECT DISTINCT ").append(column).append(" FROM ").append(physicalTableName(dimensionModel));
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" WHERE ").append(column).append(" LIKE ").append(quote("%" + keyword.trim() + "%"));
        }
        sql.append(" ORDER BY 1 LIMIT ").append(limit);

        try {
            Map<String, Object> queryResult = dataSourceQueryService.executeQuery(dataSource, sql.toString());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("dimensionModelCode", code);
            result.put("fieldName", fieldName);
            result.put("values", extractValues(queryResult.get("rows")));
            return result;
        } catch (Exception e) {
            LOG.debug("AI tool sample_dimension_values failed: {}", e.getMessage());
            return Map.of("error", "维度取值抽样失败: " + e.getMessage());
        }
    }

    private List<Object> extractValues(Object rows) {
        List<Object> values = new ArrayList<>();
        if (rows instanceof List<?> list) {
            for (Object row : list) {
                if (row instanceof Map<?, ?> map && !map.isEmpty()) {
                    values.add(map.values().iterator().next());
                }
            }
        }
        return values;
    }

    private static int parseLimit(Object raw) {
        int limit = DEFAULT_LIMIT;
        if (raw instanceof Number number) {
            limit = number.intValue();
        } else if (raw != null && !String.valueOf(raw).isBlank()) {
            try {
                limit = Integer.parseInt(String.valueOf(raw).trim());
            } catch (NumberFormatException ignored) {
                // 保持默认值
            }
        }
        return Math.max(1, Math.min(limit, MAX_LIMIT));
    }

    private static String physicalTableName(DataModel model) {
        List<String> parts = new ArrayList<>();
        if (model.getSchemaName() != null && !model.getSchemaName().isBlank()) {
            parts.add(model.getSchemaName());
        }
        if (model.getTableName() != null && !model.getTableName().isBlank()) {
            parts.add(model.getTableName());
        } else if (model.getCode() != null && !model.getCode().isBlank()) {
            parts.add(model.getCode());
        }
        return String.join(".", parts);
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
