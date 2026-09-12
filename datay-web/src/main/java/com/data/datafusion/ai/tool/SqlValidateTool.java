package com.data.datafusion.ai.tool;

import com.data.datafusion.service.DataSourceQueryService;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.metadata.util.DBUtils;
import java.sql.Connection;
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

    private static final Logger LOG = LoggerFactory.getLogger(SqlValidateTool.class);

    private final DataSourceQueryService dataSourceQueryService;

    public SqlValidateTool(DataSourceQueryService dataSourceQueryService) {
        this.dataSourceQueryService = dataSourceQueryService;
    }

    @Override
    public String name() {
        return "validate_sql";
    }

    @Override
    public String description() {
        return """
            校验一段 SQL 是否可被当前数据源正确解析执行。
            工具会以「不返回真实数据」的预检方式运行该 SQL，成功返回 valid=true，
            失败返回 valid=false 及数据库报错信息。请在给出最终 SQL 前调用本工具自检，
            若校验失败请依据报错修改后重试。
            """;
    }

    @Override
    public Map<String, Object> parametersSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> props = new LinkedHashMap<>();
        Map<String, Object> sql = new LinkedHashMap<>();
        sql.put("type", "string");
        sql.put("description", "待校验的 SQL 语句，必须是单条 SELECT 查询");
        props.put("sql", sql);
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
            result.put("valid", false);
            result.put("error", "当前会话未绑定数据源，无法校验 SQL");
            return result;
        }
        if (sql == null || sql.isBlank()) {
            result.put("valid", false);
            result.put("error", "SQL 不能为空");
            return result;
        }

        String probe = buildProbe(sql);
        try (Connection connection = DBUtils.getConnection(DataSourceQueryService.toDatasourceInfo(dataSource))) {
            dataSourceQueryService.executeQuery(dataSource, probe);
            result.put("valid", true);
            result.put("message", "SQL 语法与字段校验通过");
        } catch (SQLException | IllegalArgumentException e) {
            LOG.debug("AI tool validate_sql rejected SQL: {}", e.getMessage());
            result.put("valid", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * 将用户 SQL 包装为「零成本预检」：外层套 LIMIT 0，只走解析与类型推导，不实际拉取数据。
     * 对已带 LIMIT 的语句，直接使用原语句即可。
     */
    private String buildProbe(String sql) {
        String trimmed = sql.trim();
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return "SELECT * FROM (" + trimmed + ") datay_ai_probe LIMIT 0";
    }
}
