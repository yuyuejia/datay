package com.data.datasource.provider.jdbc;

import com.data.datasource.DataSourceConnection;
import com.data.datasource.QueryResult;
import com.data.datasource.provider.JdbcDataSourceProvider;
import com.data.job.DatasourceInfo;
import com.data.metadata.TableMeta;
import com.data.metadata.util.JdbcValueNormalizer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 基于 JDBC 的 {@link DataSourceConnection} 实现。
 *
 * <p>连接建立、库表元数据、连通性测试、方言包装等数据库特有行为全部委托给
 * {@link JdbcDataSourceProvider}，本类只负责通用的语句执行与 {@link ResultSet} 归一化。
 */
public class JdbcDataSourceConnection implements DataSourceConnection {

    private final JdbcDataSourceProvider provider;
    private final DatasourceInfo info;
    private final Connection connection;

    public JdbcDataSourceConnection(JdbcDataSourceProvider provider, DatasourceInfo info) throws SQLException {
        this.provider = provider;
        this.info = info;
        this.connection = provider.openConnection(info);
    }

    @Override
    public QueryResult executeQuery(String statement, List<Object> params, int maxRows) throws SQLException {
        if (statement == null || statement.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL语句不能为空");
        }
        String normalized = stripTrailingSemicolons(statement);
        if (params != null && !params.isEmpty()) {
            String execSql = provider.prepareStatement(connection, normalized);
            try (PreparedStatement ps = connection.prepareStatement(execSql)) {
                applyMaxRows(ps, maxRows);
                bind(ps, params);
                return executeAndRead(ps, maxRows);
            }
        }
        String execSql = provider.prepareStatement(connection, normalized);
        try (Statement stmt = connection.createStatement()) {
            applyMaxRows(stmt, maxRows);
            return executeAndRead(stmt, execSql, maxRows);
        }
    }

    /**
     * 不预判语句类型，交由数据库自己决定返回结果集还是更新行数。
     *
     * <p>这样 {@code VALUES}、带注释的 {@code SELECT}、{@code PRAGMA}/{@code CALL}
     * 以及同时包含 DDL/DML/SELECT 的多语句脚本都能按数据库的实际行为返回，
     * 不会因为首关键字不在白名单里被误当成更新语句。
     */
    private QueryResult executeAndRead(PreparedStatement statement, int maxRows) throws SQLException {
        ExecutionResult result = new ExecutionResult();
        result.drain(statement, statement.execute(), maxRows);
        return result.toQueryResult();
    }

    private QueryResult executeAndRead(Statement statement, String sql, int maxRows) throws SQLException {
        ExecutionResult result = new ExecutionResult();
        result.drain(statement, statement.execute(sql), maxRows);
        return result.toQueryResult();
    }

    /**
     * 单个 SQL 脚本的执行结果：保留最后一个结果集，并累计前面 DML 的影响行数。
     */
    private final class ExecutionResult {

        private QueryResult lastResultSet;
        private int affectedRows;

        private void drain(Statement statement, boolean hasResultSet, int maxRows) throws SQLException {
            while (true) {
                if (hasResultSet) {
                    try (ResultSet rs = statement.getResultSet()) {
                        if (rs != null) {
                            lastResultSet = readResult(rs, maxRows);
                        }
                    }
                } else {
                    int updateCount = statement.getUpdateCount();
                    if (updateCount == -1) {
                        break;
                    }
                    affectedRows += updateCount;
                }
                hasResultSet = statement.getMoreResults();
            }
        }

        private QueryResult toQueryResult() {
            return lastResultSet != null ? lastResultSet : QueryResult.updates(Math.max(affectedRows, 0));
        }
    }

    @Override
    public int executeUpdate(String statement, List<Object> params) throws SQLException {
        return executeQuery(statement, params, 0).getAffectedRows();
    }

    @Override
    public List<String> getCatalogs() throws SQLException {
        return provider.getCatalogs(connection);
    }

    @Override
    public List<String> getSchemas() throws SQLException {
        return provider.getSchemas(connection);
    }

    @Override
    public List<String> getSchemas(String catalog) throws SQLException {
        return provider.getSchemas(connection, catalog);
    }

    @Override
    public List<TableMeta> getTables(String schema, Integer limit, String search) throws SQLException {
        return provider.getTables(connection, schema, limit, search);
    }

