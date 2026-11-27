package com.data.datafusion.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.AnalysisDashboard;
import com.data.datafusion.repository.AnalysisDashboardRepository;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.service.DimensionValueService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 看板数据集执行路径与空筛选条件占位符中和逻辑测试。
 */
class DashboardDataServiceTest {

    @Test
    void shouldNeutralizeEmptyInPredicate() {
        String result = DashboardDataService.neutralizeEmptyParams(
            "SELECT * FROM t WHERE 1=1 AND region IN (${region})",
            new LinkedHashMap<>()
        );
        assertThat(result).doesNotContain("${region}");
        assertThat(result).contains("1=1");
    }

    @Test
    void shouldNeutralizeEmptyEqualityAndLikePredicates() {
        assertThat(DashboardDataService.neutralizeEmptyParams("WHERE city = ${city}", new LinkedHashMap<>())).contains("1=1");
        assertThat(DashboardDataService.neutralizeEmptyParams("WHERE name LIKE ${kw}", new LinkedHashMap<>())).contains("1=1");
        assertThat(DashboardDataService.neutralizeEmptyParams("WHERE amt >= ${min}", new LinkedHashMap<>())).contains("1=1");
    }

    @Test
    void shouldNeutralizeBetweenPredicateBothBounds() {
        String result = DashboardDataService.neutralizeEmptyParams(
            "WHERE order_date BETWEEN ${start} AND ${end}",
            new LinkedHashMap<>()
        );
        assertThat(result).doesNotContain("${start}").doesNotContain("${end}");
        assertThat(result).contains("1=1");
    }

    @Test
    void shouldNeutralizeQuotedEmptyPredicate() {
        assertThat(DashboardDataService.neutralizeEmptyParams("WHERE d = '${start}'", new LinkedHashMap<>())).contains("1=1");
        assertThat(DashboardDataService.neutralizeEmptyParams("WHERE region IN ('${region}')", new LinkedHashMap<>())).contains("1=1");
    }

    @Test
    void shouldNeutralizeQuotedBetweenPredicateBothBounds() {
        String result = DashboardDataService.neutralizeEmptyParams(
            "WHERE order_date BETWEEN '${start}' AND '${end}'",
            new LinkedHashMap<>()
        );
        assertThat(result).isEqualTo("WHERE 1=1");
    }

    @Test
    void shouldNeutralizeQuotedBetweenPredicateWithOneMissingBound() {
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("start", "2026-09-01");
        String result = DashboardDataService.neutralizeEmptyParams(
            "WHERE order_date BETWEEN '${start}' AND '${end}'",
            named
        );
        assertThat(result).isEqualTo("WHERE 1=1");
    }

    @Test
    void shouldNeutralizeQuotedLikeWildcard() {
        String result = DashboardDataService.neutralizeEmptyParams("WHERE name LIKE '%${kw}%'", new LinkedHashMap<>());
        assertThat(result).isEqualTo("WHERE 1=1");
    }

    @Test
    void shouldKeepValuedPlaceholderUntouched() {
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("region", List.of("华东", "华南"));
        String result = DashboardDataService.neutralizeEmptyParams(
            "SELECT * FROM t WHERE region IN (${region})",
            named
        );
        assertThat(result).contains("${region}");
    }

    @Test
    void shouldRenderListInsideInClause() {
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("region", List.of("华东", "华南"));
        String result = DashboardDataService.renderSql("WHERE region IN (${region})", named);
        assertThat(result).isEqualTo("WHERE region IN ('华东', '华南')");
    }

    @Test
    void shouldRenderPlaceholderInsideWildcardQuotes() {
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("kw", "东");
        String result = DashboardDataService.renderSql("WHERE name LIKE '%${kw}%'", named);
        assertThat(result).isEqualTo("WHERE name LIKE '%东%'");
    }

    @Test
    void shouldRenderQuotedDateAndNumericLiteral() {
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("start", "2024-01-01");
        named.put("min", 100);
        assertThat(DashboardDataService.renderSql("WHERE d = '${start}'", named)).isEqualTo("WHERE d = '2024-01-01'");
        assertThat(DashboardDataService.renderSql("WHERE amt >= ${min}", named)).isEqualTo("WHERE amt >= 100");
    }

