package com.datay.plugin.example;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.expression.ParameterUtil;
import com.data.job.ComponentField;
import com.data.job.ComponentFieldOption;
import com.data.job.ComponentFieldType;
import com.data.job.ComponentFieldValueFormat;
import com.data.job.ComponentRegister;
import com.data.job.DatasourceInfo;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 外部 JAR 插件示例组件。
 * <p>
 * 该组件演示四件事：
 * <ol>
 *     <li>通过 {@link ComponentRegister} 注册到 DataY 组件目录；</li>
 *     <li>通过 {@link ComponentField} 声明配置字段，让前端自动生成动态表单；</li>
 *     <li>演示输入框、数字、下拉、多选、开关、数据源、Schema、表、字段、文件、文本域等控件；</li>
 *     <li>作为一个 Source 组件生成 JSON 数组或文本数据，可直接用于调试数据流。</li>
 * </ol>
 * 组件实现只依赖 {@code datay-core} 提供的 API，打包为普通 JAR 后放入插件目录即可加载。
 */
@ComponentRegister(
    value = "ExampleHelloSource",
    name = "示例数据生成器",
    group = "示例插件",
    desc = "外部 JAR 插件示例：按配置生成示例 JSON 数组或文本数据。",
    order = 10
)
public class ExampleHelloSource extends FlowComponent {

    @ComponentField(
        label = "消息内容",
        type = ComponentFieldType.INPUT,
        required = true,
        group = "基础配置",
        order = 10,
        defaultValue = "hello from plugin",
        placeholder = "支持 ${attr:属性名} 等动态参数",
        help = "支持 ${attr:属性名}、${属性名}、#{内置参数} 等动态参数占位符"
    )
    private String message = "hello from plugin";

    @ComponentField(
        label = "生成行数",
        type = ComponentFieldType.NUMBER,
        group = "基础配置",
        order = 20,
        defaultValue = "3",
        min = 1,
        max = 1000,
        step = 1,
        help = "JSON 数组模式生成的行数，或文本模式生成的行数"
    )
    private Integer count = 3;

    @ComponentField(
        label = "输出格式",
        type = ComponentFieldType.SELECT,
        group = "基础配置",
        order = 30,
        defaultValue = "JSON_ARRAY",
        options = {
            @ComponentFieldOption(value = "JSON_ARRAY", label = "JSON 数组"),
            @ComponentFieldOption(value = "TEXT", label = "纯文本")
        }
    )
    private String outputFormat = "JSON_ARRAY";

    @ComponentField(
        key = "sourceId",
        label = "示例数据源",
        type = ComponentFieldType.DATASOURCE,
        required = true,
        group = "数据源与表（演示）",
        order = 10,
        schemaField = "schema",
        help = "演示数据源选择器；平台会把数据源 ID 解析为完整连接信息后注入组件"
    )
    private DatasourceInfo datasource;

    @ComponentField(
        key = "schema",
        label = "Schema",
        type = ComponentFieldType.SCHEMA,
        group = "数据源与表（演示）",
        order = 20,
        dataSourceField = "sourceId",
        placeholder = "请选择 Schema",
        help = "演示 Schema 选择器，依赖示例数据源"
    )
    private String schema;

    @ComponentField(
        key = "table",
        label = "示例数据表",
        type = ComponentFieldType.TABLE,
        group = "数据源与表（演示）",
        order = 30,
        dataSourceField = "sourceId",
        schemaField = "schema",
        placeholder = "请选择数据表",
        help = "演示表选择器，依赖示例数据源和 Schema"
    )
    private String table;

    @ComponentField(
        key = "columns",
        label = "示例字段",
        type = ComponentFieldType.COLUMN,
        group = "数据源与表（演示）",
        order = 40,
        multiple = true,
        valueFormat = ComponentFieldValueFormat.ARRAY,
        dataSourceField = "sourceId",
        schemaField = "schema",
        tableField = "table",
        help = "演示字段多选，依赖示例数据源、Schema 和表；结果以数组保存"
    )
    private List<String> columns = new ArrayList<>();

    @ComponentField(
        label = "标签",
        type = ComponentFieldType.MULTI_SELECT,
        group = "高级配置",
        order = 10,
        multiple = true,
        valueFormat = ComponentFieldValueFormat.ARRAY,
        options = {
            @ComponentFieldOption(value = "alpha", label = "Alpha"),
            @ComponentFieldOption(value = "beta", label = "Beta"),
            @ComponentFieldOption(value = "gamma", label = "Gamma")
        },
        help = "多选标签会写入每行数据的 tags 字段；不选则不写入"
    )
    private List<String> tags = new ArrayList<>();

    @ComponentField(
        label = "写入时间戳",
        type = ComponentFieldType.SWITCH,
        group = "高级配置",
        order = 20,
        defaultValue = "true",
        help = "开启后每行数据增加 timestamp 字段（毫秒）"
    )
    private boolean includeTimestamp = true;

