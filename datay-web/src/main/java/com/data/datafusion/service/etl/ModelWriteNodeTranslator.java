package com.data.datafusion.service.etl;

import com.data.datafusion.service.DataModelService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.dto.DataModelDTO;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.job.ComponentDescriptor;
import com.data.metadata.util.DBUtils;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 「模型写入」组件翻译器。
 * <p>设计器侧 {@code ModelWrite} 组件只需选择数据模型；翻译时读取模型绑定的
 * 数据源（{@code dataSourceId}）、Schema（{@code schemaName}）与物理表（{@code tableName}），
 * 生成后端 {@code StreamJdbcOutput} 组件的任务单元定义，实现设计期与执行期解耦。
 */
@Component
public class ModelWriteNodeTranslator implements ETLNodeTranslator {

    /** 设计器侧组件类型。 */
    public static final String COMPONENT_TYPE = "ModelWrite";

    /** 翻译后使用的后端组件名。 */
    public static final String TARGET_COMPONENT = "StreamJdbcOutput";

    private final DataModelService dataModelService;

    private final DataSourceService dataSourceService;

    public ModelWriteNodeTranslator(DataModelService dataModelService, DataSourceService dataSourceService) {
        this.dataModelService = dataModelService;
        this.dataSourceService = dataSourceService;
    }

    @Override
    public String supportedType() {
        return COMPONENT_TYPE;
    }

    @Override
    public ComponentDescriptor descriptor() {
        return new ComponentDescriptor(
            COMPONENT_TYPE,
            "模型写入",
            "数据输出",
            "选择数据模型，按模型绑定的数据源与表写入目标表。",
            getClass().getName(),
            50
        );
    }

    @Override
    public void translate(ETLNodeTranslationContext context) {
        DataModelDTO dataModel = resolveDataModel(context);
        DataSourceDTO source = resolveDataSource(dataModel);

        Map<String, Object> unit = context.getUnit();
        // 后端直接复用 StreamJdbcOutput 组件执行写入
        unit.put(".name", TARGET_COMPONENT);
        unit.put("sourceId", buildSourceId(source, dataModel.getSchemaName()));
        unit.put("schema", nullToEmpty(dataModel.getSchemaName()));
        unit.put("table", nullToEmpty(dataModel.getTableName()));
        unit.put("model", context.getConfigString("model", "append"));
        putIfPresent(context, unit, "dropIfTableExists");
        putIfPresent(context, unit, "updateColumn");
        putIfPresent(context, unit, "maxRows");
        putIfPresent(context, unit, "columnsMap");
    }

    private DataModelDTO resolveDataModel(ETLNodeTranslationContext context) {
        String modelIdValue = context.getConfigString("modelId", null);
        if (modelIdValue == null) {
            throw new IllegalArgumentException("模型写入组件未选择数据模型");
        }
        long modelId;
        try {
            modelId = Long.parseLong(modelIdValue.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("数据模型ID不合法: " + modelIdValue);
        }
        DataModelDTO dataModel = dataModelService.findOne(modelId).orElse(null);
        if (dataModel == null) {
            throw new IllegalArgumentException("数据模型不存在: " + modelIdValue);
        }
        if (dataModel.getDataSourceId() == null) {
            throw new IllegalArgumentException("数据模型未绑定数据源: " + dataModel.getName());
        }
        if (dataModel.getTableName() == null || dataModel.getTableName().trim().isEmpty()) {
            throw new IllegalArgumentException("数据模型未绑定物理表: " + dataModel.getName());
        }
        return dataModel;
    }

    private DataSourceDTO resolveDataSource(DataModelDTO dataModel) {
        return dataSourceService
            .findOne(dataModel.getDataSourceId())
            .orElseThrow(() -> new IllegalArgumentException("数据模型绑定的数据源不存在: " + dataModel.getDataSourceId()));
    }

    private Map<String, Object> buildSourceId(DataSourceDTO source, String schema) {
        Map<String, Object> sourceId = new LinkedHashMap<>();
        sourceId.put("url", source.getUrl());
        sourceId.put("driver", DBUtils.getDriverClassName(source.getUrl()));
        sourceId.put("username", source.getUsername());
        sourceId.put("password", source.getPassword());
        sourceId.put("dbschema", nullToEmpty(schema));
        return sourceId;
    }

    private void putIfPresent(ETLNodeTranslationContext context, Map<String, Object> unit, String key) {
        Object value = context.getConfig().get(key);
        if (value != null) {
            unit.put(key, value);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
