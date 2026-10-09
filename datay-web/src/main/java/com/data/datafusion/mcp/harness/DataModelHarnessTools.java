package com.data.datafusion.mcp.harness;

import static com.data.datafusion.mcp.harness.DatayHarnessArgs.intVal;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.mapList;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.str;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.array;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.bool;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.integer;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.object;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.properties;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.string;

import com.data.datafusion.service.DataModelMaterializeService;
import com.data.datafusion.service.DataModelService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.DimensionValueService;
import com.data.datafusion.service.ModelDirectoryService;
import com.data.datafusion.service.ModelFieldService;
import com.data.datafusion.service.TimeDimensionDataService;
import com.data.datafusion.service.dto.DataModelDTO;
import com.data.datafusion.service.dto.MaterializeFieldDTO;
import com.data.datafusion.service.dto.MaterializeRequestDTO;
import com.data.datafusion.service.dto.ModelDirectoryDTO;
import com.data.datafusion.service.dto.ModelFieldDTO;
import com.data.datafusion.service.dto.TimeDataRequestDTO;
import com.data.metadata.DatabaseConverter;
import com.data.metadata.impl.LogicConverter;
import com.data.metadata.util.DBUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 数据模型（维度模型 / 事实模型）对象的 Harness 工具族。
 *
 * <p>覆盖模型与字段的增删改查、维度取值浏览，以及物化落表相关动作
 * （可落表类型、物化字段准备、建表检查、DDL 生成、真正落表）。
 * 行为与 {@code /api/data-models} 下的 REST 接口保持一致。
 */
@Configuration
public class DataModelHarnessTools {

    private final DataModelService dataModelService;
    private final ModelFieldService modelFieldService;
    private final ModelDirectoryService modelDirectoryService;
    private final DimensionValueService dimensionValueService;
    private final DataModelMaterializeService materializeService;
    private final TimeDimensionDataService timeDimensionDataService;
    private final DataSourceService dataSourceService;
    private final ObjectMapper objectMapper;

    public DataModelHarnessTools(
        DataModelService dataModelService,
        ModelFieldService modelFieldService,
        ModelDirectoryService modelDirectoryService,
        DimensionValueService dimensionValueService,
        DataModelMaterializeService materializeService,
        TimeDimensionDataService timeDimensionDataService,
        DataSourceService dataSourceService,
        ObjectMapper objectMapper
    ) {
        this.dataModelService = dataModelService;
        this.modelFieldService = modelFieldService;
        this.modelDirectoryService = modelDirectoryService;
        this.dimensionValueService = dimensionValueService;
        this.materializeService = materializeService;
        this.timeDimensionDataService = timeDimensionDataService;
        this.dataSourceService = dataSourceService;
        this.objectMapper = objectMapper;
    }

    @Bean
    DatayHarnessTool dataModelListTool() {
        return DatayHarnessTool.read(
            "data_model_list",
            "列出数据模型。可按目录 directoryId 或模型类型 modelType（DIMENSION / FACT）过滤，不传则返回全部。",
            object(
                properties(
                    "directoryId", string("目录 ID（可选）"),
                    "modelType", string("模型类型，DIMENSION 维度模型 / FACT 事实模型（可选）")
                )
            ),
            (args, context) -> {
                String directoryId = str(args, "directoryId");
                String modelType = str(args, "modelType");
                List<DataModelDTO> models;
                if (directoryId != null) {
                    models = dataModelService.findByDirectoryId(directoryId);
                } else if (modelType != null) {
                    models = dataModelService.findByModelType(modelType);
                } else {
                    models = dataModelService.findAll();
                }
                return DatayHarnessViews.list(models, DatayHarnessViews::dataModel);
            }
        );
    }

    @Bean
    DatayHarnessTool dataModelGetTool() {
        return DatayHarnessTool.read(
            "data_model_get",
            "按 ID 查询数据模型详情，同时返回其字段定义。",
            object(properties("id", string("数据模型 ID")), "id"),
            (args, context) -> {
                String id = DatayHarnessArgs.reqStr(args, "id");
                Map<String, Object> view = DatayHarnessViews.dataModel(requireModel(id));
                view.put("fields", DatayHarnessViews.list(modelFieldService.findByModelId(id), DatayHarnessViews::modelField));
                return view;
            }
        );
    }

