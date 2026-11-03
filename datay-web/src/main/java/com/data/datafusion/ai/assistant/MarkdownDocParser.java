package com.data.datafusion.ai.assistant;

import com.data.datafusion.ai.assistant.EtlComponentDoc.ConfigTable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * ETL 组件帮助文档解析器。
 *
 * <p>帮助文档维护在 {@code docs/help/components/*.md}，内容是面向用户的 Markdown。
 * 本解析器不引入额外 Markdown 依赖，只提取 AI 生成任务配置最需要的部分：
 * 组件概述、核心特性、配置表格、示例代码、注意事项与设计器说明。
 *
 * <p>文档中的示例可能包含示例密码，返回前统一脱敏。
 */
@Component
public class MarkdownDocParser {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");

    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\s*\\|?[\\s:|-]+\\|?\\s*$");

    private static final Pattern SENSITIVE_NAME = Pattern.compile(
        "(?i)(password|passwd|secret|token|api[-_]?key|access[-_]?key|private[-_]?key|authorization)"
    );

    private static final Pattern SENSITIVE_TEXT = Pattern.compile(
        "(?i)([\"']?(?:password|passwd|secret|token|api[-_]?key|access[-_]?key|private[-_]?key)[\"']?\\s*[:=]\\s*)[\"'][^\"']*[\"']"
    );

    private static final int MAX_OVERVIEW_CHARS = 2000;
    private static final int MAX_DOC_EXCERPT_CHARS = 6000;
    private static final int MAX_EXAMPLE_CHARS = 2000;
    private static final int MAX_EXAMPLES = 3;
    private static final int MAX_FEATURES = 20;
    private static final int MAX_NOTES = 8;

    private final ObjectMapper objectMapper;

    public MarkdownDocParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析一篇组件帮助文档。
     *
     * @param type     组件编码，如 StreamJdbcInput
     * @param markdown Markdown 原文
     * @return 解析结果，永不返回 null
     */
    public EtlComponentDoc parse(String type, String markdown) {
        String source = markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n');
        List<Section> sections = splitSections(source);
        String title = findTitle(source, type);
        String overview = extractOverview(sections);
        List<String> features = extractFeatures(sections);
        List<ConfigTable> configTables = extractConfigTables(sections);
        List<ConfigTable> designerConfigTables = extractDesignerConfigTables(sections);
        List<String> designerNotes = extractDesignerNotes(sections);
        List<EtlComponentDoc.CodeExample> examples = extractExamples(sections);
        List<String> notes = extractNotes(sections);
        boolean truncated = source.length() > MAX_DOC_EXCERPT_CHARS;
        String docExcerpt = buildDocExcerpt(title, overview, features, configTables, examples, notes);

        return new EtlComponentDoc(
            type,
            title,
            overview,
            features,
            configTables,
            designerConfigTables,
            designerNotes,
            examples,
            notes,
            docExcerpt,
            truncated,
            source
        );
    }

    // ------------------------------------------------------------------ 章节切分

    private List<Section> splitSections(String markdown) {
        List<Section> sections = new ArrayList<>();
        String currentHeading = "概述";
        int currentLevel = 0;
        List<String> currentLines = new ArrayList<>();
        boolean inFence = false;

        for (String line : markdown.split("\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                inFence = !inFence;
                currentLines.add(line);
                continue;
            }
            if (!inFence) {
                Matcher matcher = HEADING.matcher(line);
                if (matcher.matches()) {
                    int level = matcher.group(1).length();
                    String heading = matcher.group(2).trim();
                    if (level == 1) {
                        // H1 作为文档标题，不作为章节
                        currentHeading = "概述";
                        currentLevel = 0;
                        currentLines = new ArrayList<>();
                        continue;
                    }
                    if (currentLevel > 0 || !currentLines.isEmpty()) {
                        sections.add(new Section(currentLevel, currentHeading, new ArrayList<>(currentLines)));
                    }
                    currentHeading = heading;
                    currentLevel = level;
                    currentLines = new ArrayList<>();
                    continue;
                }
            }
            currentLines.add(line);
        }
        if (currentLevel > 0 || !currentLines.isEmpty()) {
            sections.add(new Section(currentLevel, currentHeading, new ArrayList<>(currentLines)));
        }
        return sections;
    }

    private String findTitle(String markdown, String fallback) {
        for (String line : markdown.split("\n")) {
            Matcher matcher = HEADING.matcher(line);
            if (matcher.matches() && matcher.group(1).length() == 1) {
                return cleanInline(matcher.group(2));
            }
        }
        return fallback;
    }

    // ------------------------------------------------------------------ 各章节提取

    private String extractOverview(List<Section> sections) {
        Section section = firstSection(sections, "组件概述", "组件介绍", "概述", "组件说明");
        if (section != null) {
            String text = joinParagraphs(section);
            if (!text.isBlank()) {
                return truncate(text, MAX_OVERVIEW_CHARS);
            }
        }
        // 没有概述章节时，取第一个非空段落
        for (Section item : sections) {
            for (String line : item.lines()) {
                String text = cleanInline(line);
                if (!text.isBlank() && !text.startsWith("|") && !text.startsWith("```")) {
                    return truncate(text, MAX_OVERVIEW_CHARS);
                }
            }
        }
        return "";
    }

    private List<String> extractFeatures(List<Section> sections) {
        Section section = firstSection(sections, "核心特性", "功能特性", "特性", "核心功能");
        if (section == null) {
            return List.of();
        }
        List<String> items = new ArrayList<>();
        for (String line : section.lines()) {
            String item = bulletText(line);
            if (item != null && !item.isBlank()) {
                items.add(truncate(cleanInline(item), 300));
            }
            if (items.size() >= MAX_FEATURES) {
                break;
            }
        }
        return items;
    }

    private List<ConfigTable> extractConfigTables(List<Section> sections) {
        List<ConfigTable> tables = new ArrayList<>();
        for (Section section : sections) {
            for (ConfigTable table : parseTables(section)) {
                if (isConfigTable(table)) {
                    tables.add(table);
                }
            }
        }
        return tables;
    }

    private List<ConfigTable> extractDesignerConfigTables(List<Section> sections) {
        List<ConfigTable> tables = new ArrayList<>();
        for (Section section : sections) {
            String heading = section.heading().toLowerCase(Locale.ROOT);
            if (!heading.contains("设计器") && !heading.contains("web")) {
                continue;
            }
            for (ConfigTable table : parseTables(section)) {
                if (isConfigTable(table)) {
                    tables.add(table);
                }
            }
        }
        return tables;
    }

    private List<String> extractDesignerNotes(List<Section> sections) {
        List<String> notes = new ArrayList<>();
        for (Section section : sections) {
            for (String line : section.lines()) {
                String text = cleanInline(line);
                if (text.isBlank() || text.startsWith("|") || text.startsWith("```")) {
                    continue;
                }
                String lower = text.toLowerCase(Locale.ROOT);
                if (lower.contains("设计器") || lower.contains("datay web")) {
                    notes.add(truncate(text, 500));
                }
                if (notes.size() >= MAX_NOTES) {
                    return notes;
                }
            }
        }
        return notes;
    }

    private List<EtlComponentDoc.CodeExample> extractExamples(List<Section> sections) {
        List<EtlComponentDoc.CodeExample> preferred = new ArrayList<>();
        List<EtlComponentDoc.CodeExample> fallback = new ArrayList<>();
        for (Section section : sections) {
            boolean inFence = false;
            String language = "";
            StringBuilder content = new StringBuilder();
            for (String line : section.lines()) {
                String trimmed = line.trim();
                if (trimmed.startsWith("```")) {
                    if (!inFence) {
                        inFence = true;
                        language = trimmed.substring(3).trim();
                        content = new StringBuilder();
                    } else {
                        inFence = false;
                        if (!content.isEmpty()) {
                            EtlComponentDoc.CodeExample example = new EtlComponentDoc.CodeExample(
                                language,
                                truncate(sanitizeExample(language, content.toString()), MAX_EXAMPLE_CHARS)
                            );
                            if (section.heading().contains("示例")) {
                                preferred.add(example);
                            } else {
                                fallback.add(example);
                            }
                        }
                    }
                    continue;
                }
                if (inFence) {
                    content.append(line).append('\n');
                }
            }
        }
        List<EtlComponentDoc.CodeExample> result = new ArrayList<>();
        for (EtlComponentDoc.CodeExample example : preferred) {
            if (result.size() >= MAX_EXAMPLES) {
                break;
            }
            result.add(example);
        }
        for (EtlComponentDoc.CodeExample example : fallback) {
            if (result.size() >= MAX_EXAMPLES) {
                break;
            }
            if (!result.contains(example)) {
                result.add(example);
            }
        }
        return result;
    }

    private List<String> extractNotes(List<Section> sections) {
        List<String> notes = new ArrayList<>();
        for (Section section : sections) {
            String heading = section.heading().toLowerCase(Locale.ROOT);
            if (
                !heading.contains("注意") &&
                !heading.contains("约束") &&
                !heading.contains("限制") &&
                !heading.contains("使用说明")
            ) {
                continue;
            }
            for (String line : section.lines()) {
                String text = cleanInline(line);
                if (text.isBlank() || text.startsWith("|") || text.startsWith("```")) {
                    continue;
                }
                String bullet = bulletText(line);
                notes.add(truncate(bullet == null ? text : cleanInline(bullet), 500));
                if (notes.size() >= MAX_NOTES) {
                    return notes;
                }
            }
        }
        return notes;
    }

    // ------------------------------------------------------------------ 表格与文本工具

    private List<ConfigTable> parseTables(Section section) {
        List<ConfigTable> tables = new ArrayList<>();
        List<String> lines = section.lines();
        int index = 0;
        while (index < lines.size()) {
            if (!lines.get(index).trim().startsWith("|")) {
                index++;
                continue;
            }
            List<String> block = new ArrayList<>();
            while (index < lines.size() && lines.get(index).trim().startsWith("|")) {
                block.add(lines.get(index));
                index++;
            }
            if (block.size() < 3) {
                continue;
            }
            List<String> headers = splitTableRow(block.get(0));
            if (headers.isEmpty() || !TABLE_SEPARATOR.matcher(block.get(1)).matches()) {
                continue;
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (int rowIndex = 2; rowIndex < block.size(); rowIndex++) {
                List<String> cells = splitTableRow(block.get(rowIndex));
                if (cells.isEmpty()) {
                    continue;
                }
                Map<String, String> row = new LinkedHashMap<>();
                for (int cellIndex = 0; cellIndex < headers.size(); cellIndex++) {
                    row.put(headers.get(cellIndex), cellIndex < cells.size() ? cells.get(cellIndex) : "");
                }
                rows.add(row);
            }
            if (!rows.isEmpty()) {
                tables.add(new ConfigTable(section.heading(), List.copyOf(headers), rows));
            }
        }
        return tables;
    }

    private List<String> splitTableRow(String line) {
        String text = line.trim();
        if (text.startsWith("|")) {
            text = text.substring(1);
        }
        if (text.endsWith("|")) {
            text = text.substring(0, text.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;
        for (char ch : text.toCharArray()) {
            if (escaped) {
                current.append(ch);
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                current.append(ch);
                continue;
            }
            if (ch == '|') {
                cells.add(cleanInline(current.toString()));
                current = new StringBuilder();
                continue;
            }
            current.append(ch);
        }
        cells.add(cleanInline(current.toString()));
        return cells;
    }

    private boolean isConfigTable(ConfigTable table) {
        String headerText = String.join(" ", table.headers()).toLowerCase(Locale.ROOT);
        boolean hasField = headerText.contains("参数") || headerText.contains("字段");
        boolean hasDesc = headerText.contains("描述") || headerText.contains("说明") || headerText.contains("类型");
        return hasField && hasDesc;
    }

    private Section firstSection(List<Section> sections, String... keywords) {
        for (Section section : sections) {
            String heading = section.heading().toLowerCase(Locale.ROOT);
            for (String keyword : keywords) {
                if (heading.contains(keyword.toLowerCase(Locale.ROOT))) {
                    return section;
                }
            }
        }
        return null;
    }

    private String joinParagraphs(Section section) {
        StringBuilder sb = new StringBuilder();
        for (String line : section.lines()) {
            String text = cleanInline(line);
            if (text.isBlank() || text.startsWith("|") || text.startsWith("```")) {
                continue;
            }
            if (text.startsWith("- ") || text.startsWith("* ")) {
                text = text.substring(2).trim();
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(text);
        }
        return sb.toString().trim();
    }

    private String bulletText(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            return trimmed.substring(2).trim();
        }
        Matcher matcher = Pattern.compile("^\\d+\\.\\s+(.*)$").matcher(trimmed);
        if (matcher.matches()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String cleanInline(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("`", "").replace("**", "").replace("__", "").trim();
    }

    private String sanitizeExample(String language, String content) {
        String text = content == null ? "" : content.trim();
        if ("json".equalsIgnoreCase(language)) {
            try {
                JsonNode node = objectMapper.readTree(text);
                sanitizeNode(node);
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            } catch (Exception ignored) {
                // 模型可能输出带注释或尾逗号的 JSON，退回文本脱敏
            }
        }
        return SENSITIVE_TEXT.matcher(text).replaceAll("$1\"***\"");
    }

    private void sanitizeNode(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                if (SENSITIVE_NAME.matcher(name).find()) {
                    object.put(name, "***");
                } else {
                    sanitizeNode(object.get(name));
                }
            }
        } else if (node.isArray()) {
            ArrayNode array = (ArrayNode) node;
            array.forEach(this::sanitizeNode);
        }
    }

    private String buildDocExcerpt(
        String title,
        String overview,
        List<String> features,
        List<ConfigTable> configTables,
        List<EtlComponentDoc.CodeExample> examples,
        List<String> notes
    ) {
        StringBuilder sb = new StringBuilder();
        if (title != null && !title.isBlank()) {
            sb.append("# ").append(title).append("\n\n");
        }
        if (overview != null && !overview.isBlank()) {
            sb.append("## 组件概述\n").append(overview).append("\n\n");
        }
        if (!features.isEmpty()) {
            sb.append("## 核心特性\n");
            features.forEach(item -> sb.append("- ").append(item).append('\n'));
            sb.append('\n');
        }
        if (!configTables.isEmpty()) {
            sb.append("## 配置参数\n");
            for (ConfigTable table : configTables) {
                sb.append("### ").append(table.section()).append('\n');
                sb.append(String.join(" | ", table.headers())).append('\n');
                for (Map<String, String> row : table.rows()) {
                    sb.append(String.join(" | ", table.headers().stream().map(header -> row.getOrDefault(header, "")).toList()));
                    sb.append('\n');
                }
                sb.append('\n');
            }
        }
        if (!examples.isEmpty()) {
            sb.append("## 配置示例\n");
            examples.stream().limit(2).forEach(example -> {
                sb.append("```").append(example.language()).append('\n');
                sb.append(example.content()).append('\n');
                sb.append("```\n\n");
            });
        }
        if (!notes.isEmpty()) {
            sb.append("## 注意事项\n");
            notes.forEach(item -> sb.append("- ").append(item).append('\n'));
        }
        return truncate(sb.toString(), MAX_DOC_EXCERPT_CHARS);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "\n...(已截断)";
    }

    private record Section(int level, String heading, List<String> lines) {}
}