    @Test
    void shouldEscapeSingleQuoteToPreventInjection() {
        Map<String, Object> named = new LinkedHashMap<>();
        named.put("city", "x' OR '1'='1");
        String result = DashboardDataService.renderSql("WHERE city = ${city}", named);
        assertThat(result).isEqualTo("WHERE city = 'x'' OR ''1''=''1'");
    }

    @Test
    void shouldPassReferencedDatasetIdAsScopeForInMemorySpec() throws Exception {
        Map<String, Object> resolvedDataset = dataset("local-1", "lib-1");
        Map<String, Object> filter = Map.of("id", "filter-1", "type", "select");
        Map<String, Object> spec = Map.of("datasets", List.of(Map.of("id", "local-1", "refId", "lib-1")));
        Map<String, Object> resolvedSpec = Map.of("datasets", List.of(resolvedDataset), "filters", List.of(filter));
        Map<String, Object> filterValues = Map.of("filter-1", "华东");

        AnalysisDatasetResolver resolver = mock(AnalysisDatasetResolver.class);
        DatasetQueryService queryService = mock(DatasetQueryService.class);
        when(resolver.resolveSpec(spec)).thenReturn(resolvedSpec);
        when(queryService.query(any(), any(), anyList(), any(), any(), any())).thenReturn(Map.of());

        newService(resolver, queryService, mock(AnalysisDashboardRepository.class), mock(DashboardSpecValidator.class))
            .queryDataset(spec, "fallback-source", "local-1", filterValues);

        verify(queryService).query(
            eq(resolvedDataset),
            eq("fallback-source"),
            eq(List.of(filter)),
            eq(filterValues),
            eq("lib-1"),
            eq(DatasetQueryOptions.NONE)
        );
    }

    @Test
    void shouldPassNullScopeForEmbeddedDataset() throws Exception {
        Map<String, Object> resolvedDataset = dataset("local-2", null);
        Map<String, Object> spec = Map.of("datasets", List.of(Map.of("id", "local-2", "type", "SQL")));
        Map<String, Object> resolvedSpec = Map.of("datasets", List.of(resolvedDataset));
        Map<String, Object> filterValues = Map.of();

        AnalysisDatasetResolver resolver = mock(AnalysisDatasetResolver.class);
        DatasetQueryService queryService = mock(DatasetQueryService.class);
        when(resolver.resolveSpec(spec)).thenReturn(resolvedSpec);
        when(queryService.query(any(), any(), anyList(), any(), any(), any())).thenReturn(Map.of());

        newService(resolver, queryService, mock(AnalysisDashboardRepository.class), mock(DashboardSpecValidator.class))
            .queryDataset(spec, "fallback-source", "local-2", filterValues);

        verify(queryService).query(
            eq(resolvedDataset),
            eq("fallback-source"),
            eq(List.of()),
            eq(filterValues),
            isNull(),
            eq(DatasetQueryOptions.NONE)
        );
    }

    @Test
    void shouldPassReferencedDatasetIdAsScopeForSavedDashboard() throws Exception {
        Map<String, Object> resolvedDataset = dataset("local-1", "lib-1");
        Map<String, Object> spec = Map.of("datasets", List.of(Map.of("id", "local-1", "refId", "lib-1")));
        Map<String, Object> resolvedSpec = Map.of("datasets", List.of(resolvedDataset));
        AnalysisDashboard dashboard = new AnalysisDashboard();
        dashboard.setId("dashboard-1");
        dashboard.setDataSourceId("fallback-source");
        dashboard.setSpec("dashboard-spec");

        AnalysisDashboardRepository dashboardRepository = mock(AnalysisDashboardRepository.class);
        DashboardSpecValidator specValidator = mock(DashboardSpecValidator.class);
        AnalysisDatasetResolver resolver = mock(AnalysisDatasetResolver.class);
        DatasetQueryService queryService = mock(DatasetQueryService.class);
        when(dashboardRepository.findById("dashboard-1")).thenReturn(Optional.of(dashboard));
        when(specValidator.parse("dashboard-spec")).thenReturn(spec);
        when(resolver.resolveSpec(spec)).thenReturn(resolvedSpec);
        when(queryService.query(any(), any(), anyList(), any(), any(), any())).thenReturn(Map.of());

        newService(resolver, queryService, dashboardRepository, specValidator).queryDataset("dashboard-1", "local-1", Map.of());

        verify(queryService).query(
            eq(resolvedDataset),
            eq("fallback-source"),
            eq(List.of()),
            eq(Map.of()),
            eq("lib-1"),
            eq(DatasetQueryOptions.NONE)
        );
    }