    @Bean
    DatayHarnessTool dataModelCreateTool() {
        return DatayHarnessTool.write(
            "data_model_create",
            "创建数据模型。维度模型可指定 dimensionKind（NORMAL / HIERARCHY / TIME）；时间维度需给出 timeLevels 与时间范围。",
            object(
                properties(
                    "name", string("模型名称"),
                    "code", string("模型编码（可选）"),
                    "description", string("描述（可选）"),
                    "directoryId", string("所属目录 ID（可选）"),
                    "modelType", string("模型类型：DIMENSION 维度模型 / FACT 事实模型（可选，默认 DIMENSION）"),
                    "dimensionKind", string("维度类型（可选）：NORMAL 普通 / HIERARCHY 层级 / TIME 时间"),
                    "levelCount", integer("层级维度的层数（可选）"),
                    "timeFieldName", string("时间维度的时间字段名（可选）"),
                    "timeLevels", string("时间维度粒度，如 YEAR,MONTH,DAY（可选）"),
                    "timeStart", string("时间维度起始日期 yyyy-MM-dd（可选）"),
                    "timeEnd", string("时间维度结束日期 yyyy-MM-dd（可选）"),
                    "project", string("项目标识（可选）"),
                    "dataSourceId", string("关联数据源 ID（可选）"),
                    "schemaName", string("schema（可选）"),
                    "tableName", string("物理表名（可选）"),
                    "displayFieldName", string("展示字段名（可选）")
                ),
                "name"
            ),
            (args, context) -> {
                DataModelDTO dto = new DataModelDTO();
                dto.setName(DatayHarnessArgs.reqStr(args, "name"));
                applyModelFields(dto, args);
                return DatayHarnessViews.dataModel(dataModelService.save(dto));
            }
        );
    }

    @Bean
    DatayHarnessTool dataModelUpdateTool() {
        return DatayHarnessTool.write(
            "data_model_update",
            "按 ID 更新数据模型，仅提交需要修改的字段，未提交的字段保持原值。",
            object(
                properties(
                    "id", string("数据模型 ID"),
                    "name", string("模型名称"),
                    "code", string("模型编码"),
                    "description", string("描述"),
                    "directoryId", string("所属目录 ID"),
                    "modelType", string("模型类型"),
                    "dimensionKind", string("维度类型"),
                    "levelCount", integer("层级维度层数"),
                    "timeFieldName", string("时间字段名"),
                    "timeLevels", string("时间粒度"),
                    "timeStart", string("起始日期"),
                    "timeEnd", string("结束日期"),
                    "project", string("项目标识"),
                    "dataSourceId", string("关联数据源 ID"),
                    "schemaName", string("schema"),
                    "tableName", string("物理表名"),
                    "displayFieldName", string("展示字段名")
                ),
                "id"
            ),
            (args, context) -> {
                String id = DatayHarnessArgs.reqStr(args, "id");
                requireModel(id);
                DataModelDTO patch = new DataModelDTO();
                patch.setId(id);
                applyModelFields(patch, args);
                return dataModelService
                    .partialUpdate(patch)
                    .map(DatayHarnessViews::dataModel)
                    .orElseThrow(() -> new IllegalArgumentException("数据模型不存在: " + id));
            }
        );
    }

    @Bean
    DatayHarnessTool dataModelDeleteTool() {
        return DatayHarnessTool.write(
            "data_model_delete",
            "按 ID 删除数据模型，同时删除其字段定义（与页面删除行为一致）。",
            object(properties("id", string("数据模型 ID")), "id"),
            (args, context) -> {
                String id = DatayHarnessArgs.reqStr(args, "id");
                DataModelDTO existing = requireModel(id);
                modelFieldService.deleteByModelId(id);
                dataModelService.delete(id);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("deletedId", id);
                result.put("name", existing.getName());
                return result;
            }
        );
    }

    @Bean
    DatayHarnessTool dataModelFieldsTool() {
        return DatayHarnessTool.read(
            "data_model_fields",
            "查询数据模型的字段定义列表。",
            object(properties("id", string("数据模型 ID")), "id"),
            (args, context) -> {
                String id = DatayHarnessArgs.reqStr(args, "id");
                requireModel(id);
                return DatayHarnessViews.list(modelFieldService.findByModelId(id), DatayHarnessViews::modelField);
            }
        );
    }

