package com.data.datafusion.service;

import com.data.datafusion.domain.AnalysisDashboard;
import com.data.datafusion.domain.Metric;
import com.data.datafusion.repository.AnalysisDashboardRepository;
import com.data.datafusion.repository.MetricRepository;
import com.data.datafusion.service.dto.LineageEdgeDTO;
import com.data.datafusion.service.dto.LineageNodeDTO;
import com.data.datafusion.service.dto.MetricLineageDTO;
import com.data.datafusion.service.dto.ModelLineageDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 指标全链路血缘：引用指标、事实模型、物理链路、衍生指标与看板。
 */
@Service
@Transactional(readOnly = true)
public class MetricLineageService {

    private static final Logger LOG = LoggerFactory.getLogger(MetricLineageService.class);

    private static final String TYPE_METRIC = "METRIC";
    private static final String TYPE_DASHBOARD = "DASHBOARD";

    private static final String CONFIDENCE_CONFIG = "CONFIG";
    private static final String CONFIDENCE_PARSED = "PARSED";

    private static final String KIND_METRIC = "METRIC";
    private static final String KIND_DERIVE = "DERIVE";
    private static final String KIND_DASHBOARD = "DASHBOARD";

    private final MetricRepository metricRepository;
    private final AnalysisDashboardRepository analysisDashboardRepository;
    private final DataModelLineageService dataModelLineageService;
    private final MetricService metricService;
    private final ObjectMapper objectMapper;

    public MetricLineageService(
        MetricRepository metricRepository,
        AnalysisDashboardRepository analysisDashboardRepository,
        DataModelLineageService dataModelLineageService,
        MetricService metricService,
        ObjectMapper objectMapper
    ) {
        this.metricRepository = metricRepository;
        this.analysisDashboardRepository = analysisDashboardRepository;
        this.dataModelLineageService = dataModelLineageService;
        this.metricService = metricService;
        this.objectMapper = objectMapper;
    }

    public MetricLineageDTO build(String metricId) {
        Metric current = metricRepository
            .findById(metricId)
            .orElseThrow(() -> new IllegalArgumentException("指标不存在：" + metricId));
        Map<String, Metric> metricsById = metricRepository
            .findAll()
            .stream()
            .collect(Collectors.toMap(Metric::getId, metric -> metric, (left, right) -> left, LinkedHashMap::new));
        Map<String, Metric> metricsByCode = metricsById
            .values()
            .stream()
            .filter(metric -> metric.getCode() != null)
            .collect(Collectors.toMap(Metric::getCode, metric -> metric, (left, right) -> left, LinkedHashMap::new));

        LineageAccumulator accumulator = new LineageAccumulator();
        accumulator.addNode(metricNode(current, 0));
        addUpstreamMetrics(accumulator, current, 0, metricsByCode, new HashSet<>());
        addFactModelLineage(accumulator, metricsById);
        Set<String> downstreamCodes = addDownstreamMetrics(accumulator, current, metricsById, metricsByCode);
        addDashboards(accumulator, downstreamCodes, metricsByCode);

        List<LineageNodeDTO> nodes = new ArrayList<>(accumulator.nodes.values());
        nodes.sort(Comparator.comparingInt(LineageNodeDTO::layer).thenComparing(LineageNodeDTO::name, Comparator.nullsLast(String::compareTo)));
        return new MetricLineageDTO(current.getId(), nodes, new ArrayList<>(accumulator.edges.values()));
    }

    private void addUpstreamMetrics(
        LineageAccumulator accumulator,
        Metric metric,
        int layer,
        Map<String, Metric> metricsByCode,
        Set<String> visited
    ) {
        Set<String> references = metricService.parseFormulaRefs(metric.getFormula());
        for (String code : references) {
            Metric referenced = metricsByCode.get(code);
            if (referenced == null || !visited.add(referenced.getId())) {
                continue;
            }
            accumulator.addNode(metricNode(referenced, layer - 1));
            accumulator.addEdge(
                metricNodeId(referenced.getId()),
                metricNodeId(metric.getId()),
                KIND_DERIVE,
                "引用指标",
                CONFIDENCE_PARSED
            );
            addUpstreamMetrics(accumulator, referenced, layer - 1, metricsByCode, visited);
        }
    }