    @Test
    void shouldQueryMultipleDatasetsAndCollectPartialErrors() throws Exception {
        Map<String, Object> ds1 = dataset("ds1", "lib-1");
        Map<String, Object> ds2 = dataset("ds2", "lib-2");
        Map<String, Object> spec = Map.of("datasets", List.of(ds1, ds2));
        Map<String, Object> resolvedSpec = Map.of("datasets", List.of(ds1, ds2), "filters", List.of());
        AnalysisDatasetResolver resolver = mock(AnalysisDatasetResolver.class);
        DatasetQueryService queryService = mock(DatasetQueryService.class);
        when(resolver.resolveSpec(spec)).thenReturn(resolvedSpec);
        when(queryService.query(any(), any(), anyList(), any(), any(), any())).thenAnswer(invocation -> {
            Map<String, Object> dataset = invocation.getArgument(0);
            if ("ds2".equals(dataset.get("id"))) {
                throw new IllegalArgumentException("ds2 查询失败");
            }
            return Map.of("rows", List.of(Map.of("value", 1)));
        });

        Map<String, Object> result = newService(
            resolver,
            queryService,
            mock(AnalysisDashboardRepository.class),
            mock(DashboardSpecValidator.class)
        ).queryDatasets(spec, "source-1", List.of("ds1", "ds2"), Map.of());

        assertThat(result.get("datasets")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP).containsKey("ds1");
        assertThat(result.get("errors")).asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
            .containsEntry("ds2", "ds2 查询失败");
        verify(resolver).resolveSpec(spec);
        verify(queryService).query(eq(ds1), eq("source-1"), eq(List.of()), eq(Map.of()), eq("lib-1"), any());
        verify(queryService).query(eq(ds2), eq("source-1"), eq(List.of()), eq(Map.of()), eq("lib-2"), any());
    }

    @Test
    void shouldLoadDirectFieldFilterOptionsFromServerDataset() throws Exception {
        Map<String, Object> dataset = dataset("ds1", "lib-1");
        Map<String, Object> filter = Map.of(
            "id",
            "f1",
            "type",
            "select",
            "targets",
            List.of(Map.of("datasetId", "ds1", "field", "region", "mode", "client"))
        );
        Map<String, Object> spec = Map.of("datasets", List.of(dataset), "filters", List.of(filter));
        DatasetQueryService queryService = mock(DatasetQueryService.class);
        when(queryService.loadFieldValues(eq("lib-1"), eq("region"), eq("华"), eq(1), eq(50)))
            .thenReturn(Map.of("values", List.of("华东"), "total", 1L));

        Map<String, Object> result = newService(
            mock(AnalysisDatasetResolver.class),
            queryService,
            mock(AnalysisDashboardRepository.class),
            mock(DashboardSpecValidator.class)
        ).loadFilterOptions(spec, "f1", "华", null);

        assertThat(result.get("values")).isEqualTo(List.of("华东"));
        verify(queryService).loadFieldValues("lib-1", "region", "华", 1, 50);
    }