    @Bean
    DatayHarnessTool dataModelSaveFieldsTool() {
        return DatayHarnessTool.write(
            "data_model_save_fields",
            "整体保存数据模型的字段定义：先清空原有字段再写入本次提交的列表，因此每次都要提交完整字段集合。",
            object(
                properties(
                    "id", string("数据模型 ID"),
                    "fields", array(fieldSchema(), "完整字段列表")
                ),
                "id",
                "fields"
            ),
            (args, context) -> {
                String id = DatayHarnessArgs.reqStr(args, "id");
                requireModel(id);
                List<Map<String, Object>> raw = mapList(args, "fields");
                if (raw == null) {
                    throw new IllegalArgumentException("缺少必填参数: fields");
                }
                List<ModelFieldDTO> fields = new ArrayList<>(raw.size());
                for (Map<String, Object> item : raw) {
                    ModelFieldDTO field = objectMapper.convertValue(item, ModelFieldDTO.class);
                    field.setId(null);
                    field.setModelId(id);
                    fields.add(field);
                }
                modelFieldService.deleteByModelId(id);
                return DatayHarnessViews.list(modelFieldService.saveAll(fields), DatayHarnessViews::modelField);
            }
        );
    }

    @Bean
    DatayHarnessTool dataModelDimensionValuesTool() {
        return DatayHarnessTool.read(
            "data_model_dimension_values",
            "分页查询维度模型某个字段的取值，便于确认维度成员的写法。",
            object(
                properties(
                    "id", string("数据模型 ID"),
                    "fieldName", string("字段名"),
                    "keyword", string("可选关键字，按取值模糊过滤"),
                    "page", integer("页码，从 1 开始，默认 1"),
                    "size", integer("每页条数，默认 20")
                ),
                "id",
                "fieldName"
            ),
            (args, context) -> {
                String id = DatayHarnessArgs.reqStr(args, "id");
                requireModel(id);
                return dimensionValueService.pageValues(
                    id,
                    DatayHarnessArgs.reqStr(args, "fieldName"),
                    str(args, "keyword"),
                    Math.max(1, intVal(args, "page", 1)),
                    Math.max(1, intVal(args, "size", 20))
                );
            }
        );
    }

    @Bean
    DatayHarnessTool dataModelLogicalTypesTool() {
        return DatayHarnessTool.read(
            "data_model_logical_types",
            "列出建模支持的逻辑字段类型及其展示名，用于填写字段的 fieldType。",
            object(properties()),
            (args, context) -> {
                List<Map<String, String>> result = new ArrayList<>();
                for (String typeName : DatabaseConverter.getSupportedTypes("common")) {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("type", typeName);
                    item.put("label", LogicConverter.LOGIC_TYPE_LABELS.getOrDefault(typeName, typeName) + "(" + typeName + ")");
                    result.add(item);
                }
                return result;
            }
        );
    }

    @Bean
    DatayHarnessTool dataModelMaterializeTypesTool() {
        return DatayHarnessTool.read(
            "data_model_materialize_types",
            "列出指定数据源支持的物理字段类型，用于配置物化落表。",
            object(properties("dataSourceId", string("目标数据源 ID")), "dataSourceId"),
            (args, context) -> {
                String dataSourceId = DatayHarnessArgs.reqStr(args, "dataSourceId");
                String url = dataSourceService
                    .findOne(dataSourceId)
                    .orElseThrow(() -> new IllegalArgumentException("数据源不存在: " + dataSourceId))
                    .getUrl();
                return DatabaseConverter.getSupportedTypes(DBUtils.getDBType(url));
            }
        );
    }

    @Bean
    DatayHarnessTool dataModelMaterializeFieldsTool() {
        return DatayHarnessTool.read(
            "data_model_materialize_fields",
            "把模型字段准备成目标库的物化字段定义（含物理类型推导），用于生成建表语句前预览。",
            object(properties("id", string("数据模型 ID"), "dataSourceId", string("目标数据源 ID")), "id", "dataSourceId"),
            (args, context) -> {
                String id = DatayHarnessArgs.reqStr(args, "id");
                requireModel(id);
                String dataSourceId = DatayHarnessArgs.reqStr(args, "dataSourceId");
                String dbType = dataSourceService
                    .findOne(dataSourceId)
                    .map(ds -> DBUtils.getDBType(ds.getUrl()))
                    .orElse("mysql");
                return materializeService.prepareMaterializeFields(modelFieldService.findByModelId(id), dbType);
            }
        );
    }

