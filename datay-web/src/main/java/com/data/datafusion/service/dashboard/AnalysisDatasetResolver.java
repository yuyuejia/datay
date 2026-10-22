package com.data.datafusion.service.dashboard;

import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 看板数据集引用解析。
 *
 * <p>看板的 {@code datasets[]} 可以只保存 {@code {id, refId}}，运行时从
 * {@link AnalysisDataset} 读取最新定义并合并为可执行数据集。{@code id} 始终是看板本地别名，
 * 组件与筛选器都通过它引用数据集。
 */
@Component
public class AnalysisDatasetResolver {

    private final AnalysisDatasetRepository repository;
    private final ObjectMapper objectMapper;

    public AnalysisDatasetResolver(AnalysisDatasetRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * 解析 spec 中所有引用了数据集库的 dataset 定义，返回新的 spec 对象（不修改入参）。
     */
    public Map<String, Object> resolveSpec(Map<String, Object> spec) {
        if (spec == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> result = new LinkedHashMap<>(spec);
        List<Map<String, Object>> resolved = new ArrayList<>();
        for (Map<String, Object> dataset : DatasetQueryService.asMapList(spec.get("datasets"))) {
            resolved.add(resolveDataset(dataset));
        }
        result.put("datasets", resolved);
        return result;
    }

    /**
     * 解析单个 dataset；没有 refId 时原样返回。
     */
    public Map<String, Object> resolveDataset(Map<String, Object> dataset) {
        Map<String, Object> copy = new LinkedHashMap<>(dataset == null ? Map.of() : dataset);
        String refId = DatasetQueryService.asString(copy.get("refId"));
        if (refId == null || refId.isBlank()) {
            return copy;
        }
        AnalysisDataset entity = repository
            .findById(refId)
            .orElseThrow(() -> new IllegalArgumentException("引用的数据集不存在：" + refId));
        Map<String, Object> definition = parseDefinition(entity.getDefinition());
        copy.put("type", entity.getType());
        copy.put("refId", refId);
        if (DatasetQueryService.asString(copy.get("name")) == null || DatasetQueryService.asString(copy.get("name")).isBlank()) {
            copy.put("name", entity.getName());
        }
        // 清掉可能存在的旧快照字段，避免数据集更新后仍使用过期定义
        copy.remove("metricQuery");
        copy.remove("sql");
        copy.remove("params");
        if (AnalysisDataset.TYPE_SQL.equalsIgnoreCase(entity.getType())) {
            copy.put("sql", definition.get("sql"));
            if (definition.get("params") != null) {
                copy.put("params", definition.get("params"));
            }
            String dataSourceId = entity.getDataSourceId();
            if (dataSourceId == null || dataSourceId.isBlank()) {
                dataSourceId = DatasetQueryService.asString(definition.get("dataSourceId"));
            }
            if (dataSourceId != null && !dataSourceId.isBlank()) {
                copy.put("dataSourceId", dataSourceId);
            }
        } else {
            Object metricQuery = definition.get("metricQuery");
            copy.put("metricQuery", metricQuery instanceof Map<?, ?> ? metricQuery : definition);
        }
        return copy;
    }

    /**
     * 保存看板时校验引用存在且类型合法。
     */
    public void assertReferenceExists(String refId) {
        if (refId == null || refId.isBlank()) {
            return;
        }
        if (!repository.existsById(refId)) {
            throw new IllegalArgumentException("引用的数据集不存在：" + refId);
        }
    }

    /**
     * 返回引用数据集在库中的名称；用于看板 spec 未提供名称时补全。
     */
    public String findName(String refId) {
        if (refId == null || refId.isBlank()) {
            return null;
        }
        return repository.findById(refId).map(AnalysisDataset::getName).orElse(null);
    }

    /**
     * 收集 spec 中引用到的数据集 id。
     */
    public Set<String> collectRefIds(Map<String, Object> spec) {
        Set<String> refIds = new LinkedHashSet<>();
        if (spec == null) {
            return refIds;
        }
        for (Map<String, Object> dataset : DatasetQueryService.asMapList(spec.get("datasets"))) {
            String refId = DatasetQueryService.asString(dataset.get("refId"));
            if (refId != null && !refId.isBlank()) {
                refIds.add(refId);
            }
        }
        return refIds;
    }

    public Map<String, Object> parseDefinition(String definitionJson) {
        if (definitionJson == null || definitionJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(definitionJson, new TypeReference<>() {});
            return map == null ? new LinkedHashMap<>() : map;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("数据集定义不是合法 JSON：" + e.getOriginalMessage());
        }
    }

    /**
     * 解析看板 spec JSON 中的引用，供血缘分析使用。
     */
    public String resolveSpecJson(String specJson) {
        if (specJson == null || specJson.isBlank()) {
            return specJson;
        }
        Map<String, Object> spec;
        try {
            spec = objectMapper.readValue(specJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("看板定义不是合法 JSON：" + e.getOriginalMessage());
        }
        if (spec == null) {
            return specJson;
        }
        Map<String, Object> resolved = resolveSpec(spec);
        try {
            return objectMapper.writeValueAsString(resolved);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("看板定义序列化失败：" + e.getOriginalMessage());
        }
    }
}