    @ComponentField(
        label = "模板文件",
        type = ComponentFieldType.FILE,
        group = "更多控件（演示）",
        order = 10,
        placeholder = "请选择模板文件",
        help = "演示文件选择器；示例组件会把文件路径写入 FlowFile 属性"
    )
    private String templateFile;

    @ComponentField(
        label = "自定义参数",
        type = ComponentFieldType.TEXTAREA,
        group = "更多控件（演示）",
        order = 20,
        placeholder = "{\"key\":\"value\"}",
        help = "演示多行文本输入；示例组件会把内容原样写入 FlowFile 属性"
    )
    private String customParams;

    public ExampleHelloSource() {
        setType(ComponentType.SOURCE);
    }

    @Override
    public void execute(FlowFile flowFile) {
        writeRecords(buildFlowFile(flowFile));
    }

    /**
     * 根据当前配置构造输出 FlowFile。包级可见，便于插件模块单元测试。
     *
     * @param inputFlowFile 框架传入的起始 FlowFile，用于解析动态参数
     * @return 输出 FlowFile
     */
    FlowFile buildFlowFile(FlowFile inputFlowFile) {
        String resolvedMessage = ParameterUtil.replaceParameters(message, inputFlowFile);
        int rowCount = count == null ? 0 : Math.max(count, 0);

        FlowFile output = new FlowFile();
        output.setAttribute("_source", "ExampleHelloSource");
        output.setAttribute("_pluginExample", true);
        output.setAttribute("_message", resolvedMessage);
        if (datasource != null) {
            putIfPresent(output, "_datasourceType", datasource.getType());
            putIfPresent(output, "_datasourceSchema", resolveSchema());
        }
        putIfPresent(output, "_demoTable", table);
        putIfPresent(output, "_demoColumns", columns);
        putIfPresent(output, "_templateFile", templateFile);
        putIfPresent(output, "_customParams", customParams);

        if ("TEXT".equalsIgnoreCase(outputFormat)) {
            output.setTextData(buildTextData(resolvedMessage, rowCount));
        } else {
            output.setJsonArray(buildJsonRows(resolvedMessage, rowCount));
        }
        return output;
    }

    private String resolveSchema() {
        if (schema != null && !schema.trim().isEmpty()) {
            return schema;
        }
        return datasource == null ? null : datasource.getDbschema();
    }

    private void putIfPresent(FlowFile flowFile, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String && ((String) value).trim().isEmpty()) {
            return;
        }
        if (value instanceof List && ((List<?>) value).isEmpty()) {
            return;
        }
        flowFile.setAttribute(key, value);
    }

    private JSONArray buildJsonRows(String resolvedMessage, int rowCount) {
        JSONArray rows = new JSONArray();
        for (int index = 1; index <= rowCount; index++) {
            JSONObject row = new JSONObject();
            row.put("id", index);
            row.put("message", resolvedMessage);
            if (tags != null && !tags.isEmpty()) {
                row.put("tags", new ArrayList<>(tags));
            }
            if (includeTimestamp) {
                row.put("timestamp", System.currentTimeMillis());
            }
            rows.add(row);
        }
        return rows;
    }

    private String buildTextData(String resolvedMessage, int rowCount) {
        StringBuilder builder = new StringBuilder();
        for (int index = 1; index <= rowCount; index++) {
            if (index > 1) {
                builder.append('\n');
            }
            builder.append(index).append(": ").append(resolvedMessage);
            if (tags != null && !tags.isEmpty()) {
                builder.append(" [").append(String.join(",", tags)).append(']');
            }
            if (includeTimestamp) {
                builder.append(" @").append(System.currentTimeMillis());
            }
        }
        return builder.toString();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public String getOutputFormat() {
        return outputFormat;
    }

    public void setOutputFormat(String outputFormat) {
        this.outputFormat = outputFormat;
    }

    public DatasourceInfo getDatasource() {
        return datasource;
    }

    public void setDatasource(DatasourceInfo datasource) {
        this.datasource = datasource;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public String getTable() {
        return table;
    }

    public void setTable(String table) {
        this.table = table;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns == null ? new ArrayList<>() : columns;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags == null ? new ArrayList<>() : tags;
    }

    public boolean isIncludeTimestamp() {
        return includeTimestamp;
    }

    public void setIncludeTimestamp(boolean includeTimestamp) {
        this.includeTimestamp = includeTimestamp;
    }

    public String getTemplateFile() {
        return templateFile;
    }

    public void setTemplateFile(String templateFile) {
        this.templateFile = templateFile;
    }

    public String getCustomParams() {
        return customParams;
    }

    public void setCustomParams(String customParams) {
        this.customParams = customParams;
    }
}
