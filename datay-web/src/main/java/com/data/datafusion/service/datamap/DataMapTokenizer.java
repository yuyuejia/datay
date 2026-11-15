package com.data.datafusion.service.datamap;

import com.data.datafusion.service.dto.datamap.DataMapNodeDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 数据地图全文索引分词器。
 *
 * <p>拉丁词按非字母数字切分并补充 camelCase 片段；中文按单字 + 2-gram 切分，
 * 既不依赖 DuckDB FTS 扩展，也能覆盖中文短语的部分匹配。</p>
 */
@Component
public class DataMapTokenizer {

    private static final Pattern CJK_GROUP = Pattern.compile("[\\u3400-\\u4dbf\\u4e00-\\u9fff\\uf900-\\ufaff]+");

    private static final Pattern LATIN_GROUP = Pattern.compile("[a-z0-9_$]+");

    private static final Pattern CAMEL_PART = Pattern.compile("[A-Z]?[a-z]+|[A-Z]+(?![a-z])|[0-9]+");

    public List<DataMapToken> tokensFor(DataMapNodeDTO node) {
        Map<String, DataMapToken> tokens = new LinkedHashMap<>();
        addField(tokens, "name", node.name(), 8.0);
        addField(tokens, "code", node.code(), 5.0);
        addField(tokens, "qualifiedName", node.qualifiedName(), 4.0);
        addField(tokens, "description", node.description(), 2.0);
        addField(tokens, "dataSourceName", node.dataSourceName(), 2.0);
        addField(tokens, "status", node.status(), 1.0);
        if (node.meta() != null) {
            Object comment = node.meta().get("comment");
            if (comment != null) {
                addField(tokens, "description", String.valueOf(comment), 2.0);
            }
            Object dataType = node.meta().get("dataType");
            if (dataType != null) {
                addField(tokens, "meta", String.valueOf(dataType), 1.0);
            }
        }
        return new ArrayList<>(tokens.values());
    }

    /**
     * 查询词切分，结果去重，保持顺序。
     */
    public List<String> queryTokens(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return new ArrayList<>(tokenize(query));
    }

    public Set<String> tokenize(String text) {
        Set<String> result = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return result;
        }
        String normalized = text.toLowerCase(Locale.ROOT).trim();
        for (String part : normalized.split("[\\s,;|/\\\\()\\[\\]{}<>\"'`_]+")) {
            if (part.isBlank()) {
                continue;
            }
            collectPart(result, part);
            for (String camel : CAMEL_PART.split(part)) {
                if (!camel.isBlank()) {
                    collectPart(result, camel);
                }
            }
        }
        if (normalized.length() <= 128) {
            result.add(normalized);
        }
        return result;
    }

    private void collectPart(Set<String> result, String part) {
        Matcher latin = LATIN_GROUP.matcher(part);
        while (latin.find()) {
            String value = latin.group();
            if (value.length() >= 2 || Character.isDigit(value.charAt(0))) {
                result.add(value);
            }
        }
        Matcher cjk = CJK_GROUP.matcher(part);
        while (cjk.find()) {
            String value = cjk.group();
            for (int i = 0; i < value.length(); i++) {
                result.add(value.substring(i, i + 1));
                if (i + 2 <= value.length()) {
                    result.add(value.substring(i, i + 2));
                }
            }
            if (value.length() <= 64) {
                result.add(value);
            }
        }
    }

    private void addField(Map<String, DataMapToken> tokens, String field, String text, double weight) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String token : tokenize(text)) {
            if (token.length() > 128) {
                continue;
            }
            String key = field + "|" + token;
            tokens.putIfAbsent(key, new DataMapToken(token, field, weight));
        }
    }

    public record DataMapToken(String token, String field, double weight) {}
}