    private void addFactModelLineage(LineageAccumulator accumulator, Map<String, Metric> metricsById) {
        List<LineageNodeDTO> metricNodes = accumulator.nodes
            .values()
            .stream()
            .filter(node -> TYPE_METRIC.equals(node.type()))
            .toList();
        for (LineageNodeDTO metricNode : metricNodes) {
            String metricId = entityId(metricNode.id());
            Metric metric = metricsById.get(metricId);
            if (metric == null || !Metric.TYPE_ATOMIC.equals(metric.getMetricType())) {
                continue;
            }
            String factModelId = metric.getFactModelId();
            if (factModelId == null || factModelId.isBlank()) {
                continue;
            }
            ModelLineageDTO upstream;
            try {
                upstream = dataModelLineageService.buildUpstream(factModelId);
            } catch (IllegalArgumentException e) {
                LOG.debug("指标 {} 关联的事实模型 {} 不存在，跳过模型血缘", metric.getCode(), factModelId);
                continue;
            }
            int offset = metricNode.layer() - 1;
            for (LineageNodeDTO node : upstream.nodes()) {
                accumulator.mergeUpstreamNode(shiftLayer(node, offset));
            }
            for (LineageEdgeDTO edge : upstream.edges()) {
                accumulator.addEdge(edge.source(), edge.target(), edge.kind(), edge.label(), edge.confidence());
            }
            String modelNodeId = modelNodeId(factModelId);
            if (accumulator.nodes.containsKey(modelNodeId)) {
                accumulator.addEdge(modelNodeId, metricNode.id(), KIND_METRIC, "计算指标", CONFIDENCE_CONFIG);
            }
        }
    }