    @Override
    public List<TableMeta> getTables(String catalog, String schema, Integer limit, String search) throws SQLException {
        return provider.getTables(connection, catalog, schema, limit, search);
    }

    @Override
    public TableMeta getTableMeta(String schema, String table) throws SQLException {
        return provider.getTableMeta(connection, schema, table);
    }

    @Override
    public TableMeta getTableMeta(String catalog, String schema, String table) throws SQLException {
        return provider.getTableMeta(connection, catalog, schema, table);
    }

    @Override
    public TableMeta getTableDetail(String schema, String table) throws SQLException {
        return provider.getTableDetail(connection, schema, table);
    }

    @Override
    public TableMeta getTableDetail(String catalog, String schema, String table) throws SQLException {
        return provider.getTableDetail(connection, catalog, schema, table);
    }

    @Override
    public Map<String, Object> getTableHealth(String catalog, String schema, String table) throws SQLException {
        return provider.getTableHealth(connection, catalog, schema, table);
    }

    @Override
    public List<Map<String, Object>> getTableMaintenanceActions(String catalog, String schema, String table)
        throws SQLException {
        return provider.getTableMaintenanceActions(connection, catalog, schema, table);
    }

    @Override
    public Map<String, Object> executeTableMaintenance(
        String catalog,
        String schema,
        String table,
        String action,
        Map<String, Object> params
    ) throws SQLException {
        return provider.executeTableMaintenance(connection, catalog, schema, table, action, params);
    }

    @Override
    public List<Map<String, Object>> getCatalogMaintenanceActions() throws SQLException {
        return provider.getCatalogMaintenanceActions(connection);
    }

    @Override
    public Map<String, Object> executeCatalogMaintenance(String action, Map<String, Object> params) throws SQLException {
        return provider.executeCatalogMaintenance(connection, action, params);
    }

