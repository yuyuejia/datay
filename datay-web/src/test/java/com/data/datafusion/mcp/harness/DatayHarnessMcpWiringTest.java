package com.data.datafusion.mcp.harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.ai.rag.MetricRagProperties;
import com.data.datafusion.ai.rag.MetricRagService;
import com.data.datafusion.ai.tool.AiTool;
import com.data.datafusion.ai.tool.AiToolContext;
import com.data.datafusion.ai.tool.AiToolRegistry;
import com.data.datafusion.ai.tool.MetricCatalogTool;
import com.data.datafusion.ai.tool.MetricDimensionValueTool;
import com.data.datafusion.ai.tool.MetricMetaTool;
import com.data.datafusion.ai.tool.MetricQueryDataTool;
import com.data.datafusion.ai.tool.MetricQueryGuideTool;
import com.data.datafusion.ai.tool.MetricRagSearchTool;
import com.data.datafusion.domain.Authority;
import com.data.datafusion.domain.Tenant;
import com.data.datafusion.domain.User;
import com.data.datafusion.mcp.DatasourceMcpServerConfig;
import com.data.datafusion.mcp.McpTokenService;
import com.data.datafusion.mcp.MetricMcpServerConfig;
import com.data.datafusion.mcp.MetricMcpToolExecutor;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.TenantRepository;
import com.data.datafusion.repository.UserRepository;
import com.data.datafusion.service.DataModelMaterializeService;
import com.data.datafusion.service.DataModelService;
import com.data.datafusion.service.DataSourceQueryService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.DataSyncService;
import com.data.datafusion.service.DefaultWarehouseConfigService;
import com.data.datafusion.service.DimensionValueService;
import com.data.datafusion.service.ETLTaskService;
import com.data.datafusion.service.JobDependService;
import com.data.datafusion.service.JobInstanceService;
import com.data.datafusion.service.MetricQueryService;
import com.data.datafusion.service.MetricDirectoryService;
import com.data.datafusion.service.MetricService;
import com.data.datafusion.service.MetricSqlService;
import com.data.datafusion.service.ModelDirectoryService;
import com.data.datafusion.service.ModelFieldService;
import com.data.datafusion.service.TimeDimensionDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Harness 装配测试：
 * <ol>
 *     <li>三套 MCP 端点（datasource / metric / datay）可在同一容器内共存，不产生 bean 歧义；</li>
 *     <li>三个工具族 + 元工具全部注册成功，且工具清单与预期完全一致（等于变相校验工具名无重复）。</li>
 * </ol>
 */
class DatayHarnessMcpWiringTest {

