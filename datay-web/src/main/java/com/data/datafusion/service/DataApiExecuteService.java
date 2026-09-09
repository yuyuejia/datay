package com.data.datafusion.service;

import com.data.datafusion.domain.DataApi;
import com.data.datafusion.domain.Tenant;
import com.data.datafusion.domain.User;
import com.data.datafusion.mcp.McpTokenService;
import com.data.datafusion.repository.TenantRepository;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.dto.DataApiDTO;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import com.data.job.DatasourceInfo;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 数据服务对外调用入口。
 *
 * <p>复用用户的 {@code mcp_token} 鉴权：令牌所属用户默认租户下配置的“数据服务”才会被解析和执行。
 * 表来源类型支持按列等值过滤与分页；SQL 来源类型把 {@code ${param}} 占位符替换为预编译参数后只读执行。</p>
 */
@Service
public class DataApiExecuteService {

    private static final Logger LOG = LoggerFactory.getLogger(DataApiExecuteService.class);

    private static final String PLACEHOLDER_PREFIX = "${";

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)\\}");

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 1000;

    private static final int MAX_SQL_ROWS = 10000;

    private static final Pattern DML_KEYWORD = Pattern.compile(
        "(?i)\\b(update|delete|insert|drop|alter|truncate|merge|create|replace|grant|revoke|rename|set)\\b"
    );

    private final McpTokenService mcpTokenService;

    private final TenantRepository tenantRepository;

    private final DataApiService dataApiService;

    private final DataSourceService dataSourceService;

    public DataApiExecuteService(
        McpTokenService mcpTokenService,
        TenantRepository tenantRepository,
        DataApiService dataApiService,
        DataSourceService dataSourceService
    ) {
        this.mcpTokenService = mcpTokenService;
        this.tenantRepository = tenantRepository;
        this.dataApiService = dataApiService;
        this.dataSourceService = dataSourceService;
    }

    /**
     * 使用用户的 mcp_token 调用指定编码的数据服务。
     *
     * @param token  用户令牌，形如 {@code mcp_<userId>_<random>}。
     * @param code   服务编码。
     * @param params 调用参数（键为参数名，值为参数值）。表模式中与表字段同名的参数将做等值过滤，
     *               并识别 {@code pageNum}/{@code pageSize} 分页参数；SQL 模式中用于绑定 SQL 里的占位符。
     * @return 统一返回体 {@code {code, message, data, columns, total, pageNum, pageSize}}。
     */
    public Map<String, Object> invokeApi(String token, String code, Map<String, Object> params) {
        User user = mcpTokenService
            .authenticateUser(token)
            .orElseThrow(() -> new DataApiAccessException(401, "无效或缺失的访问令牌"));

        Long tenantId = tenantRepository
            .findDefaultTenantByUserId(user.getId())
            .map(Tenant::getId)
            .orElseThrow(() -> new DataApiAccessException(401, "用户未关联任何租户"));

        TenantContext.setTenantId(tenantId);
        try {
            DataApiDTO api = dataApiService
                .findByCode(code)
                .orElseThrow(() -> new DataApiAccessException(404, "服务不存在或未发布：" + code));
            if (!DataApi.STATUS_ENABLED.equals(api.getStatus())) {
                throw new DataApiAccessException(404, "服务不存在或未发布：" + code);
            }

            DataSourceDTO dataSource = dataSourceService
                .findOne(api.getDataSourceId())
                .orElseThrow(() -> new DataApiAccessException(500, "服务绑定的数据源不存在"));

            DatasourceInfo datasourceInfo = DataSourceQueryService.toDatasourceInfo(dataSource);
            if (DataApi.SOURCE_TYPE_TABLE.equals(api.getSourceType())) {
                return invokeFromTable(api, datasourceInfo, params);
            }
            return invokeFromSql(api, datasourceInfo, params);
        } finally {
            TenantContext.clear();
        }
    }

    // ------------------------------------------------------------------ table

    private Map<String, Object> invokeFromTable(DataApiDTO api, DatasourceInfo datasourceInfo, Map<String, Object> params) {
        String schema = api.getSchemaName();
        String table = api.getTableName();
        try (Connection connection = DBUtils.getConnection(datasourceInfo)) {
            String dbType = resolveDbType(datasourceInfo);
            TableMeta tableMeta = DBUtils.getTableMetaData(connection, schema, table);
            Map<String, ColumnMeta> columnMap = new LinkedHashMap<>();
            for (ColumnMeta column : tableMeta.columns()) {
                columnMap.put(column.getName(), column);
            }

            String qualifiedTable = qualifyTable(dbType, schema, table);
            List<String> conditions = new ArrayList<>();
            List<Object> bindValues = new ArrayList<>();

            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String paramName = entry.getKey();
                if (paramName == null || isReservedTableParam(paramName)) {
                    continue;
                }
                ColumnMeta column = columnMap.get(paramName);
                if (column == null) {
                    continue;
                }
                String rawValue = toStringValue(entry.getValue());
                if (rawValue == null || rawValue.isEmpty()) {
                    continue;
                }
                conditions.add(quoteIdent(dbType, column.getName()) + " = ?");
                bindValues.add(convertByColumnType(column, rawValue));
            }

            int pageNum = parsePositiveInt(toStringValue(params.get("pageNum")), 1);
            int pageSize = Math.min(Math.max(parsePositiveInt(toStringValue(params.get("pageSize")), DEFAULT_PAGE_SIZE), 1), MAX_PAGE_SIZE);
            long maxOffset = Integer.MAX_VALUE - pageSize;
            if (((long) pageNum - 1) * pageSize > maxOffset) {
                pageNum = (int) (maxOffset / pageSize) + 1;
            }
            int offset = (pageNum - 1) * pageSize;

            String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

            long total = 0;
            try (
                PreparedStatement countStmt = connection.prepareStatement("SELECT COUNT(*) FROM " + qualifiedTable + where);
            ) {
                bindParameters(countStmt, bindValues);
                try (ResultSet rs = countStmt.executeQuery()) {
                    if (rs.next()) {
                        total = rs.getLong(1);
                    }
                }
            }

            String baseSelect = "SELECT * FROM " + qualifiedTable + where;
            String pageSql = buildPagedSelect(dbType, baseSelect, pageSize, offset);
            List<Object> pageParams = new ArrayList<>(bindValues);
            if (isOracle(dbType)) {
                pageParams.add((long) pageNum * pageSize);
                pageParams.add((long) offset);
            } else {
                pageParams.add(pageSize);
                pageParams.add(offset);
            }

            Map<String, Object> readResult;
            try (
                PreparedStatement dataStmt = connection.prepareStatement(pageSql);
            ) {
                bindParameters(dataStmt, pageParams);
                try (ResultSet rs = dataStmt.executeQuery()) {
                    readResult = readResultSet(rs, MAX_PAGE_SIZE);
                }
            }

            return buildSuccess(readResult, total, pageNum, pageSize);
        } catch (SQLException e) {
            LOG.error("Failed to execute table data api {} ({}).{}", api.getCode(), schema, table, e);
            throw new DataApiAccessException(500, "服务执行失败：" + e.getMessage());
        }
    }

    // ------------------------------------------------------------------- sql

    private Map<String, Object> invokeFromSql(DataApiDTO api, DatasourceInfo datasourceInfo, Map<String, Object> params) {
        String originalSql = api.getSqlText();
        assertReadOnlySql(originalSql);

        List<String> placeholderNames = new ArrayList<>();
        StringBuilder parsedSql = new StringBuilder();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(originalSql);
        int lastEnd = 0;
        while (matcher.find()) {
            parsedSql.append(originalSql, lastEnd, matcher.start());
            placeholderNames.add(matcher.group(1));
            parsedSql.append("?");
            lastEnd = matcher.end();
        }
        parsedSql.append(originalSql.substring(lastEnd));

        List<String> missing = new ArrayList<>();
        List<Object> bindValues = new ArrayList<>();
        for (String name : placeholderNames) {
            Object value = params.get(name);
            if (value == null || toStringValue(value).isEmpty()) {
                missing.add(name);
            } else {
                bindValues.add(toBindableValue(value));
            }
        }
        if (!missing.isEmpty()) {
            throw new DataApiAccessException(400, "缺少参数：" + String.join(", ", missing));
        }

        String sql = parsedSql.toString();
        try (Connection connection = DBUtils.getConnection(datasourceInfo)) {
            Map<String, Object> readResult;
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                bindParameters(stmt, bindValues);
                boolean hasResultSet = stmt.execute();
                if (!hasResultSet) {
                    throw new DataApiAccessException(400, "仅支持 SELECT 类只读查询");
                }
                try (ResultSet rs = stmt.getResultSet()) {
                    readResult = readResultSet(rs, MAX_SQL_ROWS);
                }
            }
            return buildSuccess(readResult, ((List<?>) readResult.get("data")).size(), 1, 0);
        } catch (SQLException e) {
            LOG.error("Failed to execute sql data api {}: {}", api.getCode(), sql, e);
            throw new DataApiAccessException(500, "服务执行失败：" + e.getMessage());
        }
    }

    private void assertReadOnlySql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new DataApiAccessException(400, "自定义 SQL 不能为空");
        }
        String trimmed = sql.trim();
        int firstSpace = trimmed.indexOf(' ');
        String firstWord = (firstSpace < 0 ? trimmed : trimmed.substring(0, firstSpace)).toUpperCase();
        boolean readableFirstKeyword =
            firstWord.equals("SELECT") ||
            firstWord.equals("WITH") ||
            firstWord.equals("SHOW") ||
            firstWord.equals("EXPLAIN") ||
            firstWord.equals("DESC") ||
            firstWord.equals("DESCRIBE");
        if (!readableFirstKeyword || DML_KEYWORD.matcher(trimmed).find()) {
            throw new DataApiAccessException(400, "自定义 SQL 仅支持 SELECT 等只读查询");
        }
    }

    // ---------------------------------------------------------------- helpers

    private Map<String, Object> buildSuccess(Map<String, Object> readResult, long total, int pageNum, int pageSize) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 0);
        result.put("message", "success");
        result.put("data", readResult.get("rows"));
        result.put("columns", readResult.get("columns"));
        result.put("total", total);
        if (pageNum > 0) {
            result.put("pageNum", pageNum);
            result.put("pageSize", pageSize);
        }
        return result;
    }

    private Map<String, Object> readResultSet(ResultSet rs, int maxRows) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int rowCount = 0;
        while (rs.next() && rowCount < maxRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(meta.getColumnLabel(i), convertCell(rs.getObject(i)));
            }
            rows.add(row);
            rowCount++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", columns);
        result.put("rows", rows);
        return result;
    }

    private static Object convertCell(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp || value instanceof java.sql.Date || value instanceof java.sql.Time) {
            return value.toString();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.doubleValue();
        }
        if (value instanceof byte[]) {
            return "[BLOB]";
        }
        if (value instanceof java.sql.Clob clob) {
            try {
                return clob.getSubString(1, (int) clob.length());
            } catch (SQLException e) {
                return null;
            }
        }
        if (value instanceof java.sql.Blob) {
            return "[BLOB]";
        }
        return value;
    }

    private static void bindParameters(PreparedStatement stmt, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            stmt.setObject(i + 1, values.get(i));
        }
    }

    private static Object convertByColumnType(ColumnMeta column, String rawValue) {
        String type = column.getType() == null ? "" : column.getType().toUpperCase();
        if (type.contains("BOOL")) {
            return toBoolean(rawValue);
        }
        if (type.contains("INT") || type.contains("NUM") || type.contains("DEC") || type.contains("FLOAT") || type.contains("DOUBLE") || type.contains("REAL")) {
            try {
                if (
                    type.contains("FLOAT") ||
                    type.contains("DOUBLE") ||
                    type.contains("REAL") ||
                    type.contains("DEC") ||
                    type.contains("NUM")
                ) {
                    return Double.parseDouble(rawValue);
                }
                return Long.parseLong(rawValue);
            } catch (NumberFormatException e) {
                return rawValue;
            }
        }
        return rawValue;
    }

    private static Object toBindableValue(Object value) {
        if (value instanceof Number || value instanceof Boolean) {
            return value;
        }
        String text = toStringValue(value);
        if (text == null || text.isEmpty()) {
            return text;
        }
        if ("true".equalsIgnoreCase(text) || "false".equalsIgnoreCase(text)) {
            return Boolean.valueOf(text);
        }
        if (text.matches("-?\\d+")) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        if (text.matches("-?\\d+\\.\\d+([eE][+-]?\\d+)?")) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return text;
    }

    private static Boolean toBoolean(String value) {
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    private static String toStringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof String[] array) {
            return array.length == 0 ? null : array[0];
        }
        return String.valueOf(value);
    }

    private static int parsePositiveInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static boolean isReservedTableParam(String paramName) {
        return "pageNum".equals(paramName) || "pageSize".equals(paramName) || "access_token".equals(paramName);
    }

    private static String buildPagedSelect(String dbType, String baseSelect, int pageSize, int offset) {
        if (isOracle(dbType)) {
            return "SELECT * FROM (SELECT t.*, ROWNUM rn FROM (" + baseSelect + ") t WHERE ROWNUM <= ?) WHERE rn > ?";
        }
        return baseSelect + " LIMIT ? OFFSET ?";
    }

    private static boolean isOracle(String dbType) {
        return "ORACLE".equals(dbType);
    }

    private static String resolveDbType(DatasourceInfo datasourceInfo) {
        String fromUrl = DBUtils.getDBType(datasourceInfo.getUrl());
        if (fromUrl != null && !fromUrl.isBlank()) {
            return fromUrl.toUpperCase();
        }
        return datasourceInfo.getType() == null ? "MYSQL" : datasourceInfo.getType().toUpperCase();
    }

    private static String qualifyTable(String dbType, String schema, String table) {
        String quotedTable = quoteIdent(dbType, table);
        if (schema != null && !schema.trim().isEmpty()) {
            return quoteIdent(dbType, schema.trim()) + "." + quotedTable;
        }
        return quotedTable;
    }

    private static String quoteIdent(String dbType, String ident) {
        if (ident == null) {
            return "";
        }
        if ("MYSQL".equals(dbType) || "DORIS".equals(dbType) || "CLICKHOUSE".equals(dbType)) {
            return "`" + ident.replace("`", "``") + "`";
        }
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }
}
