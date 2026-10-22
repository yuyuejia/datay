package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.AnalysisDashboard;
import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.DataSource;
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
import com.data.datafusion.service.dto.ModelLineageDTO;
import com.data.datafusion.service.lineage.SqlLineageParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataModelLineageServiceTest {

    private DataModelRepository dataModelRepository;
    private ModelFieldRepository modelFieldRepository;
    private MetricRepository metricRepository;
    private DataSyncTableConfigRepository dataSyncTableConfigRepository;
    private DataSyncRepository dataSyncRepository;
    private ETLNodeRepository etlNodeRepository;
    private ETLEdgeRepository etlEdgeRepository;
    private ETLTaskRepository etlTaskRepository;
    private AnalysisDashboardRepository analysisDashboardRepository;
    private DataSourceRepository dataSourceRepository;
    private ObjectMapper objectMapper;
    private DataModelLineageService service;

    @BeforeEach
    void setUp() {
        dataModelRepository = mock(DataModelRepository.class);
        modelFieldRepository = mock(ModelFieldRepository.class);
        metricRepository = mock(MetricRepository.class);
        dataSyncTableConfigRepository = mock(DataSyncTableConfigRepository.class);
        dataSyncRepository = mock(DataSyncRepository.class);
        etlNodeRepository = mock(ETLNodeRepository.class);
        etlEdgeRepository = mock(ETLEdgeRepository.class);
        etlTaskRepository = mock(ETLTaskRepository.class);
        analysisDashboardRepository = mock(AnalysisDashboardRepository.class);
        dataSourceRepository = mock(DataSourceRepository.class);
        objectMapper = new ObjectMapper();
        AnalysisDatasetResolver datasetResolver = mock(AnalysisDatasetResolver.class);
        when(datasetResolver.resolveSpecJson(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new DataModelLineageService(
            dataModelRepository,
            modelFieldRepository,
            metricRepository,
            dataSyncTableConfigRepository,
            dataSyncRepository,
            etlNodeRepository,
            etlEdgeRepository,
            etlTaskRepository,
            analysisDashboardRepository,
            dataSourceRepository,
            new SqlLineageParser(),
            objectMapper,
            datasetResolver
        );
    }

    @Test
    void shouldBuildFactDimensionMetricAndDashboardLineage() throws Exception {
        DataModel fact = model("f1", "fact_sales", "DWD", "main", "fact_sales");
        DataModel dimension = model("d1", "dim_store", "DIMENSION", "main", "dim_store");
        ModelField factField = field("ff1", "f1", "store_key", "d1", "df1");
        ModelField dimensionField = field("df1", "d1", "store_id", null, null);
        Metric amount = metric("m1", "sales_amount", "ATOMIC", "f1", null);
        Metric profit = metric("m2", "profit_rate", "DERIVED", null, "${sales_amount} / 100");
        AnalysisDashboard dashboard = dashboard();

        when(dataModelRepository.findById("f1")).thenReturn(Optional.of(fact));
        when(dataModelRepository.findAll()).thenReturn(List.of(fact, dimension));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("f1")).thenReturn(List.of(factField));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("d1")).thenReturn(List.of(dimensionField));
        when(metricRepository.findByFactModelId("f1")).thenReturn(List.of(amount));
        when(metricRepository.findAll()).thenReturn(List.of(amount, profit));
        when(analysisDashboardRepository.findAll()).thenReturn(List.of(dashboard));
        when(dataSyncTableConfigRepository.findByDesTableNameIgnoreCase(anyString())).thenReturn(List.of());
        when(etlNodeRepository.findAll()).thenReturn(List.of());

        ModelLineageDTO lineage = service.build("f1");

        assertThat(lineage.nodes()).anyMatch(node -> "dim_store".equals(node.name()));
        assertThat(lineage.nodes()).anyMatch(node -> "sales_amount".equals(node.meta().get("code")));
        assertThat(lineage.nodes()).anyMatch(node -> "profit_rate".equals(node.meta().get("code")));
        assertThat(lineage.nodes()).anyMatch(node -> "销售看板".equals(node.name()));
        assertThat(edgeKinds(lineage)).contains("DIMENSION", "METRIC", "DERIVE", "DASHBOARD", "READ");
    }

    @Test
    void shouldDetectAnyEtlWriterAndSqlWriter() throws Exception {
        DataModel fact = model("f1", "fact_sales", "DWD", "main", "fact_sales");
        ETLNode outputNode = node("n1", "t1", "StreamJdbcOutput", "写入事实表", "{\"sourceId\":\"ds1\",\"schema\":\"main\",\"table\":\"fact_sales\"}");
        ETLNode sqlNode = node(
            "n2",
            "t2",
            "SqlTask",
            "SQL装载",
            "{\"sourceId\":\"ds1\",\"sql\":\"INSERT INTO main.fact_sales SELECT * FROM ods.orders\"}"
        );
        ETLNode readNode = node(
            "n3",
            "t3",
            "SqlTask",
            "读取事实表",
            "{\"sourceId\":\"ds1\",\"sql\":\"SELECT * FROM main.fact_sales\"}"
        );
        ETLTask outputTask = task("t1", "同步事实表");
        ETLTask sqlTask = task("t2", "SQL 装载任务");
        ETLTask readTask = task("t3", "SQL 读取任务");

        when(dataModelRepository.findById("f1")).thenReturn(Optional.of(fact));
        when(dataModelRepository.findAll()).thenReturn(List.of(fact));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("f1")).thenReturn(List.of());
        when(metricRepository.findByFactModelId(anyString())).thenReturn(List.of());
        when(metricRepository.findAll()).thenReturn(List.of());
        when(analysisDashboardRepository.findAll()).thenReturn(List.of());
        when(dataSyncTableConfigRepository.findByDesTableNameIgnoreCase(anyString())).thenReturn(List.of());
        when(etlNodeRepository.findAll()).thenReturn(List.of(outputNode, sqlNode, readNode));
        when(etlTaskRepository.findById("t1")).thenReturn(Optional.of(outputTask));
        when(etlTaskRepository.findById("t2")).thenReturn(Optional.of(sqlTask));
        when(etlTaskRepository.findById("t3")).thenReturn(Optional.of(readTask));

        ModelLineageDTO lineage = service.build("f1");

        assertThat(lineage.nodes()).anyMatch(node -> "etl:t1".equals(node.id()));
        assertThat(lineage.nodes()).anyMatch(node -> "etl:t2".equals(node.id()));
        assertThat(lineage.nodes()).anyMatch(node -> "etl:t3".equals(node.id()));
        assertThat(lineage.edges())
            .filteredOn(edge -> "WRITE".equals(edge.kind()))
            .extracting("source")
            .contains("etl:t1", "etl:t2");
        assertThat(lineage.edges())
            .filteredOn(edge -> "READ".equals(edge.kind()))
            .extracting("target")
            .contains("etl:t3");
    }

    @Test
    void shouldExpandEtlTaskUpstreamSources() {
        DataModel fact = model("f1", "fact_sales", "DWD", "main", "fact_sales");
        ETLNode input = node("n1", "t1", "JdbcInput", "读取订单", "{\"sourceId\":\"ds2\",\"schema\":\"ods\",\"table\":\"orders\"}");
        ETLNode output = node(
            "n2",
            "t1",
            "StreamJdbcOutput",
            "写入事实表",
            "{\"sourceId\":\"ds1\",\"schema\":\"main\",\"table\":\"fact_sales\"}"
        );
        ETLTask task = task("t1", "订单清洗");
        ETLEdge edge = new ETLEdge();
        edge.setTaskId("t1");
        edge.setSource("n1");
        edge.setTarget("n2");

        when(dataModelRepository.findById("f1")).thenReturn(Optional.of(fact));
        when(dataModelRepository.findAll()).thenReturn(List.of(fact));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("f1")).thenReturn(List.of());
        when(metricRepository.findByFactModelId(anyString())).thenReturn(List.of());
        when(metricRepository.findAll()).thenReturn(List.of());
        when(analysisDashboardRepository.findAll()).thenReturn(List.of());
        when(dataSyncTableConfigRepository.findByDesTableNameIgnoreCase(anyString())).thenReturn(List.of());
        when(etlNodeRepository.findAll()).thenReturn(List.of(input, output));
        when(etlNodeRepository.findAllByTaskId("t1")).thenReturn(Optional.of(List.of(input, output)));
        when(etlEdgeRepository.findAllByTaskId("t1")).thenReturn(Optional.of(List.of(edge)));
        when(etlTaskRepository.findById("t1")).thenReturn(Optional.of(task));

        ModelLineageDTO lineage = service.build("f1");

        assertThat(lineage.nodes()).anyMatch(node -> "table:ds2:ods.orders".equals(node.id()));
        assertThat(lineage.edges())
            .anyMatch(edgeView ->
                "table:ds2:ods.orders".equals(edgeView.source()) &&
                "etl:t1".equals(edgeView.target()) &&
                "READ".equals(edgeView.kind())
            );
    }

    @Test
    void shouldExpandEtlTaskOutputTables() {
        ETLNode input = node("n1", "t1", "JdbcInput", "读取事实表", "{\"sourceId\":\"ds1\",\"schema\":\"main\",\"table\":\"fact_sales\"}");
        ETLNode output = node(
            "n2",
            "t1",
            "StreamJdbcOutput",
            "写入汇总表",
            "{\"sourceId\":\"ds1\",\"schema\":\"main\",\"table\":\"dws_sales\"}"
        );
        ETLTask task = task("t1", "销售汇总ETL");
        ETLEdge edge = new ETLEdge();
        edge.setTaskId("t1");
        edge.setSource("n1");
        edge.setTarget("n2");

        when(dataModelRepository.findAll()).thenReturn(List.of());
        when(dataSyncTableConfigRepository.findByDesTableNameIgnoreCase(anyString())).thenReturn(List.of());
        when(etlNodeRepository.findAll()).thenReturn(List.of(input, output));
        when(etlNodeRepository.findAllByTaskId("t1")).thenReturn(Optional.of(List.of(input, output)));
        when(etlEdgeRepository.findAllByTaskId("t1")).thenReturn(Optional.of(List.of(edge)));
        when(etlTaskRepository.findById("t1")).thenReturn(Optional.of(task));

        com.data.datafusion.service.dto.TableLineageDTO lineage = service.buildTable("ds1", "main", "fact_sales");

        assertThat(lineage.nodes()).anyMatch(node -> "etl:t1".equals(node.id()));
        assertThat(lineage.nodes()).anyMatch(node -> "table:ds1:main.dws_sales".equals(node.id()));
        assertThat(lineage.edges())
            .anyMatch(edgeView ->
                "etl:t1".equals(edgeView.source()) &&
                "table:ds1:main.dws_sales".equals(edgeView.target()) &&
                "WRITE".equals(edgeView.kind())
            );
    }

    @Test
    void shouldBuildTableLineageWithBoundModel() {
        DataModel fact = model("f1", "fact_sales", "DWD", "main", "fact_sales");

        when(dataModelRepository.findById("f1")).thenReturn(Optional.of(fact));
        when(dataModelRepository.findAll()).thenReturn(List.of(fact));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("f1")).thenReturn(List.of());
        when(metricRepository.findByFactModelId(anyString())).thenReturn(List.of());
        when(metricRepository.findAll()).thenReturn(List.of());
        when(analysisDashboardRepository.findAll()).thenReturn(List.of());
        when(dataSyncTableConfigRepository.findByDesTableNameIgnoreCase(anyString())).thenReturn(List.of());
        when(etlNodeRepository.findAll()).thenReturn(List.of());
        DataSource dataSource = new DataSource();
        dataSource.setId("ds1");
        dataSource.setName("测试数据源");
        when(dataSourceRepository.findById("ds1")).thenReturn(Optional.of(dataSource));

        com.data.datafusion.service.dto.TableLineageDTO lineage = service.buildTable("ds1", "main", "fact_sales");

        assertThat(lineage.currentTableId()).isEqualTo("table:ds1:main.fact_sales");
        assertThat(lineage.nodes())
            .anyMatch(node -> lineage.currentTableId().equals(node.id()) && "测试数据源".equals(node.meta().get("dataSourceName")));
        assertThat(lineage.nodes()).anyMatch(node -> "model:f1".equals(node.id()) && node.layer() == 1);
        assertThat(lineage.edges())
            .anyMatch(edge -> lineage.currentTableId().equals(edge.source()) && "model:f1".equals(edge.target()));
    }

    @Test
    void shouldBuildOnlyUpstreamLineage() {
        DataModel fact = model("f1", "fact_sales", "DWD", "main", "fact_sales");
        ETLNode readNode = node(
            "n3",
            "t3",
            "SqlTask",
            "读取事实表",
            "{\"sourceId\":\"ds1\",\"sql\":\"SELECT * FROM main.fact_sales\"}"
        );

        when(dataModelRepository.findById("f1")).thenReturn(Optional.of(fact));
        when(dataModelRepository.findAll()).thenReturn(List.of(fact));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("f1")).thenReturn(List.of());
        when(dataSyncTableConfigRepository.findByDesTableNameIgnoreCase(anyString())).thenReturn(List.of());
        when(etlNodeRepository.findAll()).thenReturn(List.of(readNode));

        ModelLineageDTO lineage = service.buildUpstream("f1");

        assertThat(lineage.nodes()).anyMatch(node -> "MODEL".equals(node.type()));
        assertThat(lineage.nodes()).anyMatch(node -> "TABLE".equals(node.type()));
        assertThat(lineage.nodes()).noneMatch(node -> "METRIC".equals(node.type()));
        assertThat(lineage.nodes()).noneMatch(node -> "DASHBOARD".equals(node.type()));
        assertThat(lineage.nodes()).noneMatch(node -> "ETL".equals(node.type()));
    }

    @Test
    void shouldTraceDataSyncSourceTable() {
        DataModel fact = model("f1", "fact_sales", "DWD", "main", "fact_sales");
        com.data.datafusion.domain.DataSyncTableConfig config = new com.data.datafusion.domain.DataSyncTableConfig();
        config.setSyncTask("s1");
        config.setSrcSchemaName("ods");
        config.setSrcTableName("orders");
        config.setDesSchemaName("main");
        config.setDesTableName("fact_sales");
        com.data.datafusion.domain.DataSync sync = new com.data.datafusion.domain.DataSync();
        sync.setId("s1");
        sync.setJobName("订单同步");
        sync.setSource("ds2");
        sync.setTarget("ds1");

        when(dataModelRepository.findById("f1")).thenReturn(Optional.of(fact));
        when(dataModelRepository.findAll()).thenReturn(List.of(fact));
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("f1")).thenReturn(List.of());
        when(metricRepository.findByFactModelId(anyString())).thenReturn(List.of());
        when(metricRepository.findAll()).thenReturn(List.of());
        when(analysisDashboardRepository.findAll()).thenReturn(List.of());
        when(dataSyncTableConfigRepository.findByDesTableNameIgnoreCase("fact_sales")).thenReturn(List.of(config));
        when(dataSyncRepository.findById("s1")).thenReturn(Optional.of(sync));
        when(etlNodeRepository.findAll()).thenReturn(List.of());

        ModelLineageDTO lineage = service.build("f1");

        assertThat(lineage.nodes()).anyMatch(node -> "sync:s1".equals(node.id()));
        assertThat(lineage.nodes()).anyMatch(node -> "ods.orders".equals(node.name()));
        assertThat(lineage.edges()).anyMatch(edge -> "SYNC".equals(edge.kind()));
    }

    private DataModel model(String id, String name, String type, String schema, String table) {
        DataModel model = new DataModel();
        model.setId(id);
        model.setName(name);
        model.setCode(name);
        model.setModelType(type);
        model.setDataSourceId("ds1");
        model.setSchemaName(schema);
        model.setTableName(table);
        return model;
    }

    private ModelField field(String id, String modelId, String fieldName, String dimensionModelId, String dimensionFieldId) {
        ModelField field = new ModelField();
        field.setId(id);
        field.setModelId(modelId);
        field.setFieldName(fieldName);
        field.setDimensionModelId(dimensionModelId);
        field.setDimensionFieldId(dimensionFieldId);
        return field;
    }

    private Metric metric(String id, String code, String type, String factModelId, String formula) {
        Metric metric = new Metric();
        metric.setId(id);
        metric.setName(code);
        metric.setCode(code);
        metric.setMetricType(type);
        metric.setFactModelId(factModelId);
        metric.setFormula(formula);
        metric.setStatus("ENABLED");
        return metric;
    }

    private AnalysisDashboard dashboard() throws Exception {
        Map<String, Object> metricDataset = Map.of(
            "id",
            "sales",
            "type",
            "METRIC",
            "metricQuery",
            Map.of("metricCodes", List.of("sales_amount"))
        );
        Map<String, Object> sqlDataset = Map.of(
            "id",
            "detail",
            "type",
            "SQL",
            "dataSourceId",
            "ds1",
            "sql",
            "SELECT * FROM main.fact_sales"
        );
        AnalysisDashboard dashboard = new AnalysisDashboard();
        dashboard.setId("db1");
        dashboard.setName("销售看板");
        dashboard.setCode("sales_dashboard");
        dashboard.setDataSourceId("ds1");
        dashboard.setSpec(objectMapper.writeValueAsString(Map.of("datasets", List.of(metricDataset, sqlDataset))));
        return dashboard;
    }

    private ETLNode node(String id, String taskId, String type, String label, String config) {
        ETLNode node = new ETLNode();
        node.setId(id);
        node.setCode(id);
        node.setTaskId(taskId);
        node.setType(type);
        node.setLabel(label);
        node.setConfig(config);
        return node;
    }

    private ETLTask task(String id, String name) {
        ETLTask task = new ETLTask();
        task.setId(id);
        task.setTaskName(name);
        return task;
    }

    private List<String> edgeKinds(ModelLineageDTO lineage) {
        return lineage.edges().stream().map(edge -> edge.kind()).toList();
    }
}
