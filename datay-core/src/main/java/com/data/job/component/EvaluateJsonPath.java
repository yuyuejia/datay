package com.data.job.component;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONPath;
import com.data.job.ComponentField;
import com.data.job.ComponentFieldType;
import com.data.job.ComponentRegister;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;
import com.data.metadata.TableMeta;
import com.data.metadata.util.JsonTableMetaUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * JSONPath 提取组件。
 *
 * <p>使用单个 JSONPath 表达式从上游 FlowFile 的 JSON 数据中提取子结构，并用提取结果
 * 替换数据内容后发送给下游。求值范围按上游数据类型区分：
 * <ul>
 *     <li>{@code JSON_ARRAY}（或文本解析出的 JSON 数组）：<b>逐行求值</b>。每条记录单独执行
 *     JSONPath，结果为 JSON 对象时直接作为一条输出记录；结果为 JSON 数组时展平后作为数据内容
 *     合并输出；结果为标量时包装为 {@code {valueField: 结果}} 后作为一条输出记录。未命中的行会被跳过，不影响其余行。</li>
 *     <li>{@code JSON_OBJECT}（或文本解析出的 JSON 对象）：对整个对象<b>直接求值一次</b>，
 *     结果替换数据内容；JSON 数组直接作为数据内容，标量结果包装为单条记录 {@code [{valueField: 结果}]}。</li>
 * </ul>
 *
 * <p>所有未命中的 FlowFile 均跳过并记录告警，不会下发到下游。
 *
 * <p>配置示例：
 * <pre>
 * {
 *   "jsonPath": "$.data.items[*]",
 *   "valueField": "value"
 * }
 * </pre>
 */
@ComponentRegister(
    value = "EvaluateJsonPath",
    name = "JSONPath提取",
    group = "数据处理",
    desc = "按单个 JSONPath 表达式提取上游 JSON 数据：JSON 数组逐行求值，JSON 对象直接求值，用提取结果替换数据内容后下发下游，未命中跳过并告警。",
    order = 21
)
public class EvaluateJsonPath extends FlowComponent {

    /** 标量提取结果的默认包装字段名。 */
    public static final String DEFAULT_VALUE_FIELD = "value";

    /** JSONPath 表达式。 */
    @ComponentField(
        label = "JSONPath 表达式",
        type = ComponentFieldType.INPUT,
        group = "基础配置",
        order = 10,
        required = true,
        placeholder = "$.data.items[*]",
        help = "不以 $ 开头时自动补全为 $. 前缀。JSON 数组输入时逐行求值，JSON 对象输入时直接求值。"
    )
    private String jsonPath;

    /** 逐行提取结果为标量时写入的字段名。 */
    @ComponentField(
        label = "包装字段名",
        type = ComponentFieldType.INPUT,
        group = "基础配置",
        order = 20,
        defaultValue = DEFAULT_VALUE_FIELD,
        placeholder = DEFAULT_VALUE_FIELD,
        help = "当提取结果是标量时，包装成 {字段名: 值} 再输出；提取结果是对象或数组时直接作为数据内容。"
    )
    private String valueField;

    public EvaluateJsonPath() {
        setType(ComponentType.OPERATOR);
    }

    @Override
    public void execute(FlowFile flowFile) {
        // 结束信号交由基类统一广播
        if (flowFile.getAttribute("_end") != null) {
            return;
        }

        String path = resolveJsonPath();
        if (path == null) {
            logWarn("EvaluateJsonPath: 未配置 JSONPath 表达式，已跳过该 FlowFile");
            return;
        }

        Object root = resolveRootData(flowFile);
        if (root == null) {
            logWarn("EvaluateJsonPath: 不支持或无法解析的上游数据格式（dataFormat=" + flowFile.getDataFormat() + "），已跳过");
            return;
        }

        FlowFile output = root instanceof JSONArray
            ? evaluatePerRecord(flowFile, (JSONArray) root, path)
            : evaluateWhole(flowFile, root, path);
        if (output != null) {
            writeRecords(output);
        }
    }

    /**
     * JSON 数组逐行求值：每行提取结果转换为一条输出记录。
     */
    private FlowFile evaluatePerRecord(FlowFile source, JSONArray records, String path) {
        if (records.isEmpty()) {
            logWarn("EvaluateJsonPath: 上游 JSON 数组为空，已跳过");
            return null;
        }

        JSONArray output = new JSONArray();
        int matched = 0;
        int skipped = 0;
        String firstError = null;
        for (Object record : records) {
            Object result;
            try {
                result = JSONPath.eval(record, path);
            } catch (Exception e) {
                skipped++;
                if (firstError == null) {
                    firstError = e.getMessage();
                }
                continue;
            }
            if (result == null) {
                skipped++;
                continue;
            }
            matched++;
            appendResult(output, result);
        }

        if (matched == 0) {
            if (firstError != null) {
                logWarn("EvaluateJsonPath: JSONPath " + path + " 求值失败，已跳过该 FlowFile: " + firstError);
            } else {
                logWarn("EvaluateJsonPath: JSONPath " + path + " 在 " + records.size() + " 行中均未匹配到数据，已跳过");
            }
            return null;
        }
        if (skipped > 0) {
            logWarn("EvaluateJsonPath: 有 " + skipped + " 行未匹配到 JSONPath " + path + "，已跳过这些行");
        }

        FlowFile outFile = buildOutput(source, output);
        updateTableMeta(outFile, source, output);
        logInfo("EvaluateJsonPath: 逐行提取完成，输出 " + output.size() + " 条记录，跳过 " + skipped + " 行");
        return outFile;
    }

