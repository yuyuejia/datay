package com.data.datasource.provider.jdbc;

import com.data.datasource.DataSourceConnection;
import com.data.datasource.QueryResult;
import com.data.datasource.provider.JdbcDataSourceProvider;
import com.data.job.DatasourceInfo;
import com.data.metadata.TableMeta;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private static QueryResult executeAndRead(PreparedStatement statement, int maxRows) throws SQLException {
        ExecutionResult result = new ExecutionResult();
        result.drain(statement, statement.execute(), maxRows);
        return result.toQueryResult();
    }

    private static QueryResult executeAndRead(Statement statement, String sql, int maxRows) throws SQLException {
        ExecutionResult result = new ExecutionResult();
        result.drain(statement, statement.execute(sql), maxRows);
        return result.toQueryResult();
    }

    /**
     * 单个 SQL 脚本的执行结果：保留最后一个结果集，并累计前面 DML 的影响行数。
     */
    private static final class ExecutionResult {

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
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static QueryResult readResult(ResultSet rs, int maxRows) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int rowCount = 0;
        while (rs.next() && (maxRows <= 0 || rowCount < maxRows)) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(meta.getColumnLabel(i), normalizeValue(rs.getObject(i), meta.getColumnType(i)));
            }
            rows.add(row);
            rowCount++;
        }
        return new QueryResult(columns, rows, rowCount);
    }

    private static Object normalizeValue(Object value, int sqlType) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).doubleValue();
        }
        if (value instanceof String || value instanceof Boolean || value instanceof Number) {
            return value;
        }
        // 标准时间类型统一转为字符串展示
        if (
            value instanceof java.sql.Timestamp ||
            value instanceof java.sql.Date ||
            value instanceof java.sql.Time ||
            value instanceof java.util.Date ||
            value instanceof java.time.temporal.Temporal
        ) {
            return value.toString();
        }
        if (value instanceof byte[]) {
            // 部分驱动（如 TDengine WebSocket）会把 CHAR/VARCHAR 也返回为 byte[]，
            // 按列类型解码为字符串，避免查询预览把文本展示成 [BLOB]。
            if (isCharacterType(sqlType)) {
                return new String((byte[]) value, StandardCharsets.UTF_8);
            }
            return "[BLOB]";
        }
        if (value instanceof Clob) {
            Clob clob = (Clob) value;
            return clob.getSubString(1, (int) clob.length());
        }
        if (value instanceof java.sql.Blob) {
            return "[BLOB]";
        }
        // 驱动特有类型（如 oracle.sql.TIMESTAMP/RAW/NUMBER、PGobject 等）没有通用序列化支持，
        // 统一降级为其文本表示，避免序列化失败导致查询预览报 500。
        return value.toString();
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
