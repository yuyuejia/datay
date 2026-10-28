package com.data.datasource.provider.jdbc;

import com.data.datasource.DataSourceConnection;
import com.data.datasource.QueryResult;
import com.data.datasource.provider.JdbcDataSourceProvider;
import com.data.job.DatasourceInfo;
import com.data.metadata.TableMeta;
import java.math.BigDecimal;
import java.sql.Clob;
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
        if (isSelect(normalized)) {
            String execSql = provider.isQuackConnection(connection) ? provider.wrapQuery(normalized) : normalized;
            if (params != null && !params.isEmpty()) {
                try (PreparedStatement ps = connection.prepareStatement(execSql)) {
                    bind(ps, params);
                    try (ResultSet rs = ps.executeQuery()) {
                        return readResult(rs, maxRows);
                    }
                }
            }
            try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(execSql)) {
                return readResult(rs, maxRows);
            }
        }

        int affectedRows;
        if (params != null && !params.isEmpty()) {
            try (PreparedStatement ps = connection.prepareStatement(normalized)) {
                bind(ps, params);
                affectedRows = ps.executeUpdate();
            }
        } else {
            try (Statement stmt = connection.createStatement()) {
                affectedRows = stmt.executeUpdate(normalized);
            }
        }
        return QueryResult.updates(affectedRows);
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

    private static boolean isSelect(String sql) {
        String upper = sql.trim().toUpperCase();
        return (
            upper.startsWith("SELECT") ||
            upper.startsWith("WITH") ||
            upper.startsWith("SHOW") ||
            upper.startsWith("DESCRIBE") ||
            upper.startsWith("DESC") ||
            upper.startsWith("EXPLAIN")
        );
    }

    private static void bind(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
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
                row.put(meta.getColumnLabel(i), normalizeValue(rs.getObject(i)));
            }
            rows.add(row);
            rowCount++;
        }
        return new QueryResult(columns, rows, rowCount);
    }

    private static Object normalizeValue(Object value) throws SQLException {
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
}
