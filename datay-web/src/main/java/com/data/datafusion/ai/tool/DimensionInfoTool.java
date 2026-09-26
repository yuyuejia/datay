package com.data.datafusion.ai.tool;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 维度信息查询工具（只读）。
 *
 * <p>返回某个维度的基本信息（类型、是否层级/时间维度、层级数、主键）与完整字段清单，
 * 并给出推荐的展示字段，帮助模型在生成图表时选择正确的名称字段而非 id/sk 代理键。
 */
@Component
public class DimensionInfoTool implements AiTool {

    /** 工具名常量。 */
    public static final String NAME = "describe_dimension";

    private static final Logger LOG = LoggerFactory.getLogger(DimensionInfoTool.class);

    private static final String DIMENSION_KIND_HIERARCHY = "HIERARCHY";
    private static final String DIMENSION_KIND_TIME = "TIME";

    private static final Map<String, String> TIME_LEVEL_LABELS = Map.of(
        "YEAR",
        "年",
        "QUARTER",
        "季",
        "MONTH",
        "月",
        "DAY",
        "日",
        "WEEK_OF_YEAR",
        "周"
    );

    private static final Pattern PERIOD_FIELD_PATTERN = Pattern.compile(
        "(?i)^(year|quarter|month|week_of_year|week|day_of_week|day|full_date|date)(_id|_name)?$"
    );

    private final DataModelRepository dataModelRepository;
    private final ModelFieldRepository modelFieldRepository;

    public DimensionInfoTool(DataModelRepository dataModelRepository, ModelFieldRepository modelFieldRepository) {
        this.dataModelRepository = dataModelRepository;
        this.modelFieldRepository = modelFieldRepository;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return """
            查询指定维度的基本信息与字段信息（只读），用于确定图表的展示字段。
            入参 dimensionModelCode 为维度模型编码（来自 describe_metrics）。
            返回：
            - 基本信息：code、name、dimensionKind、isHierarchy、isTimeDimension、levelCount、primaryKeys；
            - fields：全部字段（fieldName、fieldType、description、isPrimaryKey、fieldRole、levelIndex）；
            - levels：层级维度的各层级 idField / nameField / granularity；
            - periodFields：时间维度可用的周期字段；
            - recommendedDisplayField：推荐的展示字段（业务名称字段）。
            生成图表时，请据此选择名称字段作为展示字段（dimensionFieldNames / dataRef.categoryField），
            不要使用 *_sk / *_id 代理键字段。
            """;
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> code = new LinkedHashMap<>();
        code.put("type", "string");
        code.put("description", "维度模型编码，例如 dim_store");
        props.put("dimensionModelCode", code);
        schema.put("properties", props);
        schema.put("required", List.of("dimensionModelCode"));
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments, AiToolContext context) {
        String code = arguments.get("dimensionModelCode") == null ? null : String.valueOf(arguments.get("dimensionModelCode"));
        if (code == null || code.isBlank()) {
            return Map.of("error", "缺少参数 dimensionModelCode");
        }
        Optional<DataModel> modelOpt = dataModelRepository.findFirstByCode(code);
        if (modelOpt.isEmpty()) {
            return Map.of("error", "维度模型不存在：" + code);
        }
        DataModel model = modelOpt.get();
        if (!"DIMENSION".equalsIgnoreCase(model.getModelType())) {
            return Map.of("error", "不是维度模型：" + code);
        }

        List<ModelField> fields = modelFieldRepository.findByModelIdOrderBySortOrderAsc(model.getId());
        boolean hierarchy = isHierarchy(model.getDimensionKind());
        boolean timeDimension = DIMENSION_KIND_TIME.equalsIgnoreCase(model.getDimensionKind());
        int levelCount = hierarchy ? resolveLevelCount(model) : 0;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", model.getId());
        result.put("code", model.getCode());
        result.put("name", model.getName());
        if (model.getDescription() != null && !model.getDescription().isBlank()) {
            result.put("description", model.getDescription());
        }
        result.put("dimensionKind", model.getDimensionKind());
        result.put("isHierarchy", hierarchy);
        result.put("isTimeDimension", timeDimension);
        if (hierarchy) {
            result.put("levelCount", levelCount);
        }

        List<String> primaryKeys = new ArrayList<>();
        List<Map<String, Object>> fieldItems = new ArrayList<>();
        List<String> periodFields = new ArrayList<>();
        for (ModelField field : fields) {
            Map<String, Object> fieldItem = new LinkedHashMap<>();
            fieldItem.put("fieldName", field.getFieldName());
            fieldItem.put("fieldType", field.getFieldType());
            if (field.getDescription() != null && !field.getDescription().isBlank()) {
                fieldItem.put("description", field.getDescription());
            }
            if (Boolean.TRUE.equals(field.getIsPrimaryKey())) {
                fieldItem.put("isPrimaryKey", true);
                primaryKeys.add(field.getFieldName());
            }
            if (field.getFieldRole() != null && !field.getFieldRole().isBlank()) {
                fieldItem.put("fieldRole", field.getFieldRole());
            }
            if (field.getLevelIndex() != null) {
                fieldItem.put("levelIndex", field.getLevelIndex());
            }
            fieldItems.add(fieldItem);
            if (field.getFieldName() != null && PERIOD_FIELD_PATTERN.matcher(field.getFieldName()).matches()) {
                periodFields.add(field.getFieldName());
            }
        }
        result.put("primaryKeys", primaryKeys);
        result.put("fields", fieldItems);

        if (hierarchy) {
            List<Map<String, Object>> levels = new ArrayList<>();
            for (int level = 1; level <= levelCount; level++) {
                Map<String, Object> levelItem = new LinkedHashMap<>();
                String nameField = levelFieldName(fields, level, "LEVEL_NAME");
                levelItem.put("levelIndex", level);
                levelItem.put("idField", levelFieldName(fields, level, "LEVEL_ID"));
                levelItem.put("nameField", nameField);
                if (timeDimension) {
                    String granularity = timeGranularityCode(model, level);
                    levelItem.put("granularity", granularity);
                    levelItem.put("label", TIME_LEVEL_LABELS.getOrDefault(granularity, granularity));
                }
                levels.add(levelItem);
            }
            result.put("levels", levels);
        }
        if (!periodFields.isEmpty()) {
            result.put("periodFields", periodFields);
        }
        if (model.getDisplayFieldName() != null && !model.getDisplayFieldName().isBlank()) {
            result.put("displayFieldName", model.getDisplayFieldName());
        }
        result.put("recommendedDisplayField", recommendedDisplayField(model, fields, hierarchy, levelCount, timeDimension, periodFields));
        LOG.debug("AI tool describe_dimension {}: fields={}, recommended={}", code, fields.size(), result.get("recommendedDisplayField"));
        return result;
    }

