package com.data.datafusion.service.datamap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.DataSource;
import com.data.datafusion.domain.Metric;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.dto.datamap.DataMapGraphDTO;
import com.data.datafusion.service.dto.datamap.DataMapOverviewDTO;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 验证数据地图模块的 Spring 装配与只读查询入口。
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
@TestPropertySource(
    properties = {
        "datay.data-map.auto-build=false",
        "datay.data-map.db-file=target/test-data-map/data-map-context.duckdb",
    }
)
class DataMapContextIT {

    @Autowired
    private DataMapQueryService queryService;

    @Autowired
    private DataMapIndexService indexService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0L);
        TenantContext.setTenantCode("default");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldLoadDataMapContext() {
        DataMapOverviewDTO overview = queryService.overview();
        assertThat(overview).isNotNull();
        assertThat(overview.indexStatus()).isNotNull();
        assertThat(queryService.types()).containsKey("TABLE");
    }

    @Test
    void shouldRebuildIndexWithRealRepositories() {
        indexService.rebuild("0", "default");
        assertThat(indexService.status("0").status()).isEqualTo(DataMapStore.Status.READY);
    }

    @Test
    @Transactional
    void shouldBuildNodesAndEdgesFromPlatformEntities() {
        DataSource dataSource = new DataSource();
        dataSource.setName("IT 订单库");
        dataSource.setType("duckdb");
        dataSource.setSchemaName("main");
        dataSource.setTenantId("0");
        entityManager.persist(dataSource);

        DataModel model = new DataModel();
        model.setName("IT 订单模型");
        model.setCode("it_fact_order");
        model.setModelType("FACT");
        model.setDataSourceId(dataSource.getId());
        model.setSchemaName("main");
        model.setTableName("orders");
        model.setTenantId("0");
        entityManager.persist(model);

        ModelField field = new ModelField();
        field.setModelId(model.getId());
        field.setFieldName("order_id");
        field.setFieldType("VARCHAR");
        field.setTenantId("0");
        entityManager.persist(field);

        Metric metric = new Metric();
        metric.setName("IT 订单金额");
        metric.setCode("it_order_amount");
        metric.setMetricType(Metric.TYPE_ATOMIC);
        metric.setFactModelId(model.getId());
        metric.setTenantId("0");
        entityManager.persist(metric);
        entityManager.flush();

        indexService.rebuild("0", "default");
        DataMapGraphDTO graph = queryService.graph("full", null, "both", null, null, null, true, null);
        assertThat(graph.nodes()).extracting(node -> node.id()).contains(
            "source:" + dataSource.getId(),
            "model:" + model.getId(),
            "metric:" + metric.getId()
        );
        assertThat(graph.links()).extracting(edge -> edge.relation()).contains("MAPS_TO", "CONTAINS", "COMPUTES");
    }

    @Test
    void shouldExposeDataMapEndpoints() throws Exception {
        mockMvc.perform(get("/api/data-map/types").with(this::withTenant)).andExpect(status().isOk()).andExpect(jsonPath("$.TABLE").value("物理表"));
        mockMvc
            .perform(get("/api/data-map/overview").with(this::withTenant))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true));
        mockMvc
            .perform(get("/api/data-map/graph").with(this::withTenant))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scope").value("full"));
        mockMvc.perform(get("/api/data-map/search").param("q", "订单").with(this::withTenant)).andExpect(status().isOk());
    }

    private org.springframework.mock.web.MockHttpServletRequest withTenant(org.springframework.mock.web.MockHttpServletRequest request) {
        TenantContext.setTenantId(0L);
        TenantContext.setTenantCode("default");
        return request;
    }
}