    private Set<String> addDownstreamMetrics(
        LineageAccumulator accumulator,
        Metric current,
        Map<String, Metric> metricsById,
        Map<String, Metric> metricsByCode
    ) {
        Set<String> downstreamCodes = new LinkedHashSet<>();
        if (current.getCode() != null) {
            downstreamCodes.add(current.getCode());
        }
        Set<String> frontier = new LinkedHashSet<>(downstreamCodes);
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 20) {
            changed = false;
            Set<String> nextFrontier = new LinkedHashSet<>();
            for (Metric metric : metricsById.values()) {
                if (!Metric.TYPE_DERIVED.equals(metric.getMetricType())) {
                    continue;
                }
                Set<String> references = metricService.parseFormulaRefs(metric.getFormula());
                List<Metric> parents = references
                    .stream()
                    .filter(frontier::contains)
                    .map(metricsByCode::get)
                    .filter(parent -> parent != null && accumulator.nodes.containsKey(metricNodeId(parent.getId())))
                    .toList();
                if (parents.isEmpty()) {
                    continue;
                }
                String nodeId = metricNodeId(metric.getId());
                int layer = parents
                    .stream()
                    .mapToInt(parent -> accumulator.nodes.get(metricNodeId(parent.getId())).layer())
                    .max()
                    .orElse(0) +
                1;
                LineageNodeDTO existing = accumulator.nodes.get(nodeId);
                if (existing == null) {
                    accumulator.addNode(metricNode(metric, layer));
                    changed = true;
                } else if (existing.layer() < layer) {
                    accumulator.nodes.put(nodeId, shiftLayer(existing, layer - existing.layer()));
                    changed = true;
                }
                for (Metric parent : parents) {
                    if (
                        accumulator.addEdge(
                            metricNodeId(parent.getId()),
                            nodeId,
                            KIND_DERIVE,
                            "引用指标",
                            CONFIDENCE_PARSED
                        )
                    ) {
                        changed = true;
                    }
                }
                if (metric.getCode() != null) {
                    downstreamCodes.add(metric.getCode());
                    nextFrontier.add(metric.getCode());
                }
            }
            frontier = nextFrontier;
        }
        return downstreamCodes;
    }

    private void addDashboards(LineageAccumulator accumulator, Set<String> metricCodes, Map<String, Metric> metricsByCode) {
        for (AnalysisDashboard dashboard : analysisDashboardRepository.findAll()) {
            JsonNode spec = readJson(dashboard.getSpec());
            if (spec == null) {
                continue;
            }
            JsonNode datasets = spec.path("datasets");
            if (!datasets.isArray()) {
                continue;
            }
            for (JsonNode dataset : datasets) {
                JsonNode codesNode = dataset.path("metricQuery").path("metricCodes");
                if (!codesNode.isArray()) {
                    continue;
                }
                for (JsonNode codeNode : codesNode) {
                    String code = codeNode.asText();
                    Metric metric = metricsByCode.get(code);
                    if (metric == null || !metricCodes.contains(code)) {
                        continue;
                    }
                    LineageNodeDTO metricNode = accumulator.nodes.get(metricNodeId(metric.getId()));
                    if (metricNode == null) {
                        continue;
                    }
                    LineageNodeDTO dashboardNode = accumulator.nodes.get(dashboardNodeId(dashboard.getId()));
                    if (dashboardNode == null) {
                        dashboardNode = dashboardNode(dashboard, metricNode.layer() + 1);
                        accumulator.addNode(dashboardNode);
                    } else if (dashboardNode.layer() < metricNode.layer() + 1) {
                        dashboardNode = shiftLayer(dashboardNode, metricNode.layer() + 1 - dashboardNode.layer());
                        accumulator.nodes.put(dashboardNode.id(), dashboardNode);
                    }
                    accumulator.addEdge(
                        metricNode.id(),
                        dashboardNode.id(),
                        KIND_DASHBOARD,
                        "指标数据集「" + datasetName(dataset) + "」",
                        CONFIDENCE_CONFIG
                    );
                }
            }
        }
    }

    private LineageNodeDTO metricNode(Metric metric, int layer) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("code", metric.getCode());
        meta.put("metricType", metric.getMetricType());
        meta.put("status", metric.getStatus());
        meta.put("unit", metric.getUnit());
        meta.put("factModelId", metric.getFactModelId());
        meta.put("formula", metric.getFormula());
        return new LineageNodeDTO(
            metricNodeId(metric.getId()),
            TYPE_METRIC,
            metric.getName(),
            metric.getMetricType(),
            layer,
            meta
        );
    }

    private LineageNodeDTO dashboardNode(AnalysisDashboard dashboard, int layer) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("code", dashboard.getCode());
        meta.put("status", dashboard.getStatus());
        meta.put("dataSourceId", dashboard.getDataSourceId());
        return new LineageNodeDTO(dashboardNodeId(dashboard.getId()), TYPE_DASHBOARD, dashboard.getName(), dashboard.getStatus(), layer, meta);
    }

    private LineageNodeDTO shiftLayer(LineageNodeDTO node, int offset) {
        return new LineageNodeDTO(node.id(), node.type(), node.name(), node.subType(), node.layer() + offset, node.meta());
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            LOG.debug("解析看板定义失败: {}", e.getMessage());
            return null;
        }
    }

    private String datasetName(JsonNode dataset) {
        JsonNode name = dataset.get("name");
        if (name != null && !name.asText().isBlank()) {
            return name.asText();
        }
        JsonNode id = dataset.get("id");
        return id == null || id.asText().isBlank() ? "数据集" : id.asText();
    }

    private String entityId(String nodeId) {
        int index = nodeId == null ? -1 : nodeId.indexOf(':');
        return index >= 0 ? nodeId.substring(index + 1) : nodeId;
    }

    private String metricNodeId(String id) {
        return "metric:" + id;
    }

    private String modelNodeId(String id) {
        return "model:" + id;
    }

    private String dashboardNodeId(String id) {
        return "dashboard:" + id;
    }

    private static class LineageAccumulator {

        private final Map<String, LineageNodeDTO> nodes = new LinkedHashMap<>();
        private final Map<String, LineageEdgeDTO> edges = new LinkedHashMap<>();

        private void addNode(LineageNodeDTO node) {
            nodes.putIfAbsent(node.id(), node);
        }

        private void mergeUpstreamNode(LineageNodeDTO node) {
            LineageNodeDTO existing = nodes.get(node.id());
            if (existing == null || node.layer() < existing.layer()) {
                nodes.put(node.id(), node);
            }
        }

        private boolean addEdge(String source, String target, String kind, String label, String confidence) {
            if (source == null || target == null || source.equals(target)) {
                return false;
            }
            String id = source + "->" + target + ":" + kind + ":" + label;
            return edges.putIfAbsent(id, new LineageEdgeDTO(id, source, target, kind, label, confidence)) == null;
        }
    }
}
