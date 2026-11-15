package com.data.datafusion.service.datamap;

import com.data.datafusion.domain.ETLNode;
import com.data.datafusion.service.lineage.SqlLineageParser;
import com.data.datafusion.service.lineage.SqlReferences;
import com.data.datafusion.service.lineage.TableRef;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 从 ETL 节点配置中解析读写表。
 *
 * <p>与 {@code DataModelLineageService} 中的 ETL 识别规则保持一致；后续可进一步抽取为共享组件。</p>
 */
@Component
public class EtlTableRefResolver {

    private static final Set<String> DIRECT_OUTPUT_TYPES = Set.of(
        "STREAMJDBCOUTPUT",
        "JDBCOUTPUT",
        "DORISSTREAMLOAD",
        "DUCKLAKEWRITE",
        "ELASTICSEARCHOUTPUT",
        "MONGODBOUTPUT",
        "INFLUXDBOUTPUT",
        "DUCKDBWRITE"
    );

    private static final Set<String> DIRECT_INPUT_TYPES = Set.of("JDBCINPUT", "STREAMJDBCINPUT");

    private final SqlLineageParser sqlLineageParser;

    public EtlTableRefResolver(SqlLineageParser sqlLineageParser) {
        this.sqlLineageParser = sqlLineageParser;
    }

    public List<TableTarget> readTargets(ETLNode node, JsonNode config, String defaultDataSourceId) {
        if (node == null || config == null) {
            return List.of();
        }
        String type = normalizeType(node.getType());
        if (DIRECT_INPUT_TYPES.contains(type)) {
            String dataSourceId = firstText(config, "sourceId", "dataSourceId", defaultDataSourceId);
            String schema = text(config, "schema");
            String table = text(config, "table");
            if (dataSourceId == null || table == null) {
                return List.of();
            }
            List<TableTarget> targets = new ArrayList<>();
            for (String item : table.split(",")) {
                if (!item.isBlank()) {
                    targets.add(new TableTarget(dataSourceId, TableRef.of(null, schema, item.trim())));
                }
            }
            return targets;
        }
        if (type.contains("SQL")) {
            String sql = extractSql(config);
            if (sql == null || sql.isBlank()) {
                return List.of();
            }
            String dataSourceId = firstText(config, "sourceId", "dataSourceId", defaultDataSourceId);
            if (dataSourceId == null) {
                return List.of();
            }
            SqlReferences references = sqlLineageParser.parse(sql);
            return references.reads().stream().map(table -> new TableTarget(dataSourceId, table)).toList();
        }
        return List.of();
    }

    public List<TableTarget> writeTargets(ETLNode node, JsonNode config, String defaultDataSourceId) {
        if (node == null || config == null) {
            return List.of();
        }
        String type = normalizeType(node.getType());
        if ("MODELWRITE".equals(type)) {
            return List.of();
        }
        if (DIRECT_OUTPUT_TYPES.contains(type)) {
            String dataSourceId = firstText(config, "sourceId", "dataSourceId", defaultDataSourceId);
            String schema = text(config, "schema");
            String table = text(config, "table");
            if (dataSourceId == null || table == null) {
                return List.of();
            }
            return List.of(new TableTarget(dataSourceId, TableRef.of(null, schema, table)));
        }
        if (type.contains("SQL")) {
            String sql = extractSql(config);
            if (sql == null || sql.isBlank()) {
                return List.of();
            }
            String dataSourceId = firstText(config, "sourceId", "dataSourceId", defaultDataSourceId);
            if (dataSourceId == null) {
                return List.of();
            }
            SqlReferences references = sqlLineageParser.parse(sql);
            return references.writes().stream().map(table -> new TableTarget(dataSourceId, table)).toList();
        }
        return List.of();
    }

    public String modelWriteId(ETLNode node, JsonNode config) {
        if (node == null || config == null || !"MODELWRITE".equals(normalizeType(node.getType()))) {
            return null;
        }
        return text(config, "modelId");
    }

    private String extractSql(JsonNode config) {
        JsonNode sql = config.get("sql");
        if (sql == null) {
            sql = config.get("query");
        }
        if (sql == null) {
            return null;
        }
        if (sql.isTextual()) {
            return sql.asText();
        }
        if (sql.isObject()) {
            return text(sql, "query");
        }
        return null;
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private String normalizeType(String type) {
        return type == null ? "" : type.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    public record TableTarget(String dataSourceId, TableRef table) {}
}
