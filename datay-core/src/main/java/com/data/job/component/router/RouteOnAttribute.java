package com.data.job.component.router;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.job.ComponentRegister;
import com.data.job.Connection;
import com.data.job.FlowComponent;
import com.data.job.FlowFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * 属性路由组件。
 *
 * <p>根据 FlowFile 的属性或数据项与下游连线标签的匹配结果，将 FlowFile 自动分流到对应连线上。
 * 匹配模式由 {@code matchMode} 指定：
 * <ul>
 *     <li>{@code attribute}（默认，属性名模式）：FlowFile 中存在与连线标签同名的属性时，路由到该连线；</li>
 *     <li>{@code value}（属性值模式）：{@code attributeName} 指定属性的值等于连线标签时，路由到该连线；</li>
 *     <li>{@code data}（数据项模式）：按 {@code dataField} 指定的字段逐条检查 FlowFile 的记录，
 *     字段值等于连线标签的记录分流到该连线，支持一次 FlowFile 拆分到多条连线。</li>
 * </ul>
 *
 * <p>当没有任何连线匹配时，会尝试路由到 {@code defaultLabel}（默认 {@code other}）对应的连线；
 * 若该连线不存在则丢弃该数据并记录告警。
 *
 * <p>配置示例：
 * <pre>
 * {
 *   "matchMode": "data",
 *   "dataField": "type",
 *   "defaultLabel": "other"
 * }
 * </pre>
 */
@ComponentRegister(
    value = "RouteOnAttribute",
    name = "属性路由",
    group = "数据处理",
    desc = "按 FlowFile 属性名/属性值或数据项字段与下游连线标签匹配，命中的 FlowFile（或记录）自动分流到对应连线，未匹配走 other 兜底连线。",
    order = 85
)
public class RouteOnAttribute extends FlowComponent {

    public static final String MODE_ATTRIBUTE = "attribute";
    public static final String MODE_VALUE = "value";
    public static final String MODE_DATA = "data";

    /** 未匹配时的默认兜底连线标签。 */
    public static final String DEFAULT_LABEL = "other";

    /** 匹配模式：attribute（属性名，默认）、value（属性值）或 data（数据项）。 */
    private String matchMode;

    /** 属性值模式下用于取值的属性名。 */
    private String attributeName;

    /** 数据项模式下用于匹配的记录字段名。 */
    private String dataField;

    /** 无匹配连线时的兜底连线标签，默认 other。 */
    private String defaultLabel;

    public RouteOnAttribute() {
        setType(ComponentType.OPERATOR);
    }

    @Override
    public void execute(FlowFile flowFile) {
        // 结束信号交由基类 writeEndRecord 广播，避免重复分流
        if (flowFile.getAttribute("_end") != null) {
            return;
        }
        if (MODE_DATA.equalsIgnoreCase(resolvedMatchMode())) {
            routeByData(flowFile);
            return;
        }
        Connection target = matchConnection(flowFile);
        if (target == null) {
            target = findConnectionByLabel(resolvedDefaultLabel());
        }
        if (target == null) {
            logWarn(
                "RouteOnAttribute: 未匹配到任何连线（matchMode=" + resolvedMatchMode() + ", attributeName=" + attributeName + "），已丢弃该 FlowFile"
            );
            return;
        }
        routeToConnection(flowFile, target);
    }

    /**
     * 数据项模式：逐条读取记录中 {@code dataField} 字段的值，按连线标签分组后再分别下发。
     * 未匹配的记录走 {@code other} 兜底连线。
     */
    private void routeByData(FlowFile flowFile) {
        List<Object> items = extractDataItems(flowFile);
        if (items.isEmpty()) {
            logWarn("RouteOnAttribute: 数据项模式下 FlowFile 无可匹配的记录，已忽略");
            return;
        }
        String field = dataField == null ? null : dataField.trim();
        if (field == null || field.isEmpty()) {
            logWarn("RouteOnAttribute: 数据项模式下未配置字段名（dataField），已忽略");
            return;
        }

        // targetId -> 命中的记录集合（保持出现顺序）
        Map<String, JSONArray> groups = new LinkedHashMap<>();
        // targetId -> 目标连线
        Map<String, Connection> targetConnections = new LinkedHashMap<>();
        int unmatched = 0;

        for (Object item : items) {
            String value = extractFieldValue(item, field);
            Connection target = value == null ? null : findConnectionByLabel(value);
            if (target == null) {
                target = findConnectionByLabel(resolvedDefaultLabel());
            }
            if (target == null) {
                unmatched++;
                continue;
            }
            groups.computeIfAbsent(target.getTargetId(), key -> new JSONArray()).add(item);
            targetConnections.putIfAbsent(target.getTargetId(), target);
        }

        for (Map.Entry<String, JSONArray> entry : groups.entrySet()) {
            FlowFile out = buildDataFlowFile(flowFile, entry.getValue());
            routeToConnection(out, targetConnections.get(entry.getKey()));
        }
        if (unmatched > 0) {
            logWarn("RouteOnAttribute: 有 " + unmatched + " 条记录未匹配任何连线（含兜底连线 " + resolvedDefaultLabel() + "），已丢弃");
        }
    }

