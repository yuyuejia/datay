package com.data.datafusion.service.etl;

import com.data.datafusion.ai.AiResult;
import com.data.datafusion.ai.assistant.EtlComponentDoc;
import com.data.datafusion.ai.assistant.EtlComponentDocRegistry;
import com.data.datafusion.service.DataModelService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.ETLComponentCatalogService;
import com.data.datafusion.service.ETLTaskService;
import com.data.datafusion.service.dto.ETLComponentDTO;
import com.data.datafusion.service.dto.ETLEdgeDTO;
import com.data.datafusion.service.dto.ETLNodeDTO;
import com.data.datafusion.service.dto.ETLTaskDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.quartz.CronExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * ETL 任务定义校验与归一化。
 *
 * <p>负责把 AI 生成的任务 spec 转换为设计器可直接应用的稳定结构：
 * <ul>
 *     <li>校验节点、连线、组件类型与 DAG 结构；</li>
 *     <li>清理引擎级字段（datasource、password、.id 等）；</li>
 *     <li>归一化 tables/incrColumns 等友好字段；</li>
 *     <li>按帮助文档检查必填配置；</li>
 *     <li>补充自动布局，并做一次任务 JSON 预检。</li>
 * </ul>
 *
 * <p>结构性问题（未知组件、重复 key、无效连线、环）直接抛出
 * {@link IllegalArgumentException}；配置缺失、引用无效等可修复问题写入 warnings，
 * 仍允许用户把节点骨架应用到画布后手动补全。
 */
@Service
public class EtlTaskSpecValidator {

    private static final Logger LOG = LoggerFactory.getLogger(EtlTaskSpecValidator.class);

    private static final Set<String> ENGINE_FIELDS = Set.of(
        ".id",
        ".name",
        ".class",
        "parallelism",
        "status",
        "datasource",
        "dbschema",
        "classname"
    );

    /** 数据源连接对象内部的字段，设计器统一用 sourceId 代替。 */
    private static final Set<String> DATASOURCE_FIELDS = Set.of("url", "driver", "username", "password", "type", "dbschema");

