package com.data.datafusion.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.Metric;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.domain.RoleDataScope;
import com.data.datafusion.repository.AuthorityRepository;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.MetricRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.repository.RoleDataScopeRepository;
import com.data.datafusion.service.DataSourceQueryService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.MetricQueryService;
import com.data.datafusion.service.MetricService;
import com.data.datafusion.service.MetricSqlService;
import com.data.datafusion.service.RoleDataScopeService;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.mapper.RoleDataScopeMapper;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 通过指标查询接口（{@code /api/metrics/query-sql}）验证基于角色的维度成员数据权限。
 * <p>
 * 真实的 {@link MetricQueryService} 与 {@link RoleDataScopeService} 参与链路，
 * 仅仓储/数据源以 mock 替代，验证「安全上下文角色 -> 数据范围解析 -> SQL 强制过滤」的完整链路。
 */
@WebMvcTest(controllers = MetricResource.class, properties = "jhipster.clientApp.name=datafusionApp")
@AutoConfigureMockMvc(addFilters = false)
@Import({ MetricQueryService.class, RoleDataScopeService.class })
class MetricQueryPermissionResourceTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private MetricService metricService;

    @MockitoBean
    private MetricSqlService metricSqlService;

    @MockitoBean
    private MetricRepository metricRepository;

    @MockitoBean
    private DataModelRepository dataModelRepository;

    @MockitoBean
    private ModelFieldRepository modelFieldRepository;

    @MockitoBean
    private DataSourceService dataSourceService;

    @MockitoBean
    private DataSourceQueryService dataSourceQueryService;

    @MockitoBean
    private RoleDataScopeRepository roleDataScopeRepository;

    @MockitoBean
    private AuthorityRepository authorityRepository;

    @MockitoBean
    private RoleDataScopeMapper roleDataScopeMapper;

    @BeforeEach
    void setUp() throws SQLException {
        Metric salesAmount = new Metric();
        salesAmount.setId("2610");
        salesAmount.setCode("sales_amount");
        salesAmount.setName("销售额");
        salesAmount.setMetricType(Metric.TYPE_ATOMIC);
        salesAmount.setFactModelId("3101");
        salesAmount.setFormula("SUM(amount)");
        when(metricRepository.findByCode("sales_amount")).thenReturn(Optional.of(salesAmount));
        when(metricRepository.findAll()).thenReturn(List.of(salesAmount));

        DataModel fact = new DataModel();
        fact.setId("3101");
        fact.setCode("fact_sales_order_item");
        fact.setModelType("DWD");
        fact.setSchemaName("main");
        fact.setTableName("fact_sales_order_item");
        fact.setDataSourceId("1600");
        when(dataModelRepository.findById("3101")).thenReturn(Optional.of(fact));
        when(dataSourceService.findOne("1600")).thenReturn(Optional.of(new DataSourceDTO()));
        when(dataSourceQueryService.executeQuery(any(), anyString()))
            .thenReturn(Map.of("columns", List.of(), "rows", List.of(), "affectedRows", 0));

        DataModel dimProduct = new DataModel();
        dimProduct.setId("3003");
        dimProduct.setCode("dim_product");
        dimProduct.setModelType("DIMENSION");
        dimProduct.setDimensionKind("NORMAL");
        dimProduct.setSchemaName("main");
        dimProduct.setTableName("dim_product");
        when(dataModelRepository.findById("3003")).thenReturn(Optional.of(dimProduct));

        ModelField amount = new ModelField();
        amount.setFieldName("amount");
        ModelField productSk = new ModelField();
        productSk.setFieldName("product_sk");
        productSk.setDimensionModelId("3003");
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("3101")).thenReturn(List.of(amount, productSk));

        ModelField productPk = new ModelField();
        productPk.setFieldName("product_sk");
        productPk.setIsPrimaryKey(true);
        when(modelFieldRepository.findByModelIdOrderBySortOrderAsc("3003")).thenReturn(List.of(productPk));

        RoleDataScope scope = new RoleDataScope();
        scope.setRoleName("ROLE_REGION");
        scope.setDimensionModelId("3003");
        scope.setEnabled(true);
        scope.setFilterConfig(
            "{\"conditions\":[{\"type\":\"DIMENSION\",\"dimensionFieldName\":\"category_l1\",\"operator\":\"EQ\",\"value\":\"电子产品\"}]}"
        );
        when(roleDataScopeRepository.findByRoleNameInAndEnabledTrue(argThat(roles -> roles != null && roles.contains("ROLE_REGION"))))
            .thenReturn(List.of(scope));
        when(roleDataScopeRepository.findByRoleNameInAndEnabledTrue(argThat(roles -> roles == null || !roles.contains("ROLE_REGION"))))
            .thenReturn(List.of());
    }

    @Test
    @WithMockUser(username = "region", roles = { "REGION" })
    void shouldApplyRoleDataScopeInMetricQueryApi() throws Exception {
        mvc
            .perform(
                post("/api/metrics/query-sql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"metricCodes\":[\"sales_amount\"]}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sql", containsString("d0.category_l1 = '电子产品'")))
            .andExpect(jsonPath("$.sql", containsString("FROM main.fact_sales_order_item f")));
    }

    @Test
    @WithMockUser(username = "region", roles = { "REGION" })
    void shouldEnforceRoleDataScopeWhenExecutingMetricQueryApi() throws Exception {
        mvc
            .perform(post("/api/metrics/query").contentType(MediaType.APPLICATION_JSON).content("{\"metricCodes\":[\"sales_amount\"]}"))
            .andExpect(status().isOk());

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(dataSourceQueryService).executeQuery(any(), sqlCaptor.capture());
        assertThat(sqlCaptor.getValue()).contains("d0.category_l1 = '电子产品'");
    }

    @Test
    @WithMockUser(username = "admin", roles = { "ADMIN" })
    void shouldBypassRoleDataScopeForAdminInMetricQueryApi() throws Exception {
        mvc
            .perform(
                post("/api/metrics/query-sql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"metricCodes\":[\"sales_amount\"]}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sql", not(containsString("d0.category_l1"))));
    }

    @Test
    @WithMockUser(username = "plain", roles = { "USER" })
    void shouldNotRestrictUserWithoutScopeInMetricQueryApi() throws Exception {
        mvc
            .perform(
                post("/api/metrics/query-sql")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"metricCodes\":[\"sales_amount\"]}")
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sql", not(containsString("d0.category_l1"))));
    }
}
