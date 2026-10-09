package com.data.datafusion.ai.tool;

import com.data.datafusion.service.DataSourceQueryService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.metadata.util.DBUtils;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SQL 校验工具（只读）。
 *
 * <p>让模型在给出最终 SQL 前先自检语法与字段引用是否正确，
 * 借助数据库自身的解析器快速失败，从而在 loop 中自我修正。
 */
@Component
public class SqlValidateTool implements AiTool {

    /** 工具名常量，供助手显式声明工具集合时引用，避免字符串拼写漂移。 */
    public static final String NAME = "validate_sql";

    private static final Logger LOG = LoggerFactory.getLogger(SqlValidateTool.class);

    private final DataSourceQueryService dataSourceQueryService;
    private final DataSourceService dataSourceService;

    public SqlValidateTool(DataSourceQueryService dataSourceQueryService, DataSourceService dataSourceService) {
        this.dataSourceQueryService = dataSourceQueryService;
        this.dataSourceService = dataSourceService;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String description() {
        return """
            校验一段 SQL 是否可被当前数据源正确解析。工具不会执行任何写操作：
            - SELECT 查询以「不返回真实数据」的方式预检语法与字段引用；
            - 建表、写入等 DDL/DML 语句仅提交数据库驱动做解析校验，不会真正执行。
            成功返回 valid=true，失败返回 valid=false 及数据库报错信息。
            请在给出最终 SQL 前调用本工具自检，若校验失败请依据报错修改后重试。
            """;
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> sql = new LinkedHashMap<>();
        sql.put("type", "string");
        sql.put("description", "待校验的 SQL 语句");
        props.put("sql", sql);
        Map<String, Object> dataSourceId = new LinkedHashMap<>();
        dataSourceId.put("type", "string");
        dataSourceId.put("description", "目标数据源 id，可选；缺省使用当前会话数据源。可用 list_data_sources 获取");
        props.put("dataSourceId", dataSourceId);
        schema.put("properties", props);
        schema.put("required", List.of("sql"));
        return schema;
    }

    @Override
    public Object execute(Map<String, Object> arguments, AiToolContext context) throws Exception {
        DataSourceDTO dataSource = resolveDataSource(arguments, context);
        String sql = arguments.get("sql") == null ? null : String.valueOf(arguments.get("sql"));

        Map<String, Object> result = new LinkedHashMap<>();
        if (dataSource == null) {
            result.put("valid", false);
            result.put("error", "当前会话未绑定数据源，且未提供有效的 dataSourceId");
            return result;
        }
        if (sql == null || sql.isBlank()) {
            result.put("valid", false);
            result.put("error", "SQL 不能为空");
            return result;
        }

        try (Connection connection = DBUtils.getConnection(DataSourceQueryService.toDatasourceInfo(dataSource))) {
            if (context.getAssistant().allowWrites() && !isReadOnlySql(sql)) {
                validateByParse(connection, sql, result);
            } else {
                String probe = buildProbe(sql);
                dataSourceQueryService.executeQuery(dataSource, probe);
                result.put("valid", true);
                result.put("message", "SQL 语法与字段校验通过");
            }
        } catch (SQLException | IllegalArgumentException e) {
            LOG.debug("AI tool validate_sql rejected SQL: {}", e.getMessage());
            result.put("valid", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * DDL/DML 的解析校验：借助 JDBC PreparedStatement 让数据库驱动解析语句结构，
     * 但绝不调用 execute，因此不会产生任何真实的数据变更。
     */
    private void validateByParse(Connection connection, String sql, Map<String, Object> result) {
        String target = stripTrailingSemicolon(sql);
        // 仅创建 PreparedStatement 触发数据库驱动的语法解析，不调用 execute，避免真实写操作
        try (PreparedStatement ignored = connection.prepareStatement(target)) {
            result.put("valid", true);
            result.put("message", "语句已通过数据库解析校验（未执行）");
        } catch (SQLException e) {
            result.put("valid", false);
            result.put("error", e.getMessage());
        }
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

    private DataSourceDTO resolveDataSource(Map<String, Object> arguments, AiToolContext context) {
        Object raw = arguments.get("dataSourceId");
        if (raw != null && !String.valueOf(raw).isBlank()) {
            try {
                String id = String.valueOf(raw).trim();
                return dataSourceService.findOne(id).orElse(null);
            } catch (NumberFormatException ignored) {
                // 非法 id 时回退到会话数据源
            }
        }
        return context.getDataSource();
    }

    /**
     * 将用户 SQL 包装为「零成本预检」：外层套 LIMIT 0，只走解析与类型推导，不实际拉取数据。
     * 对已带 LIMIT 的语句，直接使用原语句即可。
     */
    private String buildProbe(String sql) {
        return "SELECT * FROM (" + stripTrailingSemicolon(sql) + ") datay_ai_probe LIMIT 0";
    }
}