    @Bean
    DatayHarnessTool dataModelMaterializeCheckTool() {
        return DatayHarnessTool.read(
            "data_model_materialize_check",
            "检查物化目标表是否已存在，返回 tableExists 与提示信息。",
            object(materializeRequestProperties(), "id", "dataSourceId"),
            (args, context) -> {
                String id = DatayHarnessArgs.reqStr(args, "id");
                requireModel(id);
                return materializeService.checkTableExists(toMaterializeRequest(args));
            }
        );
    }

    @Bean
    DatayHarnessTool dataModelMaterializeDdlTool() {
        return DatayHarnessTool.read(
            "data_model_materialize_ddl",
            "生成物化建表 DDL，但不执行，便于人工确认后再落表。",
            object(materializeRequestProperties(), "id", "dataSourceId"),
            (args, context) -> {
                String id = DatayHarnessArgs.reqStr(args, "id");
                requireModel(id);
                return materializeService.generateDDL(toMaterializeRequest(args));
            }
        );
    }

    @Bean
    DatayHarnessTool dataModelMaterializeTool() {
        return DatayHarnessTool.write(
            "data_model_materialize",
            "把数据模型物化落表到目标数据源：建表成功后回写模型的物理位置，并可选生成时间维度的预置数据。",
            object(
                properties(
                    "id", string("数据模型 ID"),
                    "dataSourceId", string("目标数据源 ID"),
                    "schemaName", string("目标 schema"),
                    "tableName", string("目标表名"),
                    "overwrite", bool("目标表已存在时是否覆盖，默认 false"),
                    "generateData", bool("是否生成时间维度预置数据，默认 false"),
                    "dataStart", string("预置数据起始日期 yyyy-MM-dd（可选）"),
                    "dataEnd", string("预置数据结束日期 yyyy-MM-dd（可选）")
                ),
                "id",
                "dataSourceId"
            ),
            (args, context) -> {
                String id = DatayHarnessArgs.reqStr(args, "id");
                requireModel(id);
                var request = toMaterializeRequest(args);
                var response = materializeService.materialize(request);
                if (!response.isSuccess()) {
                    throw new IllegalStateException("物化失败: " + response.getMessage());
                }
                dataModelService.findOne(id).ifPresent(existing -> {
                    existing.setDataSourceId(request.getDataSourceId());
                    existing.setSchemaName(request.getSchemaName());
                    existing.setTableName(request.getTableName());
                    dataModelService.partialUpdate(existing);
                });
                if (Boolean.TRUE.equals(request.getGenerateData())) {
                    TimeDataRequestDTO dataRequest = new TimeDataRequestDTO();
                    dataRequest.setDataSourceId(request.getDataSourceId());
                    dataRequest.setSchemaName(request.getSchemaName());
                    dataRequest.setTableName(request.getTableName());
                    dataRequest.setStart(request.getDataStart());
                    dataRequest.setEnd(request.getDataEnd());
                    dataRequest.setOverwrite(true);
                    int rows = timeDimensionDataService.generate(id, dataRequest);
                    response.setDataRows(rows);
                    response.setMessage(response.getMessage() + "，已生成预置数据 " + rows + " 行");
                }
                return response;
            }
        );
    }

    @Bean
    DatayHarnessTool dataModelDirectoryListTool() {
        return DatayHarnessTool.read(
            "data_model_directory_list",
            "列出数据模型的目录树节点，便于创建模型时指定 directoryId。",
            object(properties("parentId", string("父目录 ID（可选，不传返回全部）"))),
            (args, context) -> {
                String parentId = str(args, "parentId");
                List<ModelDirectoryDTO> directories = parentId == null
                    ? modelDirectoryService.findAll()
                    : modelDirectoryService.findByParentId(parentId);
                return DatayHarnessViews.list(directories, DataModelHarnessTools::directoryView);
            }
        );
    }