    /**
     * 提取 FlowFile 中的可匹配记录。JSON 数组按元素拆分，JSON 对象视为单条记录。
     */
    private List<Object> extractDataItems(FlowFile flowFile) {
        if (flowFile.getDataFormat() == FlowFile.DataFormat.JSON_ARRAY) {
            JSONArray array = flowFile.getJsonArray();
            if (array == null || array.isEmpty()) {
                return Collections.emptyList();
            }
            return new ArrayList<>(array);
        }
        if (flowFile.getDataFormat() == FlowFile.DataFormat.JSON_OBJECT) {
            JSONObject object = flowFile.getJsonObject();
            if (object == null || object.isEmpty()) {
                return Collections.emptyList();
            }
            return Collections.singletonList(object);
        }
        return Collections.emptyList();
    }

    /**
     * 读取一条记录中指定字段的值并转为字符串。
     */
    private String extractFieldValue(Object item, String field) {
        if (!(item instanceof JSONObject)) {
            return String.valueOf(item);
        }
        Object value = ((JSONObject) item).get(field);
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 基于原 FlowFile 的属性构造承载匹配记录的新 FlowFile。
     */
    private FlowFile buildDataFlowFile(FlowFile source, JSONArray records) {
        FlowFile out = new FlowFile();
        out.getAttributeMap().putAll(source.getAttributeMap());
        out.setJsonArray(records);
        return out;
    }

    /**
     * 按配置的匹配模式查找命中的输出连线。
     */
    private Connection matchConnection(FlowFile flowFile) {
        if (MODE_VALUE.equalsIgnoreCase(resolvedMatchMode())) {
            return matchByValue(flowFile);
        }
        return matchByAttributeName(flowFile);
    }

    /**
     * 属性名模式：FlowFile 存在与连线标签同名的属性即命中。
     */
    private Connection matchByAttributeName(FlowFile flowFile) {
        for (Connection connection : getOutput()) {
            String label = normalizeLabel(connection.getLabel());
            if (label != null && flowFile.getAttribute(label) != null) {
                return connection;
            }
        }
        return null;
    }

    /**
     * 属性值模式：指定属性的值等于连线标签即命中。
     */
    private Connection matchByValue(FlowFile flowFile) {
        if (attributeName == null || attributeName.trim().isEmpty()) {
            return null;
        }
        Object value = flowFile.getAttribute(attributeName);
        if (value == null) {
            return null;
        }
        return findConnectionByLabel(String.valueOf(value));
    }

    private Connection findConnectionByLabel(String label) {
        String target = normalizeLabel(label);
        if (target == null) {
            return null;
        }
        for (Connection connection : getOutput()) {
            if (target.equals(normalizeLabel(connection.getLabel()))) {
                return connection;
            }
        }
        return null;
    }

    private String normalizeLabel(String label) {
        if (label == null) {
            return null;
        }
        String trimmed = label.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolvedMatchMode() {
        return (matchMode == null || matchMode.trim().isEmpty()) ? MODE_ATTRIBUTE : matchMode.trim();
    }

    private String resolvedDefaultLabel() {
        String label = normalizeLabel(defaultLabel);
        return label != null ? label : DEFAULT_LABEL;
    }

    /**
     * 将 FlowFile 写入目标连线对应的队列。
     */
    private void routeToConnection(FlowFile flowFile, Connection connection) {
        BlockingQueue<Object> queue = getContext().getConnections().get(this.getId() + "_" + connection.getTargetId());
        if (queue == null) {
            logError("RouteOnAttribute: 无法找到目标队列，连接ID: " + this.getId() + "_" + connection.getTargetId());
            return;
        }
        try {
            queue.put(flowFile);
            logDebug("RouteOnAttribute: 将 FlowFile 路由到连线标签 [" + connection.getLabel() + "]，目标节点: " + connection.getTargetId());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("RouteOnAttribute 路由被中断", e);
        }
    }

    public String getMatchMode() {
        return matchMode;
    }

    public void setMatchMode(String matchMode) {
        this.matchMode = matchMode;
    }

    public String getAttributeName() {
        return attributeName;
    }

    public void setAttributeName(String attributeName) {
        this.attributeName = attributeName;
    }

    public String getDataField() {
        return dataField;
    }

    public void setDataField(String dataField) {
        this.dataField = dataField;
    }

    public String getDefaultLabel() {
        return defaultLabel;
    }

    public void setDefaultLabel(String defaultLabel) {
        this.defaultLabel = defaultLabel;
    }
}
