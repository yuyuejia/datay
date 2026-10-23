package com.data.datafusion.service;

import com.data.datafusion.domain.AnalysisDashboard;
import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.DataSource;
import com.data.datafusion.domain.DataSync;
import com.data.datafusion.domain.DataSyncTableConfig;
import com.data.datafusion.domain.ETLEdge;
import com.data.datafusion.domain.ETLNode;
import com.data.datafusion.domain.ETLTask;
import com.data.datafusion.domain.Metric;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.repository.AnalysisDashboardRepository;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.DataSourceRepository;
import com.data.datafusion.repository.DataSyncRepository;
import com.data.datafusion.repository.DataSyncTableConfigRepository;
import com.data.datafusion.repository.ETLEdgeRepository;
import com.data.datafusion.repository.ETLNodeRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.repository.MetricRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.dashboard.AnalysisDatasetResolver;
import com.data.datafusion.service.dto.LineageEdgeDTO;
import com.data.datafusion.service.dto.LineageNodeDTO;
import com.data.datafusion.service.dto.ModelLineageDTO;
import com.data.datafusion.service.dto.TableLineageDTO;
import com.data.datafusion.service.lineage.SqlLineageParser;
import com.data.datafusion.service.lineage.SqlReferences;
import com.data.datafusion.service.lineage.TableRef;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据模型全链路血缘：物理表、数据同步、ETL、事实/维度、指标与看板。
 */
@Service
@Transactional(readOnly = true)
public class DataModelLineageService {

    private static final Logger LOG = LoggerFactory.getLogger(DataModelLineageService.class);

    private static final String TYPE_MODEL = "MODEL";
    private static final String TYPE_TABLE = "TABLE";
    private static final String TYPE_SYNC = "SYNC";
    private static final String TYPE_ETL = "ETL";
    private static final String TYPE_METRIC = "METRIC";
    private static final String TYPE_DASHBOARD = "DASHBOARD";
    private static final String TYPE_DATASET = "DATASET";

    private static final String CONFIDENCE_CONFIG = "CONFIG";
    private static final String CONFIDENCE_PARSED = "PARSED";
    private static final String CONFIDENCE_INFERRED = "INFERRED";

    private static final Pattern METRIC_FORMULA_REF = Pattern.compile("\\$\\{([A-Za-z0-9_\\-]+)}");

    private static final Set<String> DIRECT_OUTPUT_TYPES = Set.of(
        "STREAMJDBCOUTPUT",
        "JDBCOUTPUT",
        "DORISSTREAMLOAD",
        "DUCKLAKEWRITE",
        "ELASTICSEARCHOUTPUT",
        "MONGODBOUTPUT",
        "INFLUXDBOUTPUT",
        "DUCKDBWRITE"
    );

    private static final Set<String> DIRECT_INPUT_TYPES = Set.of("JDBCINPUT", "STREAMJDBCINPUT");

    private final DataModelRepository dataModelRepository;
    private final ModelFieldRepository modelFieldRepository;
    private final MetricRepository metricRepository;
    private final DataSyncTableConfigRepository dataSyncTableConfigRepository;
    private final DataSyncRepository dataSyncRepository;
    private final ETLNodeRepository etlNodeRepository;
    private final ETLEdgeRepository etlEdgeRepository;
    private final ETLTaskRepository etlTaskRepository;
    private final AnalysisDashboardRepository analysisDashboardRepository;
    private final DataSourceRepository dataSourceRepository;
    private final SqlLineageParser sqlLineageParser;
    private final ObjectMapper objectMapper;
    private final AnalysisDatasetResolver analysisDatasetResolver;

    public DataModelLineageService(
        DataModelRepository dataModelRepository,
        ModelFieldRepository modelFieldRepository,
        MetricRepository metricRepository,
        DataSyncTableConfigRepository dataSyncTableConfigRepository,
        DataSyncRepository dataSyncRepository,
        ETLNodeRepository etlNodeRepository,
        ETLEdgeRepository etlEdgeRepository,
        ETLTaskRepository etlTaskRepository,
        AnalysisDashboardRepository analysisDashboardRepository,
        DataSourceRepository dataSourceRepository,
        SqlLineageParser sqlLineageParser,
        ObjectMapper objectMapper,
        AnalysisDatasetResolver analysisDatasetResolver
    ) {
        this.dataModelRepository = dataModelRepository;
        this.modelFieldRepository = modelFieldRepository;
        this.metricRepository = metricRepository;
        this.dataSyncTableConfigRepository = dataSyncTableConfigRepository;
        this.dataSyncRepository = dataSyncRepository;
        this.etlNodeRepository = etlNodeRepository;
        this.etlEdgeRepository = etlEdgeRepository;
        this.etlTaskRepository = etlTaskRepository;
        this.analysisDashboardRepository = analysisDashboardRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.sqlLineageParser = sqlLineageParser;
        this.objectMapper = objectMapper;
        this.analysisDatasetResolver = analysisDatasetResolver;
    }

    public ModelLineageDTO build(String modelId) {
        return build(modelId, true);
    }

    /**
     * 只构建模型及其上游（物理表、同步、ETL 写入、维度），供指标血缘复用。
     */
    public ModelLineageDTO buildUpstream(String modelId) {
        return build(modelId, false);
    }

    /**
     * 构建物理表血缘：表本身、同步/ETL，以及绑定到该表的数据模型及其下游。
     */
    public TableLineageDTO buildTable(String dataSourceId, String schema, String table) {
        if (dataSourceId == null || dataSourceId.isBlank() || table == null || table.isBlank()) {
            throw new IllegalArgumentException("数据源、Schema 和表名不能为空");
        }
        DataModel synthetic = new DataModel();
        synthetic.setId("table:" + dataSourceId + ":" + joinTable(schema, table).toLowerCase(Locale.ROOT));
        synthetic.setName(joinTable(schema, table));
        synthetic.setDataSourceId(dataSourceId);
        synthetic.setSchemaName(schema);
        synthetic.setTableName(table);

        LineageContext context = new LineageContext(synthetic);
        context.currentTableNode = addNode(context, tableNode(dataSourceId, schema, table, 0, null));
        addSyncLineage(context);
        addEtlLineage(context, true);
        expandTableDownstream(
            context,
            new TableTarget(dataSourceId, schema, table),
            context.currentTableNode,
            new HashSet<>(),
            new HashSet<>()
        );
        addDashboards(context);

        List<LineageNodeDTO> nodes = new ArrayList<>(context.nodes.values());
        nodes.sort(Comparator.comparingInt(LineageNodeDTO::layer).thenComparing(LineageNodeDTO::name, Comparator.nullsLast(String::compareTo)));
        return new TableLineageDTO(context.currentTableNode.id(), nodes, new ArrayList<>(context.edges.values()));
    }

