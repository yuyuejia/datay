package com.data.datafusion.mcp.harness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * {@link DatayHarnessToolRegistry} 的单元测试：注册、查找、重名拒绝与目录分组。
 */
class DatayHarnessToolRegistryTest {

    @Test
    void shouldIndexToolsByName() {
        DatayHarnessToolRegistry registry = new DatayHarnessToolRegistry(
            List.of(tool("datasource_list", false, Set.of()), tool("datasource_create", true, Set.of("ROLE_ADMIN")))
        );

        assertThat(registry.size()).isEqualTo(2);
        assertThat(registry.find("datasource_list")).isPresent();
        assertThat(registry.find("datasource_create").orElseThrow().mutating()).isTrue();
        assertThat(registry.find("unknown")).isEmpty();
        assertThat(registry.find(null)).isEmpty();
    }

    @Test
    void shouldRejectDuplicateToolNames() {
        assertThatThrownBy(() ->
            new DatayHarnessToolRegistry(List.of(tool("datasource_list", false, Set.of()), tool("datasource_list", false, Set.of())))
        )
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Duplicate harness tool name");
    }

    @Test
    void shouldSkipToolsWithBlankNames() {
        DatayHarnessTool blank = new DatayHarnessTool() {
            @Override
            public String name() {
                return "  ";
            }

            @Override
            public String description() {
                return "blank";
            }

            @Override
            public Map<String, Object> parametersSchema() {
                return Map.of("type", "object");
            }

            @Override
            public Object execute(Map<String, Object> arguments, DatayHarnessContext context) {
                return null;
            }
        };
        DatayHarnessToolRegistry registry = new DatayHarnessToolRegistry(List.of(blank, tool("etl_task_list", false, Set.of())));

        assertThat(registry.size()).isEqualTo(1);
        assertThat(registry.find("etl_task_list")).isPresent();
    }

    @Test
    void shouldGroupCatalogByToolNamePrefix() {
        DatayHarnessToolRegistry registry = new DatayHarnessToolRegistry(
            List.of(
                tool("datasource_list", false, Set.of()),
                tool("datasource_create", true, Set.of("ROLE_ADMIN")),
                tool("etl_task_run", true, Set.of())
            )
        );

        Map<String, List<Map<String, Object>>> catalog = registry.catalog();

        assertThat(catalog).containsOnlyKeys("datasource", "etl");
        assertThat(catalog.get("datasource")).hasSize(2);
        assertThat(catalog.get("etl")).hasSize(1);

        Map<String, Object> createEntry = catalog
            .get("datasource")
            .stream()
            .filter(item -> "datasource_create".equals(item.get("name")))
            .findFirst()
            .orElseThrow();
        assertThat(createEntry.get("mutating")).isEqualTo(true);
        assertThat(createEntry.get("requiredAuthorities")).isEqualTo(List.of("ROLE_ADMIN"));

        Map<String, Object> listEntry = catalog
            .get("datasource")
            .stream()
            .filter(item -> "datasource_list".equals(item.get("name")))
            .findFirst()
            .orElseThrow();
        assertThat(listEntry).doesNotContainKey("requiredAuthorities");
    }

    @Test
    void shouldHonourExplicitGroupOverNamePrefix() {
        DatayHarnessTool registryList = DatayHarnessTool.of(
            "list_metrics",
            "metric",
            "指标清单",
            Map.of("type", "object"),
            false,
            Set.of(),
            (args, context) -> null
        );
        DatayHarnessToolRegistry registry = new DatayHarnessToolRegistry(List.of(registryList));

        assertThat(registryList.group()).isEqualTo("metric");
        assertThat(registry.catalog()).containsOnlyKeys("metric");
    }

    @Test
    void shouldDeriveGroupFromNamePrefixByDefault() {
        DatayHarnessTool tool = DatayHarnessTool.read("datasource_list", "list", Map.of(), (args, context) -> null);

        assertThat(tool.group()).isEqualTo("datasource");
        assertThat(DatayHarnessTool.read("single", "x", Map.of(), (args, context) -> null).group()).isEqualTo("other");
    }

    private static DatayHarnessTool tool(String name, boolean mutating, Set<String> authorities) {
        return DatayHarnessTool.of(name, name, Map.of("type", "object"), mutating, authorities, (args, context) -> null);
    }
}