    @Bean
    DatayHarnessTool dataModelDirectoryCreateTool() {
        return DatayHarnessTool.write(
            "data_model_directory_create",
            "创建数据模型目录，用于给模型分类。",
            object(properties("name", string("目录名称"), "parentId", string("父目录 ID（可选）"), "sortOrder", integer("排序值（可选）")), "name"),
            (args, context) -> {
                ModelDirectoryDTO dto = new ModelDirectoryDTO();
                dto.setName(DatayHarnessArgs.reqStr(args, "name"));
                dto.setParentId(str(args, "parentId"));
                dto.setSortOrder(intVal(args, "sortOrder", 0));
                return directoryView(modelDirectoryService.save(dto));
            }
        );
    }

    private static Map<String, Object> directoryView(ModelDirectoryDTO dto) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", dto.getId());
        view.put("name", dto.getName());
        view.put("parentId", dto.getParentId());
        view.put("sortOrder", dto.getSortOrder());
        return view;
    }

    private static Map<String, Object> materializeRequestProperties() {
        return properties(
            "id", string("数据模型 ID"),
            "dataSourceId", string("目标数据源 ID"),
            "schemaName", string("目标 schema"),
            "tableName", string("目标表名"),
            "overwrite", bool("目标表已存在时是否覆盖（可选）")
        );
    }

    private static Map<String, Object> fieldSchema() {
        return object(
            properties(
                "fieldName", string("字段名"),
                "fieldType", string("字段类型（逻辑类型，可先用 data_model_logical_types 查询）"),
                "fieldLength", integer("长度（可选）"),
                "fieldPrecision", integer("精度（可选）"),
                "fieldScale", integer("小数位（可选）"),
                "description", string("描述（可选）"),
                "sortOrder", integer("排序值（可选）"),
                "isPartitionKey", bool("是否分区键（可选）"),
                "isPrimaryKey", bool("是否主键（可选）"),
                "dimensionModelId", string("关联维度模型 ID（可选）"),
                "dimensionFieldId", string("关联维度字段 ID（可选）"),
                "fieldRole", string("字段角色（可选，如 DIMENSION_KEY / MEASURE）"),
                "levelIndex", integer("层级维度中的层号（可选）")
            ),
            "fieldName",
            "fieldType"
        );
    }

    private MaterializeRequestDTO toMaterializeRequest(Map<String, Object> args) {
        MaterializeRequestDTO request = new MaterializeRequestDTO();
        request.setDataSourceId(DatayHarnessArgs.reqStr(args, "dataSourceId"));
        request.setSchemaName(str(args, "schemaName"));
        request.setTableName(str(args, "tableName"));
        request.setOverwrite(DatayHarnessArgs.boolVal(args, "overwrite", false));
        request.setGenerateData(DatayHarnessArgs.boolVal(args, "generateData", false));
        request.setDataStart(str(args, "dataStart"));
        request.setDataEnd(str(args, "dataEnd"));
        List<Map<String, Object>> rawFields = mapList(args, "fields");
        if (rawFields != null) {
            request.setFields(rawFields.stream().map(item -> objectMapper.convertValue(item, MaterializeFieldDTO.class)).toList());
        }
        return request;
    }

    /**
     * 仅覆盖入参中显式给出的模型字段，未提交的字段保持原值。
     */
    private static void applyModelFields(DataModelDTO dto, Map<String, Object> args) {
        dto.setName(str(args, "name"));
        dto.setCode(str(args, "code"));
        dto.setDescription(str(args, "description"));
        dto.setDirectoryId(str(args, "directoryId"));
        dto.setModelType(str(args, "modelType"));
        dto.setDimensionKind(str(args, "dimensionKind"));
        dto.setLevelCount(intVal(args, "levelCount", null));
        dto.setTimeFieldName(str(args, "timeFieldName"));
        dto.setTimeLevels(str(args, "timeLevels"));
        dto.setTimeStart(str(args, "timeStart"));
        dto.setTimeEnd(str(args, "timeEnd"));
        dto.setProject(str(args, "project"));
        dto.setDataSourceId(str(args, "dataSourceId"));
        dto.setSchemaName(str(args, "schemaName"));
        dto.setTableName(str(args, "tableName"));
        dto.setDisplayFieldName(str(args, "displayFieldName"));
    }

    private DataModelDTO requireModel(String id) {
        return dataModelService.findOne(id).orElseThrow(() -> new IllegalArgumentException("数据模型不存在: " + id));
    }
}