    /**
     * 从物理表继续向下展开：绑定模型、模型下游，以及读取该表的 ETL 任务和它们的输出表。
     */
    private void expandTableDownstream(
        LineageContext context,
        TableTarget target,
        LineageNodeDTO tableNode,
        Set<String> visitedTables,
        Set<String> visitedTasks
    ) {
        if (!visitedTables.add(tableNode.id())) {
            return;
        }
        addBoundModels(context, target, tableNode);
        for (ETLNode node : etlNodeRepository.findAll()) {
            JsonNode config = readJson(node.getConfig());
            if (config == null || node.getTaskId() == null || node.getTaskId().isBlank()) {
                continue;
            }
            if (readTargets(node, config, context).stream().noneMatch(read -> sameTarget(read, target))) {
                continue;
            }
            expandEtlTaskDownstream(context, node.getTaskId(), tableNode.layer() + 1, tableNode.id(), visitedTables, visitedTasks);
        }
    }

    private void addBoundModels(LineageContext context, TableTarget target, LineageNodeDTO tableNode) {
        for (DataModel model : dataModelRepository.findAll()) {
            if (!modelMatchesTarget(model, target)) {
                continue;
            }
            LineageNodeDTO modelNode = modelNode(model, tableNode.layer() + 1);
            addNode(context, modelNode);
            try {
                ModelLineageDTO modelLineage = build(model.getId());
                for (LineageNodeDTO node : modelLineage.nodes()) {
                    addNode(context, shiftLayer(node, tableNode.layer() + 1));
                }
                for (LineageEdgeDTO edge : modelLineage.edges()) {
                    addEdge(context, edge.source(), edge.target(), edge.kind(), edge.label(), edge.confidence());
                }
            } catch (IllegalArgumentException e) {
                LOG.debug("构建模型 {} 血缘失败: {}", model.getId(), e.getMessage());
            }
            boolean linked = context.edges.values().stream().anyMatch(edge ->
                tableNode.id().equals(edge.source()) && modelNode.id().equals(edge.target())
            );
            if (!linked) {
                addEdge(context, tableNode.id(), modelNode.id(), "TABLE", "绑定模型", CONFIDENCE_CONFIG);
            }
        }
    }