    @Test
    void shouldCacheRepeatedSavedDashboardQueriesAndEvict() throws Exception {
        Map<String, Object> ds1 = dataset("ds1", "lib-1");
        Map<String, Object> spec = Map.of("datasets", List.of(ds1));
        Map<String, Object> resolvedSpec = Map.of("datasets", List.of(ds1), "filters", List.of());
        AnalysisDashboard dashboard = new AnalysisDashboard();
        dashboard.setId("dashboard-cache");
        dashboard.setDataSourceId("source-1");
        dashboard.setSpec("dashboard-spec");

        AnalysisDashboardRepository repository = mock(AnalysisDashboardRepository.class);
        DashboardSpecValidator validator = mock(DashboardSpecValidator.class);
        AnalysisDatasetResolver resolver = mock(AnalysisDatasetResolver.class);
        DatasetQueryService queryService = mock(DatasetQueryService.class);
        when(repository.findById("dashboard-cache")).thenReturn(Optional.of(dashboard));
        when(validator.parse(any())).thenReturn(spec);
        when(resolver.resolveSpec(any())).thenReturn(resolvedSpec);
        when(queryService.query(any(), any(), anyList(), any(), any(), any())).thenReturn(
            new LinkedHashMap<>(Map.of("rows", List.of(Map.of("value", 1))))
        );

        DashboardDataService service = newService(resolver, queryService, repository, validator);
        service.queryWidgets("dashboard-cache", List.of("ds1"), List.of(), Map.of());
        service.queryWidgets("dashboard-cache", List.of("ds1"), List.of(), Map.of());

        verify(repository, times(1)).findById("dashboard-cache");
        verify(queryService, times(1)).query(any(), any(), anyList(), any(), any(), any());

        service.evictDashboardCache("dashboard-cache");
        service.queryWidgets("dashboard-cache", List.of("ds1"), List.of(), Map.of());

        verify(repository, times(2)).findById("dashboard-cache");
        verify(queryService, times(2)).query(any(), any(), anyList(), any(), any(), any());
    }

