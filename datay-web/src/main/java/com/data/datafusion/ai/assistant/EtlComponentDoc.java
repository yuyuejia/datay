package com.data.datafusion.ai.assistant;

import java.util.List;
import java.util.Map;

/**
 * 解析后的 ETL 组件帮助文档。
 *
 * <p>由 {@link MarkdownDocParser} 从 {@code docs/help/components/*.md} 中提取，
 * 供 AI 组件说明工具与任务定义校验器共用。文档内容只作为只读元数据，
 * 不会参与任务执行。
 */
public record EtlComponentDoc(
    String type,
    String title,
    String overview,
    List<String> features,
    List<ConfigTable> configTables,
    List<ConfigTable> designerConfigTables,
    List<String> designerNotes,
    List<CodeExample> examples,
    List<String> notes,
    String docExcerpt,
    boolean truncated,
    String rawMarkdown
) {
    /**
     * Markdown 中的一张配置表格。
     *
     * @param section 所属章节标题，如「必需参数」「joins 元素说明」
     * @param headers 表头
     * @param rows    表格行，key 为表头列名
     */
    public record ConfigTable(String section, List<String> headers, List<Map<String, String>> rows) {}

    /**
     * 文档中的示例代码块。
     */
    public record CodeExample(String language, String content) {}
}