    private void expandEtlTaskDownstream(
        LineageContext context,
        String taskId,
        int taskLayer,
        String inputTableNodeId,
        Set<String> visitedTables,
        Set<String> visitedTasks
    ) {
        if (!visitedTasks.add(taskId)) {
            return;
        }
        List<ETLNode> taskNodes = etlNodeRepository.findAllByTaskId(taskId).orElse(List.of());
        List<ETLEdge> taskEdges = etlEdgeRepository.findAllByTaskId(taskId).orElse(List.of());
        TableTarget inputTable = context.nodes.containsKey(inputTableNodeId)
            ? new TableTarget(
                Objects.toString(context.nodes.get(inputTableNodeId).meta().get("dataSourceId"), null),
                Objects.toString(context.nodes.get(inputTableNodeId).meta().get("schema"), null),
                Objects.toString(context.nodes.get(inputTableNodeId).meta().get("table"), null)
            )
            : null;
        if (inputTable == null) {
            return;
        }
        Map<String, ETLNode> nodesByCode = taskNodes
            .stream()
            .filter(node -> node.getCode() != null)
            .collect(Collectors.toMap(ETLNode::getCode, node -> node, (left, right) -> left, LinkedHashMap::new));
        Set<String> startCodes = taskNodes
            .stream()
            .filter(node -> node.getCode() != null)
            .filter(node -> {
                JsonNode config = readJson(node.getConfig());
                return config != null && readTargets(node, config, context).stream().anyMatch(read -> sameTarget(read, inputTable));
            })
            .map(ETLNode::getCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (startCodes.isEmpty()) {
            return;
        }
        Set<String> reachable = forwardReachable(startCodes, taskEdges);
        ETLTask task = context.taskCache.get(taskId);
        if (task == null && !context.taskCache.containsKey(taskId)) {
            task = etlTaskRepository.findById(taskId).orElse(null);
            context.taskCache.put(taskId, task);
        }
        String taskName = task == null ? "ETL 任务 " + taskId : firstNonBlank(task.getTaskName(), task.getTaskCode(), taskId);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("taskCode", task == null ? null : task.getTaskCode());
        meta.put("status", task == null ? null : task.getStatus());
        LineageNodeDTO taskNode = addNode(
            context,
            new LineageNodeDTO(etlNodeId(taskId), TYPE_ETL, taskName, null, taskLayer, meta)
        );
        boolean linked = context.edges.values().stream().anyMatch(edge ->
            inputTableNodeId.equals(edge.source()) && taskNode.id().equals(edge.target()) && "READ".equals(edge.kind())
        );
        if (!linked) {
            addEdge(context, inputTableNodeId, taskNode.id(), "READ", "读取物理表", CONFIDENCE_CONFIG);
        }
        for (String code : reachable) {
            ETLNode node = nodesByCode.get(code);
            if (node == null) {
                continue;
            }
            JsonNode config = readJson(node.getConfig());
            if (config == null) {
                continue;
            }
            for (TableTarget output : writeTargets(node, config, context)) {
                if (sameTarget(output, inputTable)) {
                    continue;
                }
                LineageNodeDTO outputTable = addNode(
                    context,
                    tableNode(output.dataSourceId(), output.schema(), output.table(), taskNode.layer() + 1, null)
                );
                addEdge(
                    context,
                    taskNode.id(),
                    outputTable.id(),
                    "WRITE",
                    "节点「" + firstNonBlank(node.getLabel(), node.getType(), code) + "」写入",
                    CONFIDENCE_CONFIG
                );
                expandTableDownstream(context, output, outputTable, visitedTables, visitedTasks);
            }
        }
    }

    private Set<String> forwardReachable(Set<String> starts, List<ETLEdge> edges) {
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        for (ETLEdge edge : edges) {
            if (edge.getSource() == null || edge.getTarget() == null) {
                continue;
            }
            adjacency.computeIfAbsent(edge.getSource(), key -> new ArrayList<>()).add(edge.getTarget());
        }
        Set<String> visited = new LinkedHashSet<>(starts);
        Deque<String> queue = new ArrayDeque<>(starts);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String next : adjacency.getOrDefault(current, List.of())) {
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return visited;
    }

    private List<TableTarget> readTargets(ETLNode node, JsonNode config, LineageContext context) {
        String type = normalizeType(node.getType());
        if (DIRECT_INPUT_TYPES.contains(type)) {
            String dataSourceId = firstText(config, "sourceId", "dataSourceId");
            String schema = text(config, "schema");
            String table = text(config, "table");
            if (dataSourceId == null || table == null) {
                return List.of();
            }
            List<TableTarget> targets = new ArrayList<>();
            for (String item : table.split(",")) {
                if (!item.isBlank()) {
                    targets.add(new TableTarget(dataSourceId, schema, item.trim()));
                }
            }
            return targets;
        }
        if (type.contains("SQL")) {
            String sql = extractSql(config);
            if (sql == null || sql.isBlank()) {
                return List.of();
            }
            String dataSourceId = firstText(config, "sourceId", "dataSourceId");
            if (dataSourceId == null) {
                dataSourceId = duckDbDataSource(context);
            }
            if (dataSourceId == null) {
                return List.of();
            }
            String sourceDataSourceId = dataSourceId;
            return sqlLineageParser
                .parse(sql)
                .reads()
                .stream()
                .map(table -> new TableTarget(sourceDataSourceId, table.schema(), table.table()))
                .toList();
        }
        return List.of();
    }

    private List<TableTarget> writeTargets(ETLNode node, JsonNode config, LineageContext context) {
        String type = normalizeType(node.getType());
        if ("MODELWRITE".equals(type)) {
            String modelId = text(config, "modelId");
            DataModel model = context.model(modelId);
            if (model == null || model.getDataSourceId() == null || model.getTableName() == null) {
                return List.of();
            }
            return List.of(new TableTarget(model.getDataSourceId(), model.getSchemaName(), model.getTableName()));
        }
        if (DIRECT_OUTPUT_TYPES.contains(type)) {
            String dataSourceId = firstText(config, "sourceId", "dataSourceId");
            if (dataSourceId == null && "DUCKDBWRITE".equals(type)) {
                dataSourceId = duckDbDataSource(context);
            }
            String schema = text(config, "schema");
            String table = text(config, "table");
            if (dataSourceId == null || table == null) {
                return List.of();
            }
            return List.of(new TableTarget(dataSourceId, schema, table));
        }
        if (type.contains("SQL")) {
            String sql = extractSql(config);
            if (sql == null || sql.isBlank()) {
                return List.of();
            }
            String dataSourceId = firstText(config, "sourceId", "dataSourceId");
            if (dataSourceId == null) {
                dataSourceId = duckDbDataSource(context);
            }
            if (dataSourceId == null) {
                return List.of();
            }
            String targetDataSourceId = dataSourceId;
            return sqlLineageParser
                .parse(sql)
                .writes()
                .stream()
                .map(table -> new TableTarget(targetDataSourceId, table.schema(), table.table()))
                .toList();
        }
        return List.of();
    }

    private String duckDbDataSource(LineageContext context) {
        return context.current.getDataSourceId() != null && isDuckDb(context.current) ? context.current.getDataSourceId() : null;
    }

    private boolean modelMatchesTarget(DataModel model, TableTarget target) {
        return (
            target.dataSourceId() != null &&
            target.dataSourceId().equals(model.getDataSourceId()) &&
            TableRef.of(null, target.schema(), target.table()).matches(model.getSchemaName(), model.getTableName())
        );
    }

    private boolean sameTarget(TableTarget left, TableTarget right) {
        return (
            left != null &&
            right != null &&
            Objects.equals(left.dataSourceId(), right.dataSourceId()) &&
            TableRef.same(left.schema(), right.schema()) &&
            TableRef.same(left.table(), right.table())
        );
    }

    private ModelLineageDTO build(String modelId, boolean includeDownstream) {
        DataModel current = dataModelRepository
            .findById(modelId)
            .orElseThrow(() -> new IllegalArgumentException("数据模型不存在：" + modelId));
        LineageContext context = new LineageContext(current);
        context.currentModelNode = addNode(context, modelNode(current, 0));
        if (hasPhysicalTable(current)) {
            context.currentTableNode = addNode(context, currentTableNode(current, -1));
            addEdge(context, context.currentTableNode.id(), context.currentModelNode.id(), "TABLE", "物理表", CONFIDENCE_CONFIG);
        }

        addSyncLineage(context);
        addEtlLineage(context, includeDownstream);
        Set<String> factModelIds = addModelRelations(context);
        if (includeDownstream) {
            addMetrics(context, factModelIds);
            addDashboards(context);
        }

        List<LineageNodeDTO> nodes = new ArrayList<>(context.nodes.values());
        List<LineageEdgeDTO> edges = new ArrayList<>(context.edges.values());
        if (!includeDownstream) {
            nodes.removeIf(node -> node.layer() > 0);
            Set<String> nodeIds = nodes.stream().map(LineageNodeDTO::id).collect(Collectors.toSet());
            edges.removeIf(edge -> !nodeIds.contains(edge.source()) || !nodeIds.contains(edge.target()));
        }
        nodes.sort(Comparator.comparingInt(LineageNodeDTO::layer).thenComparing(LineageNodeDTO::name, Comparator.nullsLast(String::compareTo)));
        return new ModelLineageDTO(current.getId(), nodes, edges);
    }

    private void addSyncLineage(LineageContext context) {
        DataModel current = context.current;
        if (!hasPhysicalTable(current) || current.getDataSourceId() == null || context.currentTableNode == null) {
            return;
        }
        addSyncLineageForTable(
            context,
            current.getDataSourceId(),
            current.getSchemaName(),
            current.getTableName(),
            context.currentTableNode
        );
    }

    private void addSyncLineageForTable(
        LineageContext context,
        String dataSourceId,
        String schema,
        String table,
        LineageNodeDTO tableNode
    ) {
        if (dataSourceId == null || table == null) {
            return;
        }
        List<DataSyncTableConfig> configs = dataSyncTableConfigRepository.findByDesTableNameIgnoreCase(table);
        for (DataSyncTableConfig config : configs) {
            if (!TableRef.of(null, config.getDesSchemaName(), config.getDesTableName()).matches(schema, table)) {
                continue;
            }
            String syncId = config.getSyncTask();
            if (syncId == null || syncId.isBlank()) {
                continue;
            }
            DataSync sync = dataSyncRepository.findById(syncId).orElse(null);
            String targetDataSourceId = firstNonBlank(config.getDesDatasource(), sync == null ? null : sync.getTarget());
            if (targetDataSourceId != null && !targetDataSourceId.isBlank() && !targetDataSourceId.equals(dataSourceId)) {
                continue;
            }
            String sourceDataSourceId = firstNonBlank(config.getSrcDatasource(), sync == null ? null : sync.getSource());
            String syncName = sync == null ? "数据同步 " + syncId : firstNonBlank(sync.getJobName(), sync.getJobCode(), syncId);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("jobCode", sync == null ? null : sync.getJobCode());
            meta.put("status", sync == null ? null : sync.getStatus());
            meta.put("sourceDataSourceId", sourceDataSourceId);
            meta.put("sourceTable", joinTable(config.getSrcSchemaName(), config.getSrcTableName()));
            LineageNodeDTO syncNode = addNode(
                context,
                new LineageNodeDTO(
                    syncNodeId(syncId),
                    TYPE_SYNC,
                    syncName,
                    sync == null ? null : sync.getType(),
                    tableNode.layer() - 1,
                    meta
                )
            );
            addEdge(context, syncNode.id(), tableNode.id(), "SYNC", "写入目标表", CONFIDENCE_CONFIG);

            if (config.getSrcTableName() != null && !config.getSrcTableName().isBlank()) {
                LineageNodeDTO sourceTable = addNode(
                    context,
                    tableNode(
                        sourceDataSourceId,
                        config.getSrcSchemaName(),
                        config.getSrcTableName(),
                        tableNode.layer() - 2,
                        null
                    )
                );
                addEdge(context, sourceTable.id(), syncNode.id(), "SYNC", "来源表", CONFIDENCE_CONFIG);
            }
        }
    }

    private void addEtlLineage(LineageContext context, boolean includeReads) {
        DataModel current = context.current;
        if (!hasPhysicalTable(current)) {
            return;
        }
        Set<String> visitedUpstreamTables = new HashSet<>();
        Set<String> visitedUpstreamTasks = new HashSet<>();
        if (context.currentTableNode != null) {
            visitedUpstreamTables.add(context.currentTableNode.id());
        }
        for (ETLNode node : etlNodeRepository.findAll()) {
            JsonNode config = readJson(node.getConfig());
            if (config == null) {
                continue;
            }
            EtlMatch match = matchEtlNode(node, config, current, context);
            if ((!match.writes() && !match.reads()) || (!includeReads && !match.writes())) {
                continue;
            }
            String taskId = node.getTaskId();
            if (taskId == null || taskId.isBlank()) {
                continue;
            }
            ETLTask task = context.taskCache.get(taskId);
            if (task == null && !context.taskCache.containsKey(taskId)) {
                task = etlTaskRepository.findById(taskId).orElse(null);
                context.taskCache.put(taskId, task);
            }
            String taskName = task == null ? "ETL 任务 " + taskId : firstNonBlank(task.getTaskName(), task.getTaskCode(), taskId);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("taskCode", task == null ? null : task.getTaskCode());
            meta.put("status", task == null ? null : task.getStatus());
            meta.put("nodeLabel", node.getLabel());
            meta.put("nodeType", node.getType());
            int layer = match.writes() ? -2 : 1;
            LineageNodeDTO taskNode = addNode(context, new LineageNodeDTO(etlNodeId(taskId), TYPE_ETL, taskName, null, layer, meta));
            String nodeLabel = firstNonBlank(node.getLabel(), node.getType(), taskId);
            if (match.writes()) {
                String label = "节点「" + nodeLabel + "」写入";
                addEdge(context, taskNode.id(), context.currentTableNode.id(), "WRITE", label, match.confidence());
                expandEtlTaskUpstream(
                    context,
                    taskId,
                    taskNode,
                    new TableTarget(current.getDataSourceId(), current.getSchemaName(), current.getTableName()),
                    visitedUpstreamTables,
                    visitedUpstreamTasks
                );
            }
            if (includeReads && match.reads()) {
                String label = "节点「" + nodeLabel + "」读取";
                addEdge(context, context.currentTableNode.id(), taskNode.id(), "READ", label, match.confidence());
                taskNode = withLayer(taskNode, Math.max(taskNode.layer(), 1));
                context.nodes.put(taskNode.id(), taskNode);
            }
        }
    }

    /**
     * 反向展开 ETL 任务的输入：任务内写入目标表的节点，其上游读取的物理表。
     */
    private void expandEtlTaskUpstream(
        LineageContext context,
        String taskId,
        LineageNodeDTO taskNode,
        TableTarget target,
        Set<String> visitedTables,
        Set<String> visitedTasks
    ) {
        if (!visitedTasks.add(taskId)) {
            return;
        }
        List<ETLNode> taskNodes = etlNodeRepository.findAllByTaskId(taskId).orElse(List.of());
        List<ETLEdge> taskEdges = etlEdgeRepository.findAllByTaskId(taskId).orElse(List.of());
        Map<String, ETLNode> nodesByCode = taskNodes
            .stream()
            .filter(node -> node.getCode() != null)
            .collect(Collectors.toMap(ETLNode::getCode, node -> node, (left, right) -> left, LinkedHashMap::new));
        Set<String> startCodes = taskNodes
            .stream()
            .filter(node -> node.getCode() != null)
            .filter(node -> {
                JsonNode config = readJson(node.getConfig());
                return config != null && writeTargets(node, config, context).stream().anyMatch(write -> sameTarget(write, target));
            })
            .map(ETLNode::getCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (startCodes.isEmpty()) {
            return;
        }
        Set<String> reachable = backwardReachable(startCodes, taskEdges);
        for (String code : reachable) {
            ETLNode node = nodesByCode.get(code);
            if (node == null) {
                continue;
            }
            JsonNode config = readJson(node.getConfig());
            if (config == null) {
                continue;
            }
            for (TableTarget source : readTargets(node, config, context)) {
                if (sameTarget(source, target)) {
                    continue;
                }
                LineageNodeDTO sourceTable = addNode(
                    context,
                    tableNode(source.dataSourceId(), source.schema(), source.table(), taskNode.layer() - 1, null)
                );
                addEdge(
                    context,
                    sourceTable.id(),
                    taskNode.id(),
                    "READ",
                    "节点「" + firstNonBlank(node.getLabel(), node.getType(), code) + "」读取",
                    CONFIDENCE_CONFIG
                );
                expandTableUpstream(context, source, sourceTable, visitedTables, visitedTasks);
            }
        }
    }

    private Set<String> backwardReachable(Set<String> starts, List<ETLEdge> edges) {
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        for (ETLEdge edge : edges) {
            if (edge.getSource() == null || edge.getTarget() == null) {
                continue;
            }
            adjacency.computeIfAbsent(edge.getTarget(), key -> new ArrayList<>()).add(edge.getSource());
        }
        Set<String> visited = new LinkedHashSet<>(starts);
        Deque<String> queue = new ArrayDeque<>(starts);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String previous : adjacency.getOrDefault(current, List.of())) {
                if (visited.add(previous)) {
                    queue.add(previous);
                }
            }
        }
        return visited;
    }