    @Test
    void shouldDrillDatasetWithoutAggregationAndApplyDimensionFilters() throws Exception {
        Map<String, Object> dataset = dataset("ds1", "lib-1");
        Map<String, Object> existingFilter = Map.of("id", "global", "type", "select");
        Map<String, Object> resolvedSpec = new LinkedHashMap<>();
        resolvedSpec.put("datasets", List.of(dataset));
        resolvedSpec.put("filters", List.of(existingFilter));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("datasetId", "ds1");
        body.put(
            "dimensions",
            List.of(
                Map.of("field", "region", "values", List.of("华东")),
                Map.of("field", "channel", "values", List.of("官网", "APP"))
            )
        );
        body.put("filterValues", Map.of("global", "all", "__drill_9", "injected"));
        body.put("limit", 50);
        body.put("offset", 100);
        body.put("sortField", "sales_amount");
        body.put("sortOrder", "desc");

        AnalysisDatasetResolver resolver = mock(AnalysisDatasetResolver.class);
        DatasetQueryService queryService = mock(DatasetQueryService.class);
        when(resolver.resolveSpec(any())).thenReturn(resolvedSpec);
        when(queryService.query(any(), any(), anyList(), any(), any(), any())).thenReturn(
            new LinkedHashMap<>(Map.of("rows", List.of()))
        );

        Map<String, Object> spec = Map.of("datasets", List.of(dataset));
        newService(resolver, queryService, mock(AnalysisDashboardRepository.class), mock(DashboardSpecValidator.class)).drillDataset(
            spec,
            "fallback-source",
            body
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> filtersCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> valuesCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<DatasetQueryOptions> optionsCaptor = ArgumentCaptor.forClass(DatasetQueryOptions.class);
        verify(queryService).query(
            eq(dataset),
            eq("fallback-source"),
            filtersCaptor.capture(),
            valuesCaptor.capture(),
            eq("lib-1"),
            optionsCaptor.capture()
        );

        List<Map<String, Object>> filters = filtersCaptor.getValue();
        assertThat(filters).hasSize(3);
        assertThat(filters.get(1)).containsEntry("id", "__drill_0").containsEntry("type", "select");
        assertThat(filters.get(2)).containsEntry("id", "__drill_1").containsEntry("type", "select");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> targets = (List<Map<String, Object>>) filters.get(1).get("targets");
        assertThat(targets).hasSize(1);
        assertThat(targets.get(0))
            .containsEntry("datasetId", "ds1")
            .containsEntry("field", "region")
            .containsEntry("mode", "server");

        Map<String, Object> values = valuesCaptor.getValue();
        assertThat(values).containsEntry("global", "all");
        assertThat(values).doesNotContainKey("__drill_9");
        assertThat(values).containsEntry("__drill_0", "华东");
        assertThat(values.get("__drill_1")).isEqualTo(List.of("官网", "APP"));

        DatasetQueryOptions options = optionsCaptor.getValue();
        assertThat(options.groupFields()).isEmpty();
        assertThat(options.aggregations()).isEmpty();
        assertThat(options.limit()).isEqualTo(50);
        assertThat(options.offset()).isEqualTo(100);
        assertThat(options.sortField()).isEqualTo("sales_amount");
        assertThat(options.sortOrder()).isEqualTo("desc");
    }

    @Test
    void shouldApplyLinkageConditionsToWidgetQuery() throws Exception {
        Map<String, Object> dataset = dataset("ds1", "lib-1");
        Map<String, Object> resolvedSpec = new LinkedHashMap<>();
        resolvedSpec.put("datasets", List.of(dataset));
        resolvedSpec.put("filters", List.of());

        AnalysisDatasetResolver resolver = mock(AnalysisDatasetResolver.class);
        DatasetQueryService queryService = mock(DatasetQueryService.class);
        when(resolver.resolveSpec(any())).thenReturn(resolvedSpec);
        when(queryService.query(any(), any(), anyList(), any(), any(), any())).thenReturn(
            new LinkedHashMap<>(Map.of("rows", List.of()))
        );

        Map<String, Object> condition = new LinkedHashMap<>();
        condition.put("field", "region");
        condition.put("values", List.of("华东", "华南"));
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("id", "q1");
        query.put("datasetId", "ds1");
        query.put("conditions", List.of(condition));

        // 调用方伪造的联动条件必须被忽略，只保留服务端生成的条件。
        Map<String, Object> response = newService(
            resolver,
            queryService,
            mock(AnalysisDashboardRepository.class),
            mock(DashboardSpecValidator.class)
        ).queryWidgets(
            Map.of("datasets", List.of(dataset)),
            "fallback-source",
            List.of(),
            List.of(query),
            Map.of("__linkage_9", "injected")
        );
        assertThat(response).containsKey("widgets");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> filtersCaptor = ArgumentCaptor.forClass(List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> valuesCaptor = ArgumentCaptor.forClass(Map.class);
        verify(queryService).query(
            eq(dataset),
            eq("fallback-source"),
            filtersCaptor.capture(),
            valuesCaptor.capture(),
            eq("lib-1"),
            any(DatasetQueryOptions.class)
        );

        List<Map<String, Object>> filters = filtersCaptor.getValue();
        assertThat(filters).hasSize(1);
        assertThat(filters.get(0)).containsEntry("id", "__linkage_0").containsEntry("type", "select");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> targets = (List<Map<String, Object>>) filters.get(0).get("targets");
        assertThat(targets).hasSize(1);
        assertThat(targets.get(0))
            .containsEntry("datasetId", "ds1")
            .containsEntry("field", "region")
            .containsEntry("mode", "server");

        Map<String, Object> values = valuesCaptor.getValue();
        assertThat(values).doesNotContainKey("__linkage_9");
        assertThat(values.get("__linkage_0")).isEqualTo(List.of("华东", "华南"));
    }

    @Test
    void shouldUseDefaultDrillLimitAndRejectInvalidDimension() throws Exception {
        Map<String, Object> dataset = dataset("ds1", null);
        Map<String, Object> resolvedSpec = new LinkedHashMap<>();
        resolvedSpec.put("datasets", List.of(dataset));
        resolvedSpec.put("filters", List.of());

        AnalysisDatasetResolver resolver = mock(AnalysisDatasetResolver.class);
        DatasetQueryService queryService = mock(DatasetQueryService.class);
        when(resolver.resolveSpec(any())).thenReturn(resolvedSpec);
        when(queryService.query(any(), any(), anyList(), any(), any(), any())).thenReturn(
            new LinkedHashMap<>(Map.of("rows", List.of()))
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("datasetId", "ds1");
        body.put("dimensions", List.of(Map.of("field", "region", "values", List.of("华东"))));
        newService(resolver, queryService, mock(AnalysisDashboardRepository.class), mock(DashboardSpecValidator.class)).drillDataset(
            Map.of("datasets", List.of(dataset)),
            "fallback-source",
            body
        );

        ArgumentCaptor<DatasetQueryOptions> optionsCaptor = ArgumentCaptor.forClass(DatasetQueryOptions.class);
        verify(queryService).query(any(), any(), anyList(), any(), any(), optionsCaptor.capture());
        assertThat(optionsCaptor.getValue().limit()).isEqualTo(50);
        assertThat(optionsCaptor.getValue().offset()).isZero();

        Map<String, Object> invalidBody = new LinkedHashMap<>();
        invalidBody.put("datasetId", "ds1");
        invalidBody.put("dimensions", List.of(Map.of("field", "region; drop table", "values", List.of("华东"))));
        assertThatThrownBy(() ->
            newService(resolver, queryService, mock(AnalysisDashboardRepository.class), mock(DashboardSpecValidator.class)).drillDataset(
                Map.of("datasets", List.of(dataset)),
                "fallback-source",
                invalidBody
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("字段名不合法");
    }

    @Test
    void shouldDrillSavedDashboardWithFallbackDataSource() throws Exception {
        Map<String, Object> dataset = dataset("local-1", "lib-1");
        Map<String, Object> spec = Map.of("datasets", List.of(Map.of("id", "local-1", "refId", "lib-1")));
        Map<String, Object> resolvedSpec = new LinkedHashMap<>();
        resolvedSpec.put("datasets", List.of(dataset));
        resolvedSpec.put("filters", List.of());
        AnalysisDashboard dashboard = new AnalysisDashboard();
        dashboard.setId("dashboard-1");
        dashboard.setDataSourceId("fallback-source");
        dashboard.setSpec("dashboard-spec");

        AnalysisDashboardRepository repository = mock(AnalysisDashboardRepository.class);
        DashboardSpecValidator validator = mock(DashboardSpecValidator.class);
        AnalysisDatasetResolver resolver = mock(AnalysisDatasetResolver.class);
        DatasetQueryService queryService = mock(DatasetQueryService.class);
        when(repository.findById("dashboard-1")).thenReturn(Optional.of(dashboard));
        when(validator.parse("dashboard-spec")).thenReturn(spec);
        when(resolver.resolveSpec(spec)).thenReturn(resolvedSpec);
        when(queryService.query(any(), any(), anyList(), any(), any(), any())).thenReturn(
            new LinkedHashMap<>(Map.of("rows", List.of()))
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("datasetId", "local-1");
        body.put("dimensions", List.of(Map.of("field", "region", "values", List.of("华东"))));
        newService(resolver, queryService, repository, validator).drillDataset("dashboard-1", body);

        verify(queryService).query(eq(dataset), eq("fallback-source"), anyList(), any(), eq("lib-1"), any());
    }

    private DashboardDataService newService(
        AnalysisDatasetResolver resolver,
        DatasetQueryService queryService,
        AnalysisDashboardRepository dashboardRepository,
        DashboardSpecValidator specValidator
    ) {
        return new DashboardDataService(
            dashboardRepository,
            specValidator,
            resolver,
            queryService,
            mock(DimensionValueService.class),
            mock(DataModelRepository.class)
        );
    }

    private Map<String, Object> dataset(String id, String refId) {
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("id", id);
        if (refId != null) {
            dataset.put("refId", refId);
        } else {
            dataset.put("type", "SQL");
        }
        return dataset;
    }
}