    @Override
    public boolean testConnection() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet rs = statement.executeQuery(provider.getTestQuery())) {
                return true;
            }
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    private static void applyMaxRows(Statement statement, int maxRows) throws SQLException {
        if (maxRows > 0) {
            // 将行数限制下推到 JDBC 驱动，减少驱动侧缓存与网络传输，避免无 LIMIT 查询把结果集整体读入内存。
            statement.setMaxRows(maxRows);
        }
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            if (value instanceof String) {
                // 部分驱动（如 TDengine WS）对 NCHAR 列的 setObject(String) 兼容性较差，
                // 统一使用标准 setString，避免类型转换异常。
                ps.setString(i + 1, (String) value);
            } else {
                ps.setObject(i + 1, value);
            }
        }
    }

    private static String stripTrailingSemicolons(String sql) {
        if (sql == null) {
            return null;
        }
        String trimmed = sql.trim();
        // Oracle 等数据库的匿名 PL/SQL 块（BEGIN ... END; / DECLARE ... END;）必须保留结尾分号，
        // 否则会被驱动判定为语法错误（ORA-06550）。普通 SQL 语句则去掉结尾分号以兼容 JDBC。
        if (isPlsqlBlock(trimmed)) {
            return trimmed;
        }
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /**
     * 判断语句是否为 PL/SQL 匿名块（允许前置空白与注释）。
     */
    private static boolean isPlsqlBlock(String sql) {
        int index = 0;
        int length = sql.length();
        while (index < length) {
            char c = sql.charAt(index);
            if (Character.isWhitespace(c)) {
                index++;
                continue;
            }
            if (sql.startsWith("--", index)) {
                int lineEnd = sql.indexOf('\n', index + 2);
                if (lineEnd < 0) {
                    return false;
                }
                index = lineEnd + 1;
                continue;
            }
            if (sql.startsWith("/*", index)) {
                int commentEnd = sql.indexOf("*/", index + 2);
                if (commentEnd < 0) {
                    return false;
                }
                index = commentEnd + 2;
                continue;
            }
            break;
        }
        if (index >= length) {
            return false;
        }
        String head = sql.substring(index, Math.min(index + 7, length)).toUpperCase(Locale.ROOT);
        return head.startsWith("BEGIN") || head.startsWith("DECLARE");
    }

    private QueryResult readResult(ResultSet rs, int maxRows) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<String> columns = buildColumnLabels(meta, columnCount);
        int[] sqlTypes = new int[columnCount];
        String[] typeNames = new String[columnCount];
        for (int i = 1; i <= columnCount; i++) {
            sqlTypes[i - 1] = meta.getColumnType(i);
            typeNames[i - 1] = meta.getColumnTypeName(i);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int rowCount = 0;
        while (rs.next() && (maxRows <= 0 || rowCount < maxRows)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                Object value = provider.readQueryValue(rs, i, sqlTypes[i - 1], typeNames[i - 1]);
                row.put(columns.get(i - 1), normalizeValue(value, sqlTypes[i - 1]));
            }
            rows.add(row);
            rowCount++;
        }
        return new QueryResult(columns, rows, rowCount);
    }

    /**
     * 生成唯一列标签。同名别名（如 {@code SELECT 1 AS X, 2 AS X}）若直接用 Map 存放会互相覆盖，
     * 这里按出现顺序追加 {@code _2}、{@code _3} 后缀，保证每一列的数据都能返回。
     */
    private static List<String> buildColumnLabels(ResultSetMetaData meta, int columnCount) throws SQLException {
        List<String> labels = new ArrayList<>(columnCount);
        Set<String> used = new HashSet<>();
        for (int i = 1; i <= columnCount; i++) {
            String label = meta.getColumnLabel(i);
            if (label == null || label.isEmpty()) {
                label = "COLUMN_" + i;
            }
            String uniqueLabel = label;
            int suffix = 2;
            while (!used.add(uniqueLabel)) {
                uniqueLabel = label + "_" + suffix++;
            }
            labels.add(uniqueLabel);
        }
        return labels;
    }

    private static Object normalizeValue(Object value, int sqlType) throws SQLException {
        if (value == null) {
            return null;
        }
        Object normalized = JdbcValueNormalizer.normalize(value);
        if (normalized == null) {
            return null;
        }
        // DECIMAL 保留 BigDecimal，避免查询 API 把高精度小数降级成 double。
        if (normalized instanceof BigDecimal) {
            return normalized;
        }
        if (normalized instanceof String || normalized instanceof Boolean || normalized instanceof Number) {
            return normalized;
        }
        // ClickHouse Array/Map/Tuple 以及 PG 数组等由归一化工具转为 JSONArray/JSONObject，
        // 直接返回结构化对象让上层序列化为 JSON，而不是输出 [I@ 之类的默认 toString。
        if (normalized instanceof Map || normalized instanceof Collection) {
            return normalized;
        }
        // 标准时间类型统一转为字符串展示
        if (
            normalized instanceof java.sql.Timestamp ||
            normalized instanceof java.sql.Date ||
            normalized instanceof java.sql.Time ||
            normalized instanceof java.util.Date ||
            normalized instanceof java.time.temporal.Temporal
        ) {
            return normalized.toString();
        }
        if (normalized instanceof byte[]) {
            // 部分驱动（如 TDengine WebSocket）会把 CHAR/VARCHAR 也返回为 byte[]，
            // 按列类型解码为字符串，避免查询预览把文本展示成 [BLOB]。
            if (isCharacterType(sqlType)) {
                return new String((byte[]) normalized, StandardCharsets.UTF_8);
            }
            return "[BLOB]";
        }
        if (normalized instanceof Clob) {
            Clob clob = (Clob) normalized;
            return clob.getSubString(1, (int) clob.length());
        }
        if (normalized instanceof java.sql.Blob) {
            return "[BLOB]";
        }
        // 驱动特有类型（如 oracle.sql.TIMESTAMP/RAW/NUMBER、PGobject 等）没有通用序列化支持，
        // 统一降级为其文本表示，避免序列化失败导致查询预览报 500。
        return normalized.toString();
    }

    /**
     * 是否为字符类型（CHAR / VARCHAR / 长文本）。
     */
    private static boolean isCharacterType(int sqlType) {
        switch (sqlType) {
            case Types.CHAR:
            case Types.VARCHAR:
            case Types.LONGVARCHAR:
            case Types.NCHAR:
            case Types.NVARCHAR:
            case Types.LONGNVARCHAR:
            case Types.CLOB:
            case Types.NCLOB:
                return true;
            default:
                return false;
        }
    }
}