    /**
     * 继续向上展开物理表：数据同步，以及其他写入该表的 ETL 任务。
     */
    private void expandTableUpstream(
        LineageContext context,
        TableTarget target,
        LineageNodeDTO tableNode,
        Set<String> visitedTables,
        Set<String> visitedTasks
    ) {
        if (!visitedTables.add(tableNode.id())) {
            return;
        }
        addSyncLineageForTable(context, target.dataSourceId(), target.schema(), target.table(), tableNode);
        for (ETLNode node : etlNodeRepository.findAll()) {
            JsonNode config = readJson(node.getConfig());
            if (config == null || node.getTaskId() == null || node.getTaskId().isBlank()) {
                continue;
            }
            if (writeTargets(node, config, context).stream().noneMatch(write -> sameTarget(write, target))) {
                continue;
            }
            LineageNodeDTO taskNode = ensureEtlTaskNode(context, node.getTaskId(), tableNode.layer() - 1);
            addEdge(context, taskNode.id(), tableNode.id(), "WRITE", "写入物理表", CONFIDENCE_CONFIG);
            expandEtlTaskUpstream(context, node.getTaskId(), taskNode, target, visitedTables, visitedTasks);
        }
    }

    private LineageNodeDTO ensureEtlTaskNode(LineageContext context, String taskId, int layer) {
        ETLTask task = context.taskCache.get(taskId);
        if (task == null && !context.taskCache.containsKey(taskId)) {
            task = etlTaskRepository.findById(taskId).orElse(null);
            context.taskCache.put(taskId, task);
        }
        String taskName = task == null ? "ETL 任务 " + taskId : firstNonBlank(task.getTaskName(), task.getTaskCode(), taskId);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("taskCode", task == null ? null : task.getTaskCode());
        meta.put("status", task == null ? null : task.getStatus());
        return addNode(context, new LineageNodeDTO(etlNodeId(taskId), TYPE_ETL, taskName, null, layer, meta));
    }

