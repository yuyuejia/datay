package com.data.datafusion.ai.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/**
 * {@link MarkdownDocParser} 帮助文档解析测试。
 */
class MarkdownDocParserTest {

    private final MarkdownDocParser parser = new MarkdownDocParser(new ObjectMapper());

    @Test
    void shouldParseOverviewFeaturesTablesExamplesAndNotes() {
        String markdown =
            """
            # StreamJdbcInput 组件配置文档

            ## 组件概述

            从关系型数据库流式读取数据，支持多表同步。

            ## 核心特性

            - 支持多表同步
            - 支持增量同步

            ## 配置参数

            | 参数名 | 类型 | 必填 | 描述 |
            | --- | --- | --- | --- |
            | `table` | String | 是 | 源表名 |
            | `where` | String | 否 | 过滤条件 |

            在 DataY Web 中配置时，数据源使用 sourceId + schema。

            ## 配置示例

            ```json
            {"table":"orders","password":"secret"}
            ```

            ## 注意事项

            - 字符串和日期需要自行加引号。
            """;

        EtlComponentDoc doc = parser.parse("StreamJdbcInput", markdown);

        assertThat(doc.type()).isEqualTo("StreamJdbcInput");
        assertThat(doc.title()).contains("StreamJdbcInput");
        assertThat(doc.overview()).contains("流式读取数据");
        assertThat(doc.features()).containsExactly("支持多表同步", "支持增量同步");
        assertThat(doc.configTables()).hasSize(1);
        assertThat(doc.configTables().get(0).rows()).hasSize(2);
        assertThat(doc.configTables().get(0).rows().get(0)).containsEntry("参数名", "table");
        assertThat(doc.examples()).hasSize(1);
        assertThat(doc.examples().get(0).content()).contains("\"***\"");
        assertThat(doc.examples().get(0).content()).doesNotContain("secret");
        assertThat(doc.designerNotes()).anyMatch(note -> note.contains("DataY Web"));
        assertThat(doc.notes()).anyMatch(note -> note.contains("字符串和日期"));
        assertThat(doc.docExcerpt()).contains("配置参数");
    }

    @Test
    void shouldReturnEmptySectionsForMinimalMarkdown() {
        EtlComponentDoc doc = parser.parse("Unknown", "# Unknown\n\n没有更多内容。\n");

        assertThat(doc.type()).isEqualTo("Unknown");
        assertThat(doc.overview()).contains("没有更多内容");
        assertThat(doc.configTables()).isEmpty();
        assertThat(doc.examples()).isEmpty();
    }
}
