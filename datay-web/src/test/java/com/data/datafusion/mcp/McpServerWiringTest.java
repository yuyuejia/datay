package com.data.datafusion.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.ai.tool.AiTool;
import com.data.datafusion.ai.tool.AiToolContext;
import com.data.datafusion.ai.tool.AiToolRegistry;
import com.data.datafusion.repository.TenantRepository;
import com.data.datafusion.repository.UserRepository;
import com.data.datafusion.service.DataSourceQueryService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.DataSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 校验 {@code /mcp/datasource} 与 {@code /mcp/metric} 两个 MCP 服务在同一容器内可同时装配，
 * 即同名类型的 transport provider / sync server 不产生 bean 歧义。
 */
class McpServerWiringTest {

    private final AiToolRegistry toolRegistry = mock(AiToolRegistry.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(DatasourceMcpServerConfig.class, MetricMcpServerConfig.class)
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withBean(McpTokenService.class, () -> mock(McpTokenService.class))
        .withBean(TenantRepository.class, () -> mock(TenantRepository.class))
        .withBean(UserRepository.class, () -> mock(UserRepository.class))
        .withBean(DataSourceService.class, () -> mock(DataSourceService.class))
        .withBean(DataSourceQueryService.class, () -> mock(DataSourceQueryService.class))
        .withBean(DataSyncService.class, () -> mock(DataSyncService.class))
        .withBean(AiToolRegistry.class, () -> toolRegistry)
        .withBean(MetricMcpToolExecutor.class, () ->
            new MetricMcpToolExecutor(
                toolRegistry,
                mock(McpTokenService.class),
                mock(TenantRepository.class),
                mock(UserRepository.class),
                new ObjectMapper()
            )
        );

    @Test
    void shouldRegisterBothMcpEndpointsWithoutAmbiguity() {
        when(toolRegistry.find(anyString())).thenAnswer(invocation -> Optional.of(stubTool(invocation.getArgument(0))));

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("datasourceMcpTransportProvider");
            assertThat(context).hasBean("metricMcpTransportProvider");
            assertThat(context).hasBean("datasourceMcpSyncServer");
            assertThat(context).hasBean("metricMcpSyncServer");
        });
    }

    private static AiTool stubTool(String name) {
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
}