    private Set<String> addModelRelations(LineageContext context) {
        Set<String> factModelIds = new LinkedHashSet<>();
        DataModel current = context.current;
        List<ModelField> currentFields = modelFieldRepository.findByModelIdOrderBySortOrderAsc(current.getId());
        if (isDimension(current)) {
            Map<String, List<ModelField>> byFact = modelFieldRepository
                .findByDimensionModelId(current.getId())
                .stream()
                .filter(field -> field.getModelId() != null)
                .collect(Collectors.groupingBy(ModelField::getModelId, LinkedHashMap::new, Collectors.toList()));
            for (Map.Entry<String, List<ModelField>> entry : byFact.entrySet()) {
                DataModel fact = context.model(entry.getKey());
                if (fact == null) {
                    continue;
                }
                factModelIds.add(fact.getId());
                LineageNodeDTO factNode = addNode(context, modelNode(fact, 1));
                String label = relationLabel(entry.getValue(), currentFields, true);
                addEdge(context, context.currentModelNode.id(), factNode.id(), "DIMENSION", label, CONFIDENCE_CONFIG);
            }
        } else {
            factModelIds.add(current.getId());
            Map<String, List<ModelField>> byDimension = currentFields
                .stream()
                .filter(field -> field.getDimensionModelId() != null && !field.getDimensionModelId().isBlank())
                .collect(Collectors.groupingBy(ModelField::getDimensionModelId, LinkedHashMap::new, Collectors.toList()));
            for (Map.Entry<String, List<ModelField>> entry : byDimension.entrySet()) {
                DataModel dimension = context.model(entry.getKey());
                if (dimension == null) {
                    continue;
                }
                LineageNodeDTO dimensionNode = addNode(context, modelNode(dimension, -1));
                List<ModelField> dimensionFields = modelFieldRepository.findByModelIdOrderBySortOrderAsc(dimension.getId());
                String label = relationLabel(entry.getValue(), dimensionFields, false);
                addEdge(context, dimensionNode.id(), context.currentModelNode.id(), "DIMENSION", label, CONFIDENCE_CONFIG);
            }
        }
        return factModelIds;
    }

    private void addMetrics(LineageContext context, Set<String> factModelIds) {
        for (String factModelId : factModelIds) {
            DataModel fact = context.model(factModelId);
            if (fact == null) {
                continue;
            }
            int layer = fact.getId().equals(context.current.getId()) ? 1 : 2;
            for (Metric metric : metricRepository.findByFactModelId(factModelId)) {
                addMetricNode(context, metric, layer, fact);
            }
        }
        addDerivedMetrics(context);
    }