    @Test
    void shouldRegisterHarnessEndpointAlongsideExistingMcpEndpoints() {
        AiToolRegistry aiToolRegistry = mock(AiToolRegistry.class);
        when(aiToolRegistry.find(anyString())).thenAnswer(invocation -> Optional.of(stubAiTool(invocation.getArgument(0))));

        new ApplicationContextRunner()
            .withUserConfiguration(DatasourceMcpServerConfig.class, MetricMcpServerConfig.class, DatayHarnessMcpServerConfig.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(McpTokenService.class, () -> mock(McpTokenService.class))
            .withBean(TenantRepository.class, () -> mock(TenantRepository.class))
            .withBean(UserRepository.class, () -> mock(UserRepository.class))
            .withBean(DataSourceService.class, () -> mock(DataSourceService.class))
            .withBean(DataSourceQueryService.class, () -> mock(DataSourceQueryService.class))
            .withBean(DataSyncService.class, () -> mock(DataSyncService.class))
            .withBean(AiToolRegistry.class, () -> aiToolRegistry)
            .withBean(MetricMcpToolExecutor.class, () ->
                new MetricMcpToolExecutor(
                    aiToolRegistry,
                    mock(McpTokenService.class),
                    mock(TenantRepository.class),
                    mock(UserRepository.class),
                    new ObjectMapper()
                )
            )
            .withBean(DatayHarnessToolRegistry.class, () -> new DatayHarnessToolRegistry(List.of()))
            .withBean(DatayHarnessExecutor.class, () -> mock(DatayHarnessExecutor.class))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context).hasBean("datasourceMcpTransportProvider");
                assertThat(context).hasBean("metricMcpTransportProvider");
                assertThat(context).hasBean("datayHarnessMcpTransportProvider");
                assertThat(context).hasBean("datasourceMcpSyncServer");
                assertThat(context).hasBean("metricMcpSyncServer");
                assertThat(context).hasBean("datayHarnessMcpSyncServer");
            });
    }

    @Test
    void shouldRegisterEveryDeclaredHarnessTool() {
        new ApplicationContextRunner()
            .withUserConfiguration(
                DatayHarnessToolRegistry.class,
                DatayHarnessMetaTools.class,
                DataSourceHarnessTools.class,
                EtlTaskHarnessTools.class,
                DataModelHarnessTools.class,
                MetricHarnessTools.class,
                MetricAdminHarnessTools.class
            )
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(MetricSqlService.class, () -> mock(MetricSqlService.class))
            .withBean(MetricDirectoryService.class, () -> mock(MetricDirectoryService.class))
            .withBean(DataSourceService.class, () -> mock(DataSourceService.class))
            .withBean(DataSourceQueryService.class, () -> mock(DataSourceQueryService.class))
            .withBean(DefaultWarehouseConfigService.class, () -> mock(DefaultWarehouseConfigService.class))
            .withBean(ETLTaskService.class, () -> mock(ETLTaskService.class))
            .withBean(JobInstanceService.class, () -> mock(JobInstanceService.class))
            .withBean(JobDependService.class, () -> mock(JobDependService.class))
            .withBean(ETLTaskRepository.class, () -> mock(ETLTaskRepository.class))
            .withBean(DataModelService.class, () -> mock(DataModelService.class))
            .withBean(ModelFieldService.class, () -> mock(ModelFieldService.class))
            .withBean(ModelDirectoryService.class, () -> mock(ModelDirectoryService.class))
            .withBean(DimensionValueService.class, () -> mock(DimensionValueService.class))
            .withBean(DataModelMaterializeService.class, () -> mock(DataModelMaterializeService.class))
            .withBean(TimeDimensionDataService.class, () -> mock(TimeDimensionDataService.class))
            .withBean(MetricRagService.class, () -> mock(MetricRagService.class))
            .withBean(MetricRagProperties.class, MetricRagProperties::new)
            .withBean(MetricService.class, () -> mock(MetricService.class))
            .withBean(MetricQueryService.class, () -> mock(MetricQueryService.class))
            .withBean(DataModelRepository.class, () -> mock(DataModelRepository.class))
            .withBean(ModelFieldRepository.class, () -> mock(ModelFieldRepository.class))
            .withBean(MetricQueryGuideTool.class, MetricQueryGuideTool::new)
            .withBean(MetricRagSearchTool.class, () ->
                new MetricRagSearchTool(mock(MetricRagService.class), new MetricRagProperties())
            )
            .withBean(MetricCatalogTool.class, () -> new MetricCatalogTool(mock(MetricService.class)))
            .withBean(MetricMetaTool.class, () -> new MetricMetaTool(mock(MetricQueryService.class)))
            .withBean(MetricDimensionValueTool.class, () ->
                new MetricDimensionValueTool(
                    mock(DataModelRepository.class),
                    mock(ModelFieldRepository.class),
                    mock(DataSourceService.class),
                    mock(DataSourceQueryService.class)
                )
            )
            .withBean(MetricQueryDataTool.class, () -> new MetricQueryDataTool(mock(MetricQueryService.class), new ObjectMapper()))
            .run(context -> {
                assertThat(context).hasNotFailed();
                DatayHarnessToolRegistry registry = context.getBean(DatayHarnessToolRegistry.class);
                assertThat(registry.all().stream().map(DatayHarnessTool::name).collect(Collectors.toSet()))
                    .containsExactlyInAnyOrderElementsOf(expectedToolNames());
                registry.all().forEach(tool -> assertThat(tool.parametersSchema()).containsEntry("type", "object"));
            });
    }

    @Test
    void shouldExposeMetricToolsAsReadOnlyAndReuseAiToolDefinition() {
        new ApplicationContextRunner()
            .withUserConfiguration(DatayHarnessToolRegistry.class, MetricHarnessTools.class, AiToolRegistry.class)
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean("genericProbeTool", AiTool.class, () -> stubAiTool("generic_probe"))
            .withBean(MetricRagService.class, () -> mock(MetricRagService.class))
            .withBean(MetricRagProperties.class, MetricRagProperties::new)
            .withBean(MetricService.class, () -> mock(MetricService.class))
            .withBean(MetricQueryService.class, () -> mock(MetricQueryService.class))
            .withBean(DataModelRepository.class, () -> mock(DataModelRepository.class))
            .withBean(ModelFieldRepository.class, () -> mock(ModelFieldRepository.class))
            .withBean(DataSourceService.class, () -> mock(DataSourceService.class))
            .withBean(DataSourceQueryService.class, () -> mock(DataSourceQueryService.class))
            .withBean(MetricQueryGuideTool.class, MetricQueryGuideTool::new)
            .withBean(MetricRagSearchTool.class, () ->
                new MetricRagSearchTool(mock(MetricRagService.class), new MetricRagProperties())
            )
            .withBean(MetricCatalogTool.class, () -> new MetricCatalogTool(mock(MetricService.class)))
            .withBean(MetricMetaTool.class, () -> new MetricMetaTool(mock(MetricQueryService.class)))
            .withBean(MetricDimensionValueTool.class, () ->
                new MetricDimensionValueTool(
                    mock(DataModelRepository.class),
                    mock(ModelFieldRepository.class),
                    mock(DataSourceService.class),
                    mock(DataSourceQueryService.class)
                )
            )
            .withBean(MetricQueryDataTool.class, () -> new MetricQueryDataTool(mock(MetricQueryService.class), new ObjectMapper()))
            .run(context -> {
                assertThat(context).hasNotFailed();
                DatayHarnessToolRegistry harness = context.getBean(DatayHarnessToolRegistry.class);
                assertThat(harness.size()).isEqualTo(MetricHarnessTools.METRIC_TOOL_NAMES.size());
                for (String name : MetricHarnessTools.METRIC_TOOL_NAMES) {
                    DatayHarnessTool tool = harness.find(name).orElseThrow();
                    assertThat(tool.group()).isEqualTo(MetricHarnessTools.GROUP);
                    assertThat(tool.mutating()).as("%s 应为只读", name).isFalse();
                    assertThat(tool.requiredAuthorities()).isEmpty();
                    assertThat(tool.parametersSchema()).containsEntry("type", "object");
                }
                assertThat(harness.catalog()).containsOnlyKeys(MetricHarnessTools.GROUP);

                // 说明与入参 schema 必须逐字沿用平台实现，避免 harness 里出现第二套提示词
                AiTool catalogAiTool = context.getBean(MetricCatalogTool.class);
                DatayHarnessTool catalogHarnessTool = harness.find(MetricCatalogTool.NAME).orElseThrow();
                assertThat(catalogHarnessTool.description()).isEqualTo("【指标智能问数】\n" + catalogAiTool.description());
                assertThat(catalogHarnessTool.parametersSchema()).isEqualTo(catalogAiTool.parametersSchema());

                AiTool queryAiTool = context.getBean(MetricQueryDataTool.class);
                DatayHarnessTool queryHarnessTool = harness.find(MetricQueryDataTool.NAME).orElseThrow();
                assertThat(queryHarnessTool.description()).isEqualTo("【指标智能问数】\n" + queryAiTool.description());
                assertThat(queryHarnessTool.parametersSchema()).isEqualTo(queryAiTool.parametersSchema());

                // 工具行为委托给原实现：stub 返回什么，harness 就回传什么
                assertThat(catalogHarnessTool.execute(Map.of(), new DatayHarnessContext(null, 1L, "t1"))).isNotNull();

                // 回归防线：harness 的 @Bean 不能与 AiTool 组件同名，
                // 否则 spring.main.allow-bean-definition-overriding=true 会静默顶替掉 AiTool 定义，
                // 让内置问数助手悄悄少掉这些工具。
                AiToolRegistry aiTools = context.getBean(AiToolRegistry.class);
                for (String name : MetricHarnessTools.METRIC_TOOL_NAMES) {
                    assertThat(aiTools.find(name)).as("AiTool %s 不应被 harness 的 @Bean 顶替", name).isPresent();
                }
                assertThat(aiTools.find("generic_probe")).isPresent();
            });
    }

    @Test
    void shouldReadMutatingSwitchFromConfigurationProperty() {
        DatayHarnessTool mutatingTool = DatayHarnessTool.write(
            "datasource_create",
            "create",
            Map.of("type", "object"),
            (args, context) -> Map.of()
        );

        McpTokenService mcpTokenService = mock(McpTokenService.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        UserRepository userRepository = mock(UserRepository.class);

        User user = new User();
        user.setId(1L);
        user.setLogin("alice");
        when(mcpTokenService.authenticateUser("mcp_1_x")).thenReturn(Optional.of(user));

        Tenant tenant = new Tenant();
        tenant.setId(2L);
        tenant.setCode("t2");
        when(tenantRepository.findDefaultTenantByUserId(1L)).thenReturn(Optional.of(tenant));

        User withAuthorities = new User();
        withAuthorities.setId(1L);
        withAuthorities.setLogin("alice");
        withAuthorities.setPassword("$2a$10$testpasswordhash");
        withAuthorities.getAuthorities().add(new Authority().name("ROLE_USER"));
        when(userRepository.findOneWithAuthoritiesById(1L)).thenReturn(Optional.of(withAuthorities));

        new ApplicationContextRunner()
            .withUserConfiguration(DatayHarnessExecutor.class)
            .withPropertyValues("datay.mcp.harness.allow-mutating-tools=false")
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(DatayHarnessToolRegistry.class, () -> new DatayHarnessToolRegistry(List.of(mutatingTool)))
            .withBean(McpTokenService.class, () -> mcpTokenService)
            .withBean(TenantRepository.class, () -> tenantRepository)
            .withBean(UserRepository.class, () -> userRepository)
            .run(context -> {
                assertThat(context).hasNotFailed();
                CallToolResult result = context
                    .getBean(DatayHarnessExecutor.class)
                    .invoke("datasource_create", McpTransportContext.create(Map.of("Authorization", "mcp_1_x")), Map.of());
                assertThat(result.isError()).isTrue();
                assertThat(text(result)).contains("写操作已关闭");
            });
    }

    private static Set<String> expectedToolNames() {
        return Set.of(
            "datasource_list",
            "datasource_get",
            "datasource_create",
            "datasource_update",
            "datasource_delete",
            "datasource_test_connection",
            "datasource_schemas",
            "datasource_tables",
            "datasource_columns",
            "datasource_query",
            "datasource_default_warehouse",
            "datasource_set_default_warehouse",
            "etl_task_list",
            "etl_task_get",
            "etl_task_create",
            "etl_task_update",
            "etl_task_delete",
            "etl_task_run",
            "etl_task_online",
            "etl_task_offline",
            "etl_task_debug",
            "etl_task_instances",
            "etl_task_job_preview",
            "data_model_list",
            "data_model_get",
            "data_model_create",
            "data_model_update",
            "data_model_delete",
            "data_model_fields",
            "data_model_save_fields",
            "data_model_dimension_values",
            "data_model_logical_types",
            "data_model_materialize_types",
            "data_model_materialize_fields",
            "data_model_materialize_check",
            "data_model_materialize_ddl",
            "data_model_materialize",
            "data_model_directory_list",
            "data_model_directory_create",
            "datay_harness_catalog",
            MetricQueryGuideTool.NAME,
            MetricRagSearchTool.NAME,
            MetricCatalogTool.NAME,
            MetricMetaTool.NAME,
            MetricDimensionValueTool.NAME,
            MetricQueryDataTool.NAME,
            "metric_list",
            "metric_get",
            "metric_create",
            "metric_update",
            "metric_delete",
            "metric_preview_sql",
            "metric_directory_list",
            "metric_directory_create",
            "metric_directory_update",
            "metric_directory_delete"
        );
    }

    private static AiTool stubAiTool(String name) {
        return new AiTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return name;
            }

            @Override
            public Map<String, Object> parametersSchema() {
                return Map.of("type", "object");
            }

            @Override
            public Object execute(Map<String, Object> arguments, AiToolContext context) {
                return Map.of();
            }
        };
    }

    private static String text(CallToolResult result) {
        return result
            .content()
            .stream()
            .filter(TextContent.class::isInstance)
            .map(TextContent.class::cast)
            .map(TextContent::text)
            .collect(Collectors.joining());
    }
}