    /**
     * 推荐展示字段：优先维度模型配置的默认显示字段；层级维度取层级名称字段；
     * 其余优先 *_name / name，其次首个非主键文本字段，再次首个非主键字段。
     */
    private String recommendedDisplayField(
        DataModel model,
        List<ModelField> fields,
        boolean hierarchy,
        int levelCount,
        boolean timeDimension,
        List<String> periodFields
    ) {
        String configured = model.getDisplayFieldName();
        if (configured != null && !configured.isBlank()) {
            boolean exists = fields.stream().anyMatch(field -> configured.equals(field.getFieldName()));
            if (exists) {
                return configured;
            }
        }
        if (hierarchy && levelCount > 0) {
            String nameField = levelFieldName(fields, levelCount, "LEVEL_NAME");
            if (nameField != null) {
                return nameField;
            }
        }
        for (ModelField field : fields) {
            if (!Boolean.TRUE.equals(field.getIsPrimaryKey()) && isNameField(field.getFieldName())) {
                return field.getFieldName();
            }
        }
        for (ModelField field : fields) {
            if (!Boolean.TRUE.equals(field.getIsPrimaryKey()) && isTextType(field.getFieldType())) {
                return field.getFieldName();
            }
        }
        if (timeDimension && !periodFields.isEmpty()) {
            return periodFields.get(0);
        }
        for (ModelField field : fields) {
            if (!Boolean.TRUE.equals(field.getIsPrimaryKey())) {
                return field.getFieldName();
            }
        }
        return null;
    }

    private static boolean isHierarchy(String dimensionKind) {
        return DIMENSION_KIND_HIERARCHY.equalsIgnoreCase(dimensionKind) || DIMENSION_KIND_TIME.equalsIgnoreCase(dimensionKind);
    }

    private static int resolveLevelCount(DataModel model) {
        if (DIMENSION_KIND_TIME.equalsIgnoreCase(model.getDimensionKind())) {
            String levels = model.getTimeLevels();
            if (levels == null || levels.isBlank()) {
                return 0;
            }
            int count = 0;
            for (String token : levels.split(",")) {
                if (!token.isBlank()) {
                    count++;
                }
            }
            return count;
        }
        return model.getLevelCount() == null ? 0 : model.getLevelCount();
    }

    private static String timeGranularityCode(DataModel model, int level) {
        String levels = model.getTimeLevels();
        if (levels == null || levels.isBlank()) {
            return null;
        }
        int index = 0;
        for (String token : levels.split(",")) {
            String value = token.trim();
            if (value.isEmpty()) {
                continue;
            }
            index++;
            if (index == level) {
                return value.toUpperCase();
            }
        }
        return null;
    }

    private static String levelFieldName(List<ModelField> fields, int level, String role) {
        for (ModelField field : fields) {
            if (role.equalsIgnoreCase(field.getFieldRole()) && field.getLevelIndex() != null && field.getLevelIndex() == level) {
                return field.getFieldName();
            }
        }
        return "LEVEL_ID".equals(role) ? "level" + level + "_id" : "level" + level + "_name";
    }

    private static boolean isNameField(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        String lower = fieldName.toLowerCase();
        return lower.equals("name") || lower.endsWith("_name");
    }

    private static boolean isTextType(String fieldType) {
        if (fieldType == null) {
            return false;
        }
        String upper = fieldType.toUpperCase();
        return upper.contains("CHAR") || upper.contains("TEXT") || upper.contains("STRING");
    }
}