    private void addDerivedMetrics(LineageContext context) {
        List<Metric> derived = metricRepository.findAll().stream().filter(metric -> Metric.TYPE_DERIVED.equals(metric.getMetricType())).toList();
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 20) {
            changed = false;
            for (Metric metric : derived) {
                if (context.metricsById.containsKey(metric.getId())) {
                    continue;
                }
                Set<String> references = formulaRefs(metric.getFormula());
                List<Metric> parents = references
                    .stream()
                    .map(context.metricsByCode::get)
                    .filter(parent -> parent != null)
                    .toList();
                if (parents.isEmpty()) {
                    continue;
                }
                int layer = parents.stream().mapToInt(parent -> context.metricNodeLayer.getOrDefault(parent.getId(), 1)).max().orElse(1) + 1;
                LineageNodeDTO node = addMetricNode(context, metric, layer, null);
                for (Metric parent : parents) {
                    addEdge(context, metricNodeId(parent.getId()), node.id(), "DERIVE", "引用指标", CONFIDENCE_PARSED);
                }
                changed = true;
            }
        }
    }

    private LineageNodeDTO addMetricNode(LineageContext context, Metric metric, int layer, DataModel fact) {
        if (context.metricsById.containsKey(metric.getId())) {
            return context.nodes.get(metricNodeId(metric.getId()));
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("code", metric.getCode());
        meta.put("metricType", metric.getMetricType());
        meta.put("status", metric.getStatus());
        meta.put("unit", metric.getUnit());
        meta.put("factModelId", metric.getFactModelId());
        LineageNodeDTO node = addNode(
            context,
            new LineageNodeDTO(metricNodeId(metric.getId()), TYPE_METRIC, metric.getName(), metric.getMetricType(), layer, meta)
        );
        context.metricsById.put(metric.getId(), metric);
        if (metric.getCode() != null) {
            context.metricsByCode.put(metric.getCode(), metric);
        }
        context.metricNodeLayer.put(metric.getId(), layer);
        DataModel sourceFact = fact != null ? fact : context.model(metric.getFactModelId());
        if (sourceFact != null) {
            addEdge(context, modelNodeId(sourceFact.getId()), node.id(), "METRIC", "计算指标", CONFIDENCE_CONFIG);
        }
        return node;
    }

    private void addDashboards(LineageContext context) {
        for (AnalysisDashboard dashboard : analysisDashboardRepository.findAll()) {
            JsonNode spec = readResolvedDashboardSpec(dashboard);
            if (spec == null) {
                continue;
            }
            JsonNode datasets = spec.path("datasets");
            if (!datasets.isArray()) {
                continue;
            }
            int index = 0;
            for (JsonNode dataset : datasets) {
                String type = datasetType(dataset);
                if ("METRIC".equalsIgnoreCase(type) || dataset.has("metricQuery")) {
                    JsonNode metricCodes = dataset.path("metricQuery").path("metricCodes");
                    if (metricCodes.isArray()) {
                        List<Metric> matchedMetrics = new ArrayList<>();
                        int layer = 0;
                        for (JsonNode codeNode : metricCodes) {
                            Metric metric = context.metricsByCode.get(codeNode.asText());
                            if (metric == null) {
                                continue;
                            }
                            matchedMetrics.add(metric);
                            layer = Math.max(layer, context.metricNodeLayer.getOrDefault(metric.getId(), 1) + 1);
                        }
                        if (!matchedMetrics.isEmpty()) {
                            LineageNodeDTO metricDatasetNode = datasetNode(context, dashboard, dataset, index, layer);
                            for (Metric metric : matchedMetrics) {
                                addEdge(
                                    context,
                                    metricNodeId(metric.getId()),
                                    metricDatasetNode.id(),
                                    "DATASET",
                                    "指标数据集",
                                    CONFIDENCE_CONFIG
                                );
                            }
                            LineageNodeDTO dashboardNode = dashboardNode(context, dashboard, metricDatasetNode.layer() + 1);
                            addEdge(
                                context,
                                metricDatasetNode.id(),
                                dashboardNode.id(),
                                "DASHBOARD",
                                "看板引用数据集",
                                CONFIDENCE_CONFIG
                            );
                        }
                    }
                }
                String sql = text(dataset, "sql");
                if (sql != null && !sql.isBlank() && matchesSqlRead(dataset, dashboard, sql, context.current)) {
                    String anchor = context.currentTableNode != null ? context.currentTableNode.id() : context.currentModelNode.id();
                    int sourceLayer = context.currentTableNode != null ? context.currentTableNode.layer() : context.currentModelNode.layer();
                    LineageNodeDTO sqlDatasetNode = datasetNode(context, dashboard, dataset, index, sourceLayer + 1);
                    addEdge(context, anchor, sqlDatasetNode.id(), "READ", "SQL 数据集读取", CONFIDENCE_PARSED);
                    LineageNodeDTO dashboardNode = dashboardNode(context, dashboard, sqlDatasetNode.layer() + 1);
                    addEdge(
                        context,
                        sqlDatasetNode.id(),
                        dashboardNode.id(),
                        "DASHBOARD",
                        "看板引用数据集",
                        CONFIDENCE_PARSED
                    );
                }
                index++;
            }
        }
    }

    private LineageNodeDTO datasetNode(
        LineageContext context,
        AnalysisDashboard dashboard,
        JsonNode dataset,
        int index,
        int layer
    ) {
        String nodeId = datasetNodeId(dashboard, dataset, index);
        LineageNodeDTO existing = context.nodes.get(nodeId);
        if (existing != null) {
            if (existing.layer() < layer) {
                existing = withLayer(existing, layer);
                context.nodes.put(nodeId, existing);
            }
            return existing;
        }
        String datasetType = datasetType(dataset);
        String dataSourceId = text(dataset, "dataSourceId");
        if (dataSourceId == null) {
            dataSourceId = dashboard.getDataSourceId();
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("refId", text(dataset, "refId"));
        meta.put("localId", text(dataset, "id"));
        meta.put("datasetType", datasetType);
        meta.put("dataSourceId", dataSourceId);
        meta.put("dashboardId", dashboard.getId());
        return addNode(
            context,
            new LineageNodeDTO(nodeId, TYPE_DATASET, datasetName(dataset), datasetType, layer, meta)
        );
    }

    private String datasetType(JsonNode dataset) {
        String type = text(dataset, "type");
        if (type != null) {
            return type;
        }
        if (dataset.has("metricQuery")) {
            return "METRIC";
        }
        if (dataset.has("sql")) {
            return "SQL";
        }
        return null;
    }

    private String datasetNodeId(AnalysisDashboard dashboard, JsonNode dataset, int index) {
        String refId = text(dataset, "refId");
        if (refId != null) {
            return "dataset:" + refId;
        }
        String localId = text(dataset, "id");
        if (localId != null) {
            return "dataset:" + dashboard.getId() + ":" + localId;
        }
        return "dataset:" + dashboard.getId() + ":#" + index;
    }

    private LineageNodeDTO dashboardNode(LineageContext context, AnalysisDashboard dashboard, int layer) {
        String nodeId = dashboardNodeId(dashboard.getId());
        LineageNodeDTO existing = context.nodes.get(nodeId);
        if (existing != null) {
            if (existing.layer() < layer) {
                existing = withLayer(existing, layer);
                context.nodes.put(nodeId, existing);
            }
            return existing;
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("code", dashboard.getCode());
        meta.put("status", dashboard.getStatus());
        meta.put("dataSourceId", dashboard.getDataSourceId());
        return addNode(
            context,
            new LineageNodeDTO(nodeId, TYPE_DASHBOARD, dashboard.getName(), dashboard.getStatus(), layer, meta)
        );
    }

    private boolean matchesSqlRead(JsonNode dataset, AnalysisDashboard dashboard, String sql, DataModel model) {
        if (!hasPhysicalTable(model)) {
            return false;
        }
        String dataSourceId = text(dataset, "dataSourceId");
        if (dataSourceId == null || dataSourceId.isBlank()) {
            dataSourceId = dashboard.getDataSourceId();
        }
        if (
            dataSourceId != null &&
            !dataSourceId.isBlank() &&
            model.getDataSourceId() != null &&
            !dataSourceId.equals(model.getDataSourceId())
        ) {
            return false;
        }
        SqlReferences references = sqlLineageParser.parse(sql);
        return references.reads().stream().anyMatch(table -> table.matches(model.getSchemaName(), model.getTableName()));
    }

    private EtlMatch matchEtlNode(ETLNode node, JsonNode config, DataModel current, LineageContext context) {
        String type = normalizeType(node.getType());
        if (type.isEmpty()) {
            return EtlMatch.NONE;
        }
        if ("MODELWRITE".equals(type)) {
            String modelId = text(config, "modelId");
            DataModel target = modelId == null ? null : context.model(modelId);
            if (target != null && samePhysicalTable(target, current)) {
                return new EtlMatch(true, false, CONFIDENCE_CONFIG);
            }
            return EtlMatch.NONE;
        }
        if (DIRECT_OUTPUT_TYPES.contains(type)) {
            if (directTargetMatches(type, config, current)) {
                return new EtlMatch(true, false, CONFIDENCE_CONFIG);
            }
            return EtlMatch.NONE;
        }
        if (DIRECT_INPUT_TYPES.contains(type)) {
            return directInputMatches(config, current) ? new EtlMatch(false, true, CONFIDENCE_CONFIG) : EtlMatch.NONE;
        }
        if (type.contains("SQL")) {
            String sql = extractSql(config);
            if (sql == null || sql.isBlank() || !sqlDataSourceMatches(type, config, current)) {
                return EtlMatch.NONE;
            }
            SqlReferences references = sqlLineageParser.parse(sql);
            boolean writes = references.writes().stream().anyMatch(table -> table.matches(current.getSchemaName(), current.getTableName()));
            boolean reads = references.reads().stream().anyMatch(table -> table.matches(current.getSchemaName(), current.getTableName()));
            String confidence = references.inferred() ? CONFIDENCE_INFERRED : CONFIDENCE_PARSED;
            return new EtlMatch(writes, reads, confidence);
        }
        return EtlMatch.NONE;
    }

    private boolean directTargetMatches(String type, JsonNode config, DataModel current) {
        String sourceId = firstText(config, "sourceId", "dataSourceId");
        String schema = text(config, "schema");
        String table = text(config, "table");
        if (table == null || table.isBlank()) {
            return false;
        }
        boolean sourceMatches = sourceId != null && !sourceId.isBlank() && sourceId.equals(current.getDataSourceId());
        if (!sourceMatches && "DUCKDBWRITE".equals(type)) {
            sourceMatches = current.getDataSourceId() != null && isDuckDb(current);
        }
        return sourceMatches && TableRef.of(null, schema, table).matches(current.getSchemaName(), current.getTableName());
    }

    private boolean directInputMatches(JsonNode config, DataModel current) {
        String sourceId = firstText(config, "sourceId", "dataSourceId");
        if (sourceId == null || sourceId.isBlank() || !sourceId.equals(current.getDataSourceId())) {
            return false;
        }
        String schema = text(config, "schema");
        String table = text(config, "table");
        if (table == null || table.isBlank()) {
            return false;
        }
        for (String item : table.split(",")) {
            if (TableRef.of(null, schema, item.trim()).matches(current.getSchemaName(), current.getTableName())) {
                return true;
            }
        }
        return false;
    }

    private boolean sqlDataSourceMatches(String type, JsonNode config, DataModel current) {
        String sourceId = firstText(config, "sourceId", "dataSourceId");
        if (sourceId != null && !sourceId.isBlank()) {
            return sourceId.equals(current.getDataSourceId());
        }
        return current.getDataSourceId() != null && isDuckDb(current) && type.contains("DUCKDB");
    }

    private boolean targetMatches(String schema, String table, DataModel model) {
        return table != null && TableRef.of(null, schema, table).matches(model.getSchemaName(), model.getTableName());
    }

    private boolean samePhysicalTable(DataModel left, DataModel right) {
        return left != null &&
        right != null &&
        hasPhysicalTable(left) &&
        hasPhysicalTable(right) &&
        TableRef.same(left.getTableName(), right.getTableName()) &&
        (left.getSchemaName() == null ||
            left.getSchemaName().isBlank() ||
            right.getSchemaName() == null ||
            right.getSchemaName().isBlank() ||
            TableRef.same(left.getSchemaName(), right.getSchemaName()));
    }

    private String relationLabel(List<ModelField> fields, List<ModelField> targetFields, boolean dimensionIsCurrent) {
        Map<String, String> targetFieldNames = targetFields
            .stream()
            .filter(field -> field.getId() != null)
            .collect(Collectors.toMap(ModelField::getId, ModelField::getFieldName, (left, right) -> left));
        List<String> parts = new ArrayList<>();
        for (ModelField field : fields) {
            String target = targetFieldNames.get(field.getDimensionFieldId());
            if (dimensionIsCurrent) {
                parts.add(field.getFieldName() + " → " + firstNonBlank(target, field.getFieldName()));
            } else {
                parts.add(field.getFieldName() + " → " + firstNonBlank(target, field.getDimensionFieldId()));
            }
            if (parts.size() >= 3) {
                break;
            }
        }
        return parts.isEmpty() ? "关联维度" : String.join(", ", parts);
    }

    private LineageNodeDTO modelNode(DataModel model, int layer) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("code", model.getCode());
        meta.put("modelType", model.getModelType());
        meta.put("dimensionKind", model.getDimensionKind());
        meta.put("dataSourceId", model.getDataSourceId());
        meta.put("table", joinTable(model.getSchemaName(), model.getTableName()));
        String subType = model.getModelType();
        if (isDimension(model) && model.getDimensionKind() != null) {
            subType = model.getModelType() + "/" + model.getDimensionKind();
        }
        return new LineageNodeDTO(modelNodeId(model.getId()), TYPE_MODEL, model.getName(), subType, layer, meta);
    }

    private LineageNodeDTO currentTableNode(DataModel model, int layer) {
        return tableNode(model.getDataSourceId(), model.getSchemaName(), model.getTableName(), layer, null);
    }

    private LineageNodeDTO tableNode(String dataSourceId, String schema, String table, int layer, String name) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("dataSourceId", dataSourceId);
        meta.put(
            "dataSourceName",
            dataSourceId == null ? null : dataSourceRepository.findById(dataSourceId).map(DataSource::getName).orElse(null)
        );
        meta.put("schema", schema);
        meta.put("table", table);
        return new LineageNodeDTO(
            tableNodeId(dataSourceId, schema, table),
            TYPE_TABLE,
            name == null ? joinTable(schema, table) : name,
            null,
            layer,
            meta
        );
    }

    private LineageNodeDTO addNode(LineageContext context, LineageNodeDTO node) {
        LineageNodeDTO existing = context.nodes.get(node.id());
        if (existing != null) {
            return existing;
        }
        context.nodes.put(node.id(), node);
        return node;
    }

    private void addEdge(LineageContext context, String source, String target, String kind, String label, String confidence) {
        if (source == null || target == null || source.equals(target)) {
            return;
        }
        String id = source + "->" + target + ":" + kind + ":" + label;
        context.edges.putIfAbsent(id, new LineageEdgeDTO(id, source, target, kind, label, confidence));
    }

    private LineageNodeDTO withLayer(LineageNodeDTO node, int layer) {
        return new LineageNodeDTO(node.id(), node.type(), node.name(), node.subType(), layer, node.meta());
    }

    private LineageNodeDTO shiftLayer(LineageNodeDTO node, int offset) {
        return new LineageNodeDTO(node.id(), node.type(), node.name(), node.subType(), node.layer() + offset, node.meta());
    }

    private Set<String> formulaRefs(String formula) {
        Set<String> codes = new LinkedHashSet<>();
        if (formula == null) {
            return codes;
        }
        Matcher matcher = METRIC_FORMULA_REF.matcher(formula);
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        return codes;
    }

    /**
     * 解析看板 spec，并把引用的数据集解析为最新定义，保证血缘不受数据集引用影响。
     */
    private JsonNode readResolvedDashboardSpec(AnalysisDashboard dashboard) {
        JsonNode spec = readJson(dashboard.getSpec());
        if (spec == null) {
            return null;
        }
        try {
            String resolved = analysisDatasetResolver.resolveSpecJson(dashboard.getSpec());
            return readJson(resolved);
        } catch (RuntimeException e) {
            LOG.debug("解析看板 {} 引用数据集失败: {}", dashboard.getId(), e.getMessage());
            return spec;
        }
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception e) {
            LOG.debug("解析 JSON 失败: {}", e.getMessage());
            return null;
        }
    }

    private String extractSql(JsonNode config) {
        JsonNode sql = config.get("sql");
        if (sql == null) {
            sql = config.get("query");
        }
        if (sql == null) {
            return null;
        }
        if (sql.isTextual()) {
            return sql.asText();
        }
        if (sql.isObject()) {
            return text(sql, "query");
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String datasetName(JsonNode dataset) {
        return firstNonBlank(text(dataset, "name"), text(dataset, "id"), "数据集");
    }

    private String normalizeType(String type) {
        return type == null ? "" : type.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private boolean isDimension(DataModel model) {
        return "DIMENSION".equalsIgnoreCase(model.getModelType());
    }

    private boolean isDuckDb(DataModel model) {
        if (model.getDataSourceId() == null) {
            return false;
        }
        DataSource dataSource = dataSourceRepository.findById(model.getDataSourceId()).orElse(null);
        return dataSource != null && dataSource.getUrl() != null && dataSource.getUrl().toLowerCase(Locale.ROOT).contains("duckdb");
    }

    private boolean hasPhysicalTable(DataModel model) {
        return model.getTableName() != null && !model.getTableName().isBlank();
    }

    private String joinTable(String schema, String table) {
        if (schema == null || schema.isBlank()) {
            return table == null ? "" : table;
        }
        return table == null || table.isBlank() ? schema : schema + "." + table;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private String modelNodeId(String id) {
        return "model:" + id;
    }

    private String tableNodeId(String dataSourceId, String schema, String table) {
        return "table:" + (dataSourceId == null ? "" : dataSourceId) + ":" + joinTable(schema, table).toLowerCase(Locale.ROOT);
    }

    private String syncNodeId(String id) {
        return "sync:" + id;
    }

    private String etlNodeId(String id) {
        return "etl:" + id;
    }

    private String metricNodeId(String id) {
        return "metric:" + id;
    }

    private String dashboardNodeId(String id) {
        return "dashboard:" + id;
    }

    private record TableTarget(String dataSourceId, String schema, String table) {}

    private record EtlMatch(boolean writes, boolean reads, String confidence) {
        private static final EtlMatch NONE = new EtlMatch(false, false, CONFIDENCE_CONFIG);
    }

    private class LineageContext {

        private final DataModel current;
        private final Map<String, LineageNodeDTO> nodes = new LinkedHashMap<>();
        private final Map<String, LineageEdgeDTO> edges = new LinkedHashMap<>();
        private final Map<String, DataModel> modelsById;
        private final Map<String, ETLTask> taskCache = new LinkedHashMap<>();
        private final Map<String, Metric> metricsById = new LinkedHashMap<>();
        private final Map<String, Metric> metricsByCode = new LinkedHashMap<>();
        private final Map<String, Integer> metricNodeLayer = new LinkedHashMap<>();
        private LineageNodeDTO currentModelNode;
        private LineageNodeDTO currentTableNode;

        private LineageContext(DataModel current) {
            this.current = current;
            this.modelsById = dataModelRepository.findAll().stream().collect(Collectors.toMap(DataModel::getId, model -> model, (left, right) -> left));
        }

        private DataModel model(String id) {
            return id == null ? null : modelsById.get(id);
        }
    }
}
