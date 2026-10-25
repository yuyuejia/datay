package com.data.datafusion.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.AnalysisDashboard;
import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.domain.RoleDatasetScope;
import com.data.datafusion.repository.AnalysisDashboardRepository;
import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.data.datafusion.repository.AuthorityRepository;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.RoleDatasetScopeRepository;
import com.data.datafusion.service.DataSourceQueryService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.DimensionValueService;
import com.data.datafusion.service.MetricQueryService;
import com.data.datafusion.service.RoleDatasetScopeService;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.mapper.RoleDatasetScopeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 看板通过 refId 引用已保存数据集时，数据集行级数据权限在真实 SQL 执行链路上的生效验证。
 *
 * <p>使用 H2 执行 DatasetQueryService 生成的最终 SQL，覆盖：
 * <ul>
 *   <li>看板引用已保存 SQL 数据集时按角色条件过滤行；</li>
 *   <li>看板内嵌数据集不受已保存数据集的权限规则影响；</li>
 *   <li>ROLE_ADMIN 与无匹配规则时不过滤；</li>
 *   <li>多个角色的规则条件按 OR 合并。</li>
 * </ul>
 */
class DashboardDatasetScopeControlTest {

    private String jdbcUrl;
    private ObjectMapper objectMapper;
    private AnalysisDashboardRepository dashboardRepository;
    private AnalysisDatasetRepository analysisDatasetRepository;
    private RoleDatasetScopeRepository roleDatasetScopeRepository;
    private DashboardDataService dashboardDataService;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        jdbcUrl = "jdbc:h2:mem:dashboard_scope_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "sa", ""); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE orders (region VARCHAR(20), order_amount INT)");
            statement.execute("INSERT INTO orders (region, order_amount) VALUES ('华东', 100), ('华南', 200), ('华北', 300)");
        }

        dashboardRepository = mock(AnalysisDashboardRepository.class);
        analysisDatasetRepository = mock(AnalysisDatasetRepository.class);
        roleDatasetScopeRepository = mock(RoleDatasetScopeRepository.class);
        DataSourceQueryService dataSourceQueryService = mock(DataSourceQueryService.class);
        DataSourceService dataSourceService = mock(DataSourceService.class);
        when(dataSourceQueryService.executeQuery(any(), anyString())).thenAnswer(invocation -> executeH2(invocation.getArgument(1)));

        AnalysisDataset entity = new AnalysisDataset();
        entity.setId("lib-1");
        entity.setName("订单明细");
        entity.setType(AnalysisDataset.TYPE_SQL);
        entity.setDataSourceId("source-1");
        entity.setDefinition("{\"sql\":\"SELECT region, order_amount FROM orders\"}");
        entity.setFields("{\"columns\":[{\"name\":\"region\"},{\"name\":\"order_amount\"}]}");
        when(analysisDatasetRepository.findById("lib-1")).thenReturn(Optional.of(entity));

        when(roleDatasetScopeRepository.findByRoleNameInAndDatasetIdAndEnabledTrue(anyCollection(), eq("lib-1")))
            .thenReturn(List.of(scope("ROLE_REGION", "lib-1", conditionJson("region", "EQ", "华东"))));

        DataSourceDTO dataSource = new DataSourceDTO();
        dataSource.setId("source-1");
        dataSource.setType("MYSQL");
        when(dataSourceService.findOne("source-1")).thenReturn(Optional.of(dataSource));

        AnalysisDatasetResolver resolver = new AnalysisDatasetResolver(analysisDatasetRepository, objectMapper);
        RoleDatasetScopeService roleDatasetScopeService = new RoleDatasetScopeService(
            roleDatasetScopeRepository,
            mock(AuthorityRepository.class),
            analysisDatasetRepository,
            mock(RoleDatasetScopeMapper.class),
            objectMapper
        );
        DatasetQueryService datasetQueryService = new DatasetQueryService(
            mock(MetricQueryService.class),
            dataSourceService,
            dataSourceQueryService,
            roleDatasetScopeService,
            resolver,
            objectMapper
        );
        DashboardSpecValidator specValidator = new DashboardSpecValidator(objectMapper, resolver);
        dashboardDataService = new DashboardDataService(
            dashboardRepository,
            specValidator,
            resolver,
            datasetQueryService,
            mock(DimensionValueService.class),
            mock(DataModelRepository.class)
        );

        AnalysisDashboard dashboard = new AnalysisDashboard();
        dashboard.setId("dashboard-1");
        dashboard.setDataSourceId("source-1");
        dashboard.setSpec(objectMapper.writeValueAsString(dashboardSpec()));
        when(dashboardRepository.findById("dashboard-1")).thenReturn(Optional.of(dashboard));

        authenticate("ROLE_REGION");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldFilterRowsWhenDashboardReferencesSavedDataset() throws Exception {
        Map<String, Object> result = dashboardDataService.queryDataset("dashboard-1", "chart-1", Map.of());

        assertThat(regions(result)).containsExactly("华东");
    }

    @Test
    void shouldNotFilterEmbeddedDatasetInDashboard() throws Exception {
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("id", "chart-inline");
        dataset.put("type", "SQL");
        dataset.put("sql", "SELECT region, order_amount FROM orders");
        dataset.put("dataSourceId", "source-1");
        dataset.put("fields", "{\"columns\":[{\"name\":\"region\"},{\"name\":\"order_amount\"}]}");
        Map<String, Object> spec = Map.of("datasets", List.of(dataset), "filters", List.of());

        Map<String, Object> result = dashboardDataService.queryDataset(spec, "source-1", "chart-inline", Map.of());

        assertThat(regions(result)).containsExactlyInAnyOrder("华东", "华南", "华北");
        verify(roleDatasetScopeRepository, never()).findByRoleNameInAndDatasetIdAndEnabledTrue(anyCollection(), anyString());
    }

    @Test
    void shouldPushDownSqlColumnFilterFromTargets() throws Exception {
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("id", "chart-filter");
        dataset.put("type", "SQL");
        dataset.put("sql", "SELECT region, order_amount FROM orders");
        dataset.put("dataSourceId", "source-1");
        dataset.put("fields", "{\"columns\":[{\"name\":\"region\"},{\"name\":\"order_amount\"}]}");
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("datasetId", "chart-filter");
        target.put("field", "region");
        target.put("mode", "server");
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("id", "region");
        filter.put("type", "multiSelect");
        filter.put("targets", List.of(target));
        Map<String, Object> spec = Map.of("datasets", List.of(dataset), "filters", List.of(filter));

        Map<String, Object> result = dashboardDataService.queryDataset(
            spec,
            "source-1",
            "chart-filter",
            Map.of("region", List.of("华东", "华南"))
        );

        assertThat(regions(result)).containsExactlyInAnyOrder("华东", "华南");
    }

    @Test
    void shouldPushDownSqlSortAndLimitFromWidgetQuery() throws Exception {
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("id", "chart-sort");
        dataset.put("type", "SQL");
        dataset.put("sql", "SELECT region, order_amount FROM orders");
        dataset.put("dataSourceId", "source-1");
        dataset.put("fields", "{\"columns\":[{\"name\":\"region\"},{\"name\":\"order_amount\"}]}");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("id", "widget-1");
        query.put("datasetId", "chart-sort");
        query.put("sortField", "order_amount");
        query.put("sortOrder", "desc");
        query.put("limit", 2);
        Map<String, Object> spec = Map.of("datasets", List.of(dataset), "filters", List.of());

        Map<String, Object> response = dashboardDataService.queryWidgets(
            spec,
            "source-1",
            List.of("chart-sort"),
            List.of(query),
            Map.of()
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> widget = (Map<String, Object>) ((Map<String, Object>) response.get("widgets")).get("widget-1");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) widget.get("rows");
        assertThat(rows).hasSize(2);
        assertThat(rows.stream().map(row -> value(row, "order_amount")).toList()).containsExactly(300, 200);
    }

    @Test
    void shouldPushDownSqlPaginationOffset() throws Exception {
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("id", "chart-page");
        dataset.put("type", "SQL");
        dataset.put("sql", "SELECT region, order_amount FROM orders");
        dataset.put("dataSourceId", "source-1");
        dataset.put("fields", "{\"columns\":[{\"name\":\"region\"},{\"name\":\"order_amount\"}]}");
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("id", "widget-page");
        query.put("datasetId", "chart-page");
        query.put("sortField", "order_amount");
        query.put("sortOrder", "desc");
        query.put("limit", 1);
        query.put("offset", 1);
        Map<String, Object> spec = Map.of("datasets", List.of(dataset), "filters", List.of());

        Map<String, Object> response = dashboardDataService.queryWidgets(
            spec,
            "source-1",
            List.of(),
            List.of(query),
            Map.of()
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> widget = (Map<String, Object>) ((Map<String, Object>) response.get("widgets")).get("widget-page");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) widget.get("rows");
        assertThat(rows).hasSize(1);
        assertThat(value(rows.get(0), "order_amount")).isEqualTo(200);
    }

    @Test
    void shouldNotFilterForAdmin() throws Exception {
        authenticate("ROLE_ADMIN");

        Map<String, Object> result = dashboardDataService.queryDataset("dashboard-1", "chart-1", Map.of());

        assertThat(regions(result)).containsExactlyInAnyOrder("华东", "华南", "华北");
        verify(roleDatasetScopeRepository, never()).findByRoleNameInAndDatasetIdAndEnabledTrue(anyCollection(), anyString());
    }

    @Test
    void shouldNotFilterWhenNoRuleMatches() throws Exception {
        when(roleDatasetScopeRepository.findByRoleNameInAndDatasetIdAndEnabledTrue(anyCollection(), eq("lib-1")))
            .thenReturn(List.of());

        Map<String, Object> result = dashboardDataService.queryDataset("dashboard-1", "chart-1", Map.of());

        assertThat(regions(result)).containsExactlyInAnyOrder("华东", "华南", "华北");
    }

    @Test
    void shouldCombineRulesFromMultipleRolesUsingOr() throws Exception {
        authenticate("ROLE_REGION", "ROLE_SALES");
        when(roleDatasetScopeRepository.findByRoleNameInAndDatasetIdAndEnabledTrue(anyCollection(), eq("lib-1")))
            .thenReturn(
                List.of(
                    scope("ROLE_REGION", "lib-1", conditionJson("region", "EQ", "华东")),
                    scope("ROLE_SALES", "lib-1", conditionJson("region", "EQ", "华南"))
                )
            );

        Map<String, Object> result = dashboardDataService.queryDataset("dashboard-1", "chart-1", Map.of());

        assertThat(regions(result)).containsExactlyInAnyOrder("华东", "华南");
    }

    private Map<String, Object> dashboardSpec() {
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("id", "chart-1");
        dataset.put("refId", "lib-1");
        return Map.of("datasets", List.of(dataset), "filters", List.of());
    }

    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext()
            .setAuthentication(
                new UsernamePasswordAuthenticationToken(
                    "user",
                    "pwd",
                    Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()
                )
            );
    }

    private RoleDatasetScope scope(String roleName, String datasetId, String filterConfig) {
        RoleDatasetScope entity = new RoleDatasetScope();
        entity.setRoleName(roleName);
        entity.setDatasetId(datasetId);
        entity.setFilterConfig(filterConfig);
        entity.setEnabled(true);
        return entity;
    }

    private String conditionJson(String fieldName, String operator, String value) {
        return "{\"conditions\":[{\"fieldName\":\"" + fieldName + "\",\"operator\":\"" + operator + "\",\"value\":\"" + value + "\"}]}";
    }

    private Map<String, Object> executeH2(String sql) throws SQLException {
        try (
            Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery(sql)
        ) {
            ResultSetMetaData metaData = resultSet.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= metaData.getColumnCount(); i++) {
                columns.add(metaData.getColumnLabel(i));
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            while (resultSet.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    row.put(metaData.getColumnLabel(i), resultSet.getObject(i));
                }
                rows.add(row);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("columns", columns);
            result.put("rows", rows);
            result.put("affectedRows", 0);
            return result;
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> regions(Map<String, Object> result) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) result.get("rows");
        return rows.stream().map(row -> String.valueOf(value(row, "region"))).toList();
    }

    private Object value(Map<String, Object> row, String key) {
        return row.entrySet()
            .stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(key))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    }
}