    private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "password",
        "passwd",
        "secret",
        "token",
        "apikey",
        "api_key",
        "accesskey",
        "access_key",
        "secretkey",
        "secret_key",
        "privatekey",
        "private_key",
        "authorization"
    );

    private final ETLComponentCatalogService catalogService;
    private final EtlComponentDocRegistry docRegistry;
    private final DataSourceService dataSourceService;
    private final DataModelService dataModelService;
    private final ETLTaskService eTLTaskService;
    private final ObjectMapper objectMapper;

    public EtlTaskSpecValidator(
        ETLComponentCatalogService catalogService,
        EtlComponentDocRegistry docRegistry,
        DataSourceService dataSourceService,
        DataModelService dataModelService,
        ETLTaskService eTLTaskService,
        ObjectMapper objectMapper
    ) {
        this.catalogService = catalogService;
        this.docRegistry = docRegistry;
        this.dataSourceService = dataSourceService;
        this.dataModelService = dataModelService;
        this.eTLTaskService = eTLTaskService;
        this.objectMapper = objectMapper;
    }

    /**
     * 校验并归一化 AI 生成的任务定义。
     *
     * @param raw       AI 返回的原始 spec
     * @param artifacts 本轮工具产物，校验警告会写入 {@link AiResult#ARTIFACT_WARNINGS}
     * @return 可直接应用到设计器的任务定义
     */
    public Map<String, Object> validateAndNormalize(Map<String, Object> raw, Map<String, Object> artifacts) {
        if (raw == null) {
            throw new IllegalArgumentException("未能解析到 ETL 任务定义");
        }
        List<Map<String, Object>> rawNodes = asMapList(raw.get("nodes"));
        List<Map<String, Object>> rawEdges = asMapList(raw.get("edges"));
        if (rawNodes.isEmpty()) {
            throw new IllegalArgumentException("ETL 任务至少需要一个节点");
        }

        Map<String, ETLComponentDTO> catalog = catalogIndex();
        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> nodes = new ArrayList<>();
        Set<String> nodeKeys = new LinkedHashSet<>();

        for (int index = 0; index < rawNodes.size(); index++) {
            Map<String, Object> rawNode = rawNodes.get(index);
            String key = firstNonBlank(asString(rawNode.get("key")), asString(rawNode.get("id")), "n" + (index + 1));
            if (!nodeKeys.add(key)) {
                throw new IllegalArgumentException("节点 key 重复：" + key);
            }

            String rawType = asString(rawNode.get("type"));
            if (isBlank(rawType)) {
                throw new IllegalArgumentException("节点 " + key + " 缺少 type");
            }
            ETLComponentDTO component = catalog.get(rawType.trim().toLowerCase(Locale.ROOT));
            if (component == null) {
                throw new IllegalArgumentException(
                    "未知组件类型：" + rawType + "，请先通过 list_etl_components 查询可用组件"
                );
            }

            Map<String, Object> config = parseConfig(rawNode.get("config"));
            normalizeConfig(component.getCode(), config, warnings);

            Map<String, Object> node = new LinkedHashMap<>();
            node.put("key", key);
            node.put("type", component.getCode());
            node.put(
                "label",
                firstNonBlank(asString(rawNode.get("label")), asString(component.getName()), component.getCode())
            );
            node.put("x", asNumber(rawNode.get("x")));
            node.put("y", asNumber(rawNode.get("y")));
            node.put("config", config);
            nodes.add(node);
        }

        List<Map<String, Object>> edges = normalizeEdges(rawEdges, nodeKeys, warnings);
        detectCycles(nodes, edges);
        applyAutoLayout(nodes, edges);
        warnDisconnectedNodes(nodes, edges, warnings);

        Map<String, Object> result = new LinkedHashMap<>();
        String taskName = asString(raw.get("taskName"));
        if (!isBlank(taskName)) {
            result.put("taskName", taskName.trim());
        }
        String taskDesc = asString(raw.get("taskDesc"));
        if (!isBlank(taskDesc)) {
            result.put("taskDesc", taskDesc.trim());
        }
        String cron = asString(raw.get("cron"));
        if (!isBlank(cron)) {
            String normalizedCron = cron.trim();
            if (CronExpression.isValidExpression(normalizedCron)) {
                result.put("cron", normalizedCron);
            } else {
                warnings.add("Cron 表达式无效，已忽略：" + normalizedCron);
            }
        }
        result.put("nodes", nodes);
        result.put("edges", edges);

        validateWithTaskService(result, warnings);
        if (artifacts != null) {
            artifacts.put(AiResult.ARTIFACT_WARNINGS, List.copyOf(warnings));
        }
        return result;
    }

    // ------------------------------------------------------------------ 节点配置

    private void normalizeConfig(String type, Map<String, Object> config, List<String> warnings) {
        // 1. 清理引擎级字段与敏感字段
        List<String> removeKeys = new ArrayList<>();
        for (String key : new ArrayList<>(config.keySet())) {
            if (key == null) {
                removeKeys.add(key);
                continue;
            }
            String lower = key.toLowerCase(Locale.ROOT);
            if (lower.startsWith(".") || ENGINE_FIELDS.contains(lower)) {
                removeKeys.add(key);
            } else if (
                lower.startsWith("sourceid.") ||
                lower.startsWith("datasource.") ||
                SENSITIVE_FIELDS.contains(lower)
            ) {
                removeKeys.add(key);
            }
        }
        for (String key : removeKeys) {
            config.remove(key);
            warnings.add("节点配置中的引擎级字段已移除：" + key + "（请使用 sourceId + schema）");
        }

        // 2. 归一化 tables -> table
        Object tables = config.remove("tables");
        if (tables != null && !config.containsKey("table")) {
            if (tables instanceof List<?> list) {
                String joined = list.stream().map(String::valueOf).filter(item -> !item.isBlank()).reduce((a, b) -> a + "," + b).orElse("");
                if (!joined.isBlank()) {
                    config.put("table", joined);
                }
            } else if (!String.valueOf(tables).isBlank()) {
                config.put("table", String.valueOf(tables));
            }
        }

        // 3. 归一化 incrColumns -> incrColumn JSON 字符串
        Object incrColumns = config.remove("incrColumns");
        if (incrColumns != null && !config.containsKey("incrColumn")) {
            if (incrColumns instanceof Map<?, ?> map) {
                try {
                    config.put("incrColumn", objectMapper.writeValueAsString(map));
                } catch (Exception e) {
                    warnings.add("增量字段配置序列化失败，请手动配置 incrColumn");
                }
            } else if (!String.valueOf(incrColumns).isBlank()) {
                config.put("incrColumn", String.valueOf(incrColumns));
            }
        }

        // 4. 组件默认值
        if ("StreamJdbcOutput".equals(type) || "JdbcOutput".equals(type)) {
            config.putIfAbsent("model", "overwrite");
        } else if ("ModelWrite".equals(type)) {
            config.putIfAbsent("model", "append");
        }

        // 5. 数据源引用校验
        Object sourceId = config.get("sourceId");
        if (sourceId instanceof Map<?, ?>) {
            config.remove("sourceId");
            warnings.add("sourceId 应为数据源 ID，不能是数据源对象；请通过 list_data_sources 获取 ID");
        } else if (sourceId != null && !String.valueOf(sourceId).isBlank()) {
            String id = String.valueOf(sourceId).trim();
            config.put("sourceId", id);
            var dataSource = dataSourceService.findOne(id);
            if (dataSource.isEmpty()) {
                warnings.add("节点引用的数据源不存在：" + id + "，请在节点配置中重新选择数据源");
            } else if (
                (config.get("schema") == null || String.valueOf(config.get("schema")).isBlank()) &&
                dataSource.get().getSchemaName() != null &&
                !dataSource.get().getSchemaName().isBlank()
            ) {
                config.put("schema", dataSource.get().getSchemaName());
            }
        }

        // 6. 数据模型引用校验
        Object modelId = config.get("modelId");
        if (modelId != null && !String.valueOf(modelId).isBlank()) {
            String id = String.valueOf(modelId).trim();
            config.put("modelId", id);
            if (dataModelService.findOne(id).isEmpty()) {
                warnings.add("节点引用的数据模型不存在：" + id + "，请重新选择数据模型");
            }
        }

        // 7. 根据帮助文档检查必填字段
        EtlComponentDoc doc = docRegistry.find(type).orElse(null);
        if (doc == null) {
            if (config.isEmpty()) {
                warnings.add("组件 " + type + " 暂无详细帮助文档，已生成节点骨架，请手动配置");
            } else {
                warnings.add("组件 " + type + " 暂无详细帮助文档，请核对节点配置");
            }
            return;
        }
        Set<String> requiredFields = extractRequiredFields(doc);
        for (String field : requiredFields) {
            if (!config.containsKey(field) || config.get(field) == null || String.valueOf(config.get(field)).isBlank()) {
                warnings.add("组件 " + type + " 缺少必填配置：" + field);
            }
        }
    }

    private Set<String> extractRequiredFields(EtlComponentDoc doc) {
        Set<String> required = new LinkedHashSet<>();
        collectRequiredFields(doc.configTables(), required);
        collectRequiredFields(doc.designerConfigTables(), required);
        return required;
    }

    private void collectRequiredFields(List<EtlComponentDoc.ConfigTable> tables, Set<String> required) {
        for (EtlComponentDoc.ConfigTable table : tables) {
            if (!isTopLevelConfigTable(table)) {
                continue;
            }
            String requiredHeader = null;
            for (String header : table.headers()) {
                if (header.contains("必填")) {
                    requiredHeader = header;
                    break;
                }
            }
            if (requiredHeader == null) {
                continue;
            }
            for (Map<String, String> row : table.rows()) {
                String field = fieldName(row, table.headers());
                if (field.isBlank() || !isDesignerField(field)) {
                    continue;
                }
                String requiredValue = row.getOrDefault(requiredHeader, "");
                if (isRequiredValue(requiredValue)) {
                    required.add(field);
                }
            }
        }
    }

    /**
     * 只使用顶层配置表做必填检查，避免把 joins[].table、rules[].column 等嵌套字段
     * 误当成节点顶层必填项。
     */
    private boolean isTopLevelConfigTable(EtlComponentDoc.ConfigTable table) {
        String section = table.section() == null ? "" : table.section().toLowerCase(Locale.ROOT);
        if (
            section.contains("元素") ||
            section.contains("规则") ||
            section.contains("动态参数") ||
            section.contains("详细说明") ||
            section.contains("示例") ||
            section.contains("用法")
        ) {
            return false;
        }
        return (
            section.contains("配置参数") ||
            section.contains("必需参数") ||
            section.contains("可选参数") ||
            section.contains("基础配置") ||
            section.contains("参数说明") ||
            section.contains("表配置")
        );
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
        if (!headers.isEmpty()) {
            String value = row.get(headers.get(0));
            if (value != null) {
                return value.replace("`", "").trim();
            }
        }
        return "";
    }

    private boolean isDesignerField(String field) {
        String lower = field.toLowerCase(Locale.ROOT);
        return (
            !lower.startsWith(".") &&
            !lower.startsWith("sourceid.") &&
            !lower.startsWith("datasource.") &&
            !lower.startsWith("extraparams.") &&
            !ENGINE_FIELDS.contains(lower) &&
            !DATASOURCE_FIELDS.contains(lower) &&
            !SENSITIVE_FIELDS.contains(lower)
        );
    }

    private boolean isRequiredValue(String value) {
        if (value == null) {
            return false;
        }
        String text = value.trim().toLowerCase(Locale.ROOT);
        if (text.contains("否") || text.contains("❌") || text.equals("no") || text.equals("n") || text.equals("false")) {
            return false;
        }
        return text.contains("是") || text.contains("✅") || text.equals("true") || text.equals("y") || text.contains("必填");
    }

    // ------------------------------------------------------------------ 连线与结构

    private List<Map<String, Object>> normalizeEdges(
        List<Map<String, Object>> rawEdges,
        Set<String> nodeKeys,
        List<String> warnings
    ) {
        List<Map<String, Object>> edges = new ArrayList<>();
        Set<String> signatures = new HashSet<>();
        for (Map<String, Object> rawEdge : rawEdges) {
            String source = asString(rawEdge.get("source"));
            String target = asString(rawEdge.get("target"));
            if (isBlank(source) || isBlank(target)) {
                throw new IllegalArgumentException("连线缺少 source 或 target");
            }
            source = source.trim();
            target = target.trim();
            if (!nodeKeys.contains(source)) {
                throw new IllegalArgumentException("连线引用了不存在的节点：" + source);
            }
            if (!nodeKeys.contains(target)) {
                throw new IllegalArgumentException("连线引用了不存在的节点：" + target);
            }
            if (source.equals(target)) {
                throw new IllegalArgumentException("连线不能连接节点自身：" + source);
            }
            String label = asString(rawEdge.get("label"));
            label = label == null ? "" : label.trim();
            String signature = source + "->" + target + "|" + label;
            if (!signatures.add(signature)) {
                warnings.add("重复连线已忽略：" + source + " -> " + target);
                continue;
            }
            Map<String, Object> edge = new LinkedHashMap<>();
            edge.put("source", source);
            edge.put("target", target);
            edge.put("label", label);
            edges.add(edge);
        }
        return edges;
    }

    private void detectCycles(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        Map<String, Integer> indegree = new LinkedHashMap<>();
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            String key = String.valueOf(node.get("key"));
            indegree.put(key, 0);
            adjacency.put(key, new ArrayList<>());
        }
        for (Map<String, Object> edge : edges) {
            String source = String.valueOf(edge.get("source"));
            String target = String.valueOf(edge.get("target"));
            adjacency.computeIfAbsent(source, ignored -> new ArrayList<>()).add(target);
            indegree.merge(target, 1, Integer::sum);
        }
        Deque<String> queue = new ArrayDeque<>();
        indegree.forEach((key, value) -> {
            if (value == 0) {
                queue.add(key);
            }
        });
        int visited = 0;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            visited++;
            for (String next : adjacency.getOrDefault(current, List.of())) {
                int remaining = indegree.merge(next, -1, Integer::sum);
                if (remaining == 0) {
                    queue.add(next);
                }
            }
        }
        if (visited < nodes.size()) {
            throw new IllegalArgumentException("任务连线存在环，ETL 任务必须是有向无环图");
        }
    }

    private void applyAutoLayout(List<Map<String, Object>> nodes, List<Map<String, Object>> edges) {
        Map<String, Integer> depth = new LinkedHashMap<>();
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        for (Map<String, Object> node : nodes) {
            depth.put(String.valueOf(node.get("key")), 0);
            adjacency.put(String.valueOf(node.get("key")), new ArrayList<>());
        }
        for (Map<String, Object> edge : edges) {
            adjacency.computeIfAbsent(String.valueOf(edge.get("source")), ignored -> new ArrayList<>()).add(
                String.valueOf(edge.get("target"))
            );
        }
        Deque<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : depth.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }
        // 拓扑顺序计算最长路径深度
        Map<String, Integer> indegree = new LinkedHashMap<>();
        depth.keySet().forEach(key -> indegree.put(key, 0));
        for (List<String> targets : adjacency.values()) {
            for (String target : targets) {
                indegree.merge(target, 1, Integer::sum);
            }
        }
        queue.clear();
        indegree.forEach((key, value) -> {
            if (value == 0) {
                queue.add(key);
            }
        });
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDepth = depth.getOrDefault(current, 0);
            for (String next : adjacency.getOrDefault(current, List.of())) {
                depth.merge(next, currentDepth + 1, Math::max);
                int remaining = indegree.merge(next, -1, Integer::sum);
                if (remaining == 0) {
                    queue.add(next);
                }
            }
        }
        for (int index = 0; index < nodes.size(); index++) {
            Map<String, Object> node = nodes.get(index);
            String key = String.valueOf(node.get("key"));
            Number x = (Number) node.get("x");
            Number y = (Number) node.get("y");
            if (x == null) {
                x = 100 + depth.getOrDefault(key, 0) * 280;
                node.put("x", x);
            }
            if (y == null) {
                y = 120 + index * 160;
                node.put("y", y);
            }
        }
    }

    private void warnDisconnectedNodes(List<Map<String, Object>> nodes, List<Map<String, Object>> edges, List<String> warnings) {
        if (nodes.size() <= 1) {
            return;
        }
        Set<String> connected = new HashSet<>();
        for (Map<String, Object> edge : edges) {
            connected.add(String.valueOf(edge.get("source")));
            connected.add(String.valueOf(edge.get("target")));
        }
        for (Map<String, Object> node : nodes) {
            String key = String.valueOf(node.get("key"));
            if (!connected.contains(key)) {
                warnings.add("节点 " + key + " 没有连线，请确认是否需要保留");
            }
        }
    }

    // ------------------------------------------------------------------ 语义预检

    private void validateWithTaskService(Map<String, Object> spec, List<String> warnings) {
        try {
            ETLTaskDTO task = new ETLTaskDTO();
            task.setTaskName(asString(spec.get("taskName")));
            task.setTaskDesc(asString(spec.get("taskDesc")));
            task.setCron(asString(spec.get("cron")));
            List<ETLNodeDTO> nodeDtos = new ArrayList<>();
            for (Map<String, Object> node : asMapList(spec.get("nodes"))) {
                ETLNodeDTO dto = new ETLNodeDTO();
                dto.setCode(asString(node.get("key")));
                dto.setLabel(asString(node.get("label")));
                dto.setType(asString(node.get("type")));
                dto.setConfig(toJson(node.get("config")));
                dto.setxAxis(String.valueOf(node.get("x")));
                dto.setyAxis(String.valueOf(node.get("y")));
                nodeDtos.add(dto);
            }
            List<ETLEdgeDTO> edgeDtos = new ArrayList<>();
            int index = 0;
            for (Map<String, Object> edge : asMapList(spec.get("edges"))) {
                ETLEdgeDTO dto = new ETLEdgeDTO();
                dto.setCode("e" + (++index));
                dto.setSource(asString(edge.get("source")));
                dto.setTarget(asString(edge.get("target")));
                dto.setName(asString(edge.get("label")));
                dto.setConfig("{\"queueCapacity\":10}");
                edgeDtos.add(dto);
            }
            task.setNodes(nodeDtos);
            task.setEdges(edgeDtos);
            eTLTaskService.generateETLJobJson(task);
        } catch (Exception e) {
            LOG.debug("ETL task semantic pre-check failed: {}", e.getMessage());
            warnings.add("任务定义预检未通过：" + truncate(e.getMessage(), 500));
        }
    }

    // ------------------------------------------------------------------ 通用工具

    private Map<String, ETLComponentDTO> catalogIndex() {
        Map<String, ETLComponentDTO> index = new LinkedHashMap<>();
        for (ETLComponentDTO dto : catalogService.listCatalog()) {
            if (dto != null && dto.getCode() != null) {
                index.put(dto.getCode().trim().toLowerCase(Locale.ROOT), dto);
            }
        }
        return index;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseConfig(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> config = new LinkedHashMap<>();
            map.forEach((key, value) -> config.put(String.valueOf(key), value));
            return config;
        }
        if (raw instanceof String text && !text.isBlank()) {
            try {
                return objectMapper.readValue(text, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                return new LinkedHashMap<>();
            }
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMapList(Object raw) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> converted = new LinkedHashMap<>();
                    map.forEach((key, value) -> converted.put(String.valueOf(key), value));
                    result.add(converted);
                }
            }
        }
        return result;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private Number asNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        if (value != null) {
            try {
                return Double.parseDouble(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                // 非法坐标交给自动布局
            }
        }
        return null;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