    /**
     * JSON 对象直接求值：用结果替换数据内容。
     */
    private FlowFile evaluateWhole(FlowFile source, Object root, String path) {
        Object result;
        try {
            result = JSONPath.eval(root, path);
        } catch (Exception e) {
            logWarn("EvaluateJsonPath: JSONPath " + path + " 求值失败，已跳过: " + e.getMessage());
            return null;
        }
        if (result == null) {
            logWarn("EvaluateJsonPath: JSONPath " + path + " 未匹配到数据，已跳过");
            return null;
        }

        result = normalizeResult(result);
        FlowFile outFile = buildOutput(source, null);
        if (result instanceof JSONArray) {
            outFile.setJsonArray((JSONArray) result);
        } else if (result instanceof JSONObject) {
            outFile.setJsonObject((JSONObject) result);
        } else {
            JSONObject record = new JSONObject();
            record.put(resolveValueField(), result);
            JSONArray wrapped = new JSONArray();
            wrapped.add(record);
            outFile.setJsonArray(wrapped);
        }
        updateTableMeta(outFile, source, outFile.getData());
        logInfo("EvaluateJsonPath: 提取完成，输出格式 " + outFile.getDataFormat());
        return outFile;
    }

    /**
     * 将单行提取结果追加到输出数组：JSON 数组展平后作为数据内容，对象直接追加为记录，标量放入包装字段。
     */
    private void appendResult(JSONArray output, Object result) {
        Object normalized = normalizeResult(result);
        if (normalized instanceof JSONArray) {
            output.addAll((JSONArray) normalized);
        } else if (normalized instanceof JSONObject) {
            output.add(normalized);
        } else {
            JSONObject record = new JSONObject();
            record.put(resolveValueField(), normalized);
            output.add(record);
        }
    }

    /**
     * 将 fastjson2 可能返回的 Map/List 统一转换为 JSONObject/JSONArray。
     */
    private Object normalizeResult(Object result) {
        if (result instanceof JSONObject || result instanceof JSONArray) {
            return result;
        }
        if (result instanceof Map) {
            return new JSONObject((Map<String, Object>) result);
        }
        if (result instanceof List) {
            return new JSONArray((List<?>) result);
        }
        if (result instanceof Collection) {
            return new JSONArray((Collection<?>) result);
        }
        return result;
    }

    /**
     * 复制上游属性与状态，构造承载提取结果的 FlowFile。
     */
    private FlowFile buildOutput(FlowFile source, JSONArray data) {
        FlowFile outFile = new FlowFile();
        outFile.getAttributeMap().putAll(source.getAttributeMap());
        outFile.getStatusMap().putAll(source.getStatusMap());
        if (data != null) {
            outFile.setJsonArray(data);
        }
        return outFile;
    }

    /**
     * 按提取结果重建表元数据，保留原表名与 schema；无法推断字段时移除旧元数据，避免下游按错误结构建表。
     */
    private void updateTableMeta(FlowFile outFile, FlowFile source, Object data) {
        Object meta = source.getAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA);
        TableMeta existing = meta instanceof TableMeta ? (TableMeta) meta : null;
        String dbType = existing != null && existing.getDbType() != null ? existing.getDbType() : "mysql";
        TableMeta tableMeta = JsonTableMetaUtils.build(data, dbType);
        if (tableMeta != null) {
            if (existing != null) {
                tableMeta.setTable(existing.getTable());
                tableMeta.setSchema(existing.getSchema());
            }
            outFile.setAttribute(FlowFile.ATTRIBUTE_TABLE_METADATA, tableMeta);
        } else {
            outFile.getAttributeMap().remove(FlowFile.ATTRIBUTE_TABLE_METADATA);
        }
    }

    /**
     * 解析上游数据：JSON 数组/对象直接返回，文本尝试按 JSON 解析，其余格式返回 null。
     */
    private Object resolveRootData(FlowFile flowFile) {
        if (flowFile.getDataFormat() == null) {
            return null;
        }
        switch (flowFile.getDataFormat()) {
            case JSON_ARRAY:
                return flowFile.getJsonArray();
            case JSON_OBJECT:
                return flowFile.getJsonObject();
            case TEXT:
                try {
                    return JsonTableMetaUtils.parse(flowFile.getTextData());
                } catch (Exception e) {
                    return null;
                }
            default:
                return null;
        }
    }

    /**
     * 解析并补全 JSONPath 表达式，未配置时返回 null。
     */
    private String resolveJsonPath() {
        if (jsonPath == null || jsonPath.trim().isEmpty()) {
            return null;
        }
        String trimmed = jsonPath.trim();
        return trimmed.startsWith("$") ? trimmed : "$." + trimmed;
    }

    private String resolveValueField() {
        return valueField == null || valueField.trim().isEmpty() ? DEFAULT_VALUE_FIELD : valueField.trim();
    }

    public String getJsonPath() {
        return jsonPath;
    }

    public void setJsonPath(String jsonPath) {
        this.jsonPath = jsonPath;
    }

    public String getValueField() {
        return valueField;
    }

    public void setValueField(String valueField) {
        this.valueField = valueField;
    }
}
