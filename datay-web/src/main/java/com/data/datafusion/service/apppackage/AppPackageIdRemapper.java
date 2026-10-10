package com.data.datafusion.service.apppackage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 资产包 / ETL 任务导入时通用的 JSON 引用重写工具。
 *
 * <p>把 JSON 中指定字段名（如 {@code sourceId}、{@code modelId}、{@code dimensionModelId}）的
 * 旧 ID 替换为映射表中的新 ID，字段名命中且值能在映射表中找到时替换，找不到则保留原值。
 * 对对象与数组递归处理，保持原有结构。
 */
public final class AppPackageIdRemapper {

    private AppPackageIdRemapper() {}

    /**
     * 递归重写 JSON 中指定字段名的 ID。
     *
     * @param node         待处理的 JSON 节点
     * @param remapByField 字段名 -&gt; （旧 ID -&gt; 新 ID）映射
     * @return 处理后的节点（与入参为同一引用，会就地修改）
     */
    public static JsonNode remapIds(JsonNode node, Map<String, Map<String, String>> remapByField) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                JsonNode value = object.get(name);
                Map<String, String> mapping = remapByField.get(name);
                if (mapping != null && value != null && !value.isContainerNode()) {
                    String mapped = mapping.get(value.asText());
                    if (mapped != null) {
                        object.put(name, mapped);
                        continue;
                    }
                }
                JsonNode remapped = remapIds(value, remapByField);
                if (remapped != null && remapped != value) {
                    object.set(name, remapped);
                }
            }
            return object;
        }
        if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                JsonNode remapped = remapIds(array.get(i), remapByField);
                if (remapped != null && remapped != array.get(i)) {
                    array.set(i, remapped);
                }
            }
            return array;
        }
        return node;
    }
}
