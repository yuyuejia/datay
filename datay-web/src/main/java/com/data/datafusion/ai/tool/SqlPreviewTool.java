package com.data.datafusion.ai.tool;

import com.data.datafusion.ai.AiSqlMode;
import com.data.datafusion.service.DataSourceQueryService;
import com.data.datafusion.service.dto.DataSourceDTO;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SQL 试跑工具（只读）。
 *
 * <p>在真实数据源上小样本执行 SELECT，让模型看到返回结构，
 * 从而判断字段/口径是否符合用户意图。严格限制为单条 SELECT，
 * 并强制外层套 LIMIT，避免大结果集冲击数据库。
 */
@Component
public class SqlPreviewTool implements AiTool {

    private static final Logger LOG = LoggerFactory.getLogger(SqlPreviewTool.class);

    private final DataSourceQueryService dataSourceQueryService;

    public SqlPreviewTool(DataSourceQueryService dataSourceQueryService) {
        this.dataSourceQueryService = dataSourceQueryService;
    }

    @Override
    public String name() {
        return "preview_sql_result";
    }

    /**
     * 试跑工具仅适用于只读查询场景；SQL 任务包含 DDL/DML，不提供试跑以避免误执行写操作。
     */
    @Override
    public boolean supports(AiSqlMode mode) {
        return mode != AiSqlMode.TASK;
    }

    @Override
    public String description() {
        return """
            以很小的样本量试跑一段 SELECT，返回列名与前若干行数据，用于确认查询口径是否符合用户预期。
            仅支持单条 SELECT 查询，禁止任何写操作。返回结果仅用于自检，不要臆造数据。
            """;
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> sql = new LinkedHashMap<>();
        sql.put("type", "string");
        sql.put("description", "待试跑的 SELECT 语句");
        props.put("sql", sql);
        Map<String, Object> limit = new LinkedHashMap<>();
        limit.put("type", "integer");
        limit.put("description", "返回行数上限，默认 5，最大 20");
        props.put("limit", limit);
        schema.put("properties", props);
        schema.put("required", List.of("sql"));
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments, AiToolContext context) throws Exception {
        DataSourceDTO dataSource = context.getDataSource();
        String sql = arguments.get("sql") == null ? null : String.valueOf(arguments.get("sql"));

        Map<String, Object> result = new LinkedHashMap<>();
        if (dataSource == null) {
            result.put("error", "当前会话未绑定数据源，无法试跑 SQL");
            return result;
        }
        if (sql == null || sql.isBlank()) {
            result.put("error", "SQL 不能为空");
            return result;
        }
        if (!isReadOnlySql(sql)) {
            result.put("error", "preview_sql_result 只允许执行 SELECT 查询");
            return result;
        }

        int limit = parseLimit(arguments.get("limit"));
        String probe = "SELECT * FROM (" + stripTrailingSemicolon(sql) + ") datay_ai_preview LIMIT " + limit;

        try {
            Map<String, Object> queryResult = dataSourceQueryService.executeQuery(dataSource, probe);
            result.put("columns", queryResult.get("columns"));
            List<Map<String, Object>> rows = castRows(queryResult.get("rows"));
            result.put("rowCount", rows.size());
            result.put("rows", rows.size() > limit ? rows.subList(0, limit) : rows);
        } catch (SQLException | IllegalArgumentException e) {
            LOG.debug("AI tool preview_sql_result failed: {}", e.getMessage());
            result.put("error", "试跑失败: " + e.getMessage());
        }
        return result;
    }

    private static boolean isReadOnlySql(String sql) {
        String normalized = sql.trim().toUpperCase();
        return normalized.startsWith("SELECT") || normalized.startsWith("WITH");
    }

    private static String stripTrailingSemicolon(String sql) {
        String trimmed = sql.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static int parseLimit(Object raw) {
        int limit = 5;
        if (raw instanceof Number number) {
            limit = number.intValue();
        } else if (raw != null) {
            try {
                limit = Integer.parseInt(String.valueOf(raw));
            } catch (NumberFormatException ignored) {
                // 保持默认值
            }
        }
        return Math.max(1, Math.min(limit, 20));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castRows(Object rows) {
        if (rows instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return new ArrayList<>();
    }
}
