package com.data.datafusion.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * {@link EtlComponentDocRegistry} classpath 文档加载测试。
 */
class EtlComponentDocRegistryTest {

    @Test
    void shouldLoadComponentDocsFromClasspath() {
        EtlComponentDocRegistry registry = new EtlComponentDocRegistry(new MarkdownDocParser(new ObjectMapper()));

        registry.load();

        assertThat(registry.all()).isNotEmpty();
        assertThat(registry.find("Join")).isPresent();
        assertThat(registry.find("join")).isPresent();
        EtlComponentDoc join = registry.find("Join").orElseThrow();
        assertThat(join.overview()).isNotBlank();
        assertThat(join.configTables()).isNotEmpty();
        assertThat(join.docExcerpt()).contains("配置参数");
        assertThat(registry.find("StreamJdbcInput")).isPresent();
        assertThat(registry.find("NotExists")).isEmpty();
    }
}
