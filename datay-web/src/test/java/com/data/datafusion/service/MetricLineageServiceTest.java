package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.AnalysisDashboard;
import com.data.datafusion.domain.Metric;
import com.data.datafusion.repository.AnalysisDashboardRepository;
import com.data.datafusion.repository.MetricRepository;
import com.data.datafusion.service.dto.LineageEdgeDTO;
import com.data.datafusion.service.dto.LineageNodeDTO;
import com.data.datafusion.service.dto.MetricLineageDTO;
import com.data.datafusion.service.dto.ModelLineageDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricLineageServiceTest {

    private static final Pattern FORMULA_REF = Pattern.compile("\\$\\{([A-Za-z0-9_\\-]+)}");

    private MetricRepository metricRepository;
    private AnalysisDashboardRepository analysisDashboardRepository;
    private DataModelLineageService dataModelLineageService;
    private MetricService metricService;
    private ObjectMapper objectMapper;
    private MetricLineageService service;

    @BeforeEach
    void setUp() {
        metricRepository = mock(MetricRepository.class);
        analysisDashboardRepository = mock(AnalysisDashboardRepository.class);
        dataModelLineageService = mock(DataModelLineageService.class);
        metricService = mock(MetricService.class);
        objectMapper = new ObjectMapper();
        when(metricService.parseFormulaRefs(any())).thenAnswer(invocation -> formulaRefs(invocation.getArgument(0)));
        service = new MetricLineageService(
            metricRepository,
            analysisDashboardRepository,
            dataModelLineageService,
            metricService,
            objectMapper
        );
    }

    @Test
    void shouldBuildAtomicMetricUpstreamAndDashboard() throws Exception {
        Metric amount = metric("m1", "sales_amount", Metric.TYPE_ATOMIC, "f1", "SUM(amount)");
        when(metricRepository.findById("m1")).thenReturn(Optional.of(amount));
        when(metricRepository.findAll()).thenReturn(List.of(amount));
        when(dataModelLineageService.buildUpstream("f1")).thenReturn(modelUpstream());
        when(analysisDashboardRepository.findAll()).thenReturn(List.of(dashboard("sales_dashboard", "sales_amount")));

        MetricLineageDTO lineage = service.build("m1");

        assertThat(lineage.nodes()).anyMatch(node -> "metric:m1".equals(node.id()) && node.layer() == 0);
        assertThat(lineage.nodes()).anyMatch(node -> "model:f1".equals(node.id()) && node.layer() == -1);
        assertThat(lineage.nodes()).anyMatch(node -> "table:ds1:main.fact_sales".equals(node.id()) && node.layer() == -2);
        assertThat(lineage.nodes()).anyMatch(node -> "DASHBOARD".equals(node.type()) && node.layer() == 1);
        assertThat(kinds(lineage)).contains("METRIC", "DASHBOARD");
    }

    @Test
    void shouldBuildDerivedMetricUpstreamAndDownstream() throws Exception {
        Metric amount = metric("m1", "sales_amount", Metric.TYPE_ATOMIC, "f1", "SUM(amount)");
        Metric profitRate = metric("m2", "profit_rate", Metric.TYPE_DERIVED, null, "${sales_amount} / 100");
        Metric doubled = metric("m3", "profit_rate_double", Metric.TYPE_DERIVED, null, "${profit_rate} * 2");
        when(metricRepository.findById("m2")).thenReturn(Optional.of(profitRate));
        when(metricRepository.findAll()).thenReturn(List.of(amount, profitRate, doubled));
        when(dataModelLineageService.buildUpstream("f1")).thenReturn(modelUpstream());
        when(analysisDashboardRepository.findAll())
            .thenReturn(List.of(dashboard("profit_dashboard", "profit_rate", "profit_rate_double")));

        MetricLineageDTO lineage = service.build("m2");

        assertThat(lineage.nodes()).anyMatch(node -> "metric:m1".equals(node.id()) && node.layer() == -1);
        assertThat(lineage.nodes()).anyMatch(node -> "metric:m3".equals(node.id()) && node.layer() == 1);
        assertThat(lineage.nodes()).anyMatch(node -> "DASHBOARD".equals(node.type()) && node.layer() == 2);
        assertThat(kinds(lineage)).contains("DERIVE", "METRIC", "DASHBOARD");
        assertThat(lineage.edges()).anyMatch(edge -> "metric:m1".equals(edge.source()) && "metric:m2".equals(edge.target()));
        assertThat(lineage.edges()).anyMatch(edge -> "metric:m2".equals(edge.source()) && "metric:m3".equals(edge.target()));
    }

    private ModelLineageDTO modelUpstream() {
        LineageNodeDTO model = new LineageNodeDTO("model:f1", "MODEL", "fact_sales", "DWD", 0, Map.of("code", "fact_sales"));
        LineageNodeDTO table = new LineageNodeDTO("table:ds1:main.fact_sales", "TABLE", "main.fact_sales", null, -1, Map.of());
        return new ModelLineageDTO(
            "f1",
            List.of(model, table),
            List.of(new LineageEdgeDTO("table->model", table.id(), model.id(), "TABLE", "物理表", "CONFIG"))
        );
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

    private AnalysisDashboard dashboard(String code, String... metricCodes) throws Exception {
        AnalysisDashboard dashboard = new AnalysisDashboard();
        dashboard.setId("db-" + code);
        dashboard.setName(code);
        dashboard.setCode(code);
        dashboard.setDataSourceId("ds1");
        Map<String, Object> dataset = Map.of(
            "id",
            "dataset",
            "name",
            "指标数据集",
            "metricQuery",
            Map.of("metricCodes", List.of(metricCodes))
        );
        dashboard.setSpec(objectMapper.writeValueAsString(Map.of("datasets", List.of(dataset))));
        return dashboard;
    }

    private Set<String> formulaRefs(Object formula) {
        Set<String> result = new LinkedHashSet<>();
        if (formula == null) {
            return result;
        }
        Matcher matcher = FORMULA_REF.matcher(String.valueOf(formula));
        while (matcher.find()) {
            result.add(matcher.group(1));
        }
        return result;
    }

    private List<String> kinds(MetricLineageDTO lineage) {
        return lineage.edges().stream().map(LineageEdgeDTO::kind).toList();
    }
}
