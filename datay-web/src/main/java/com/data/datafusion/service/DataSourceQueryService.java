package com.data.datafusion.service;

import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.job.DatasourceInfo;
import com.data.metadata.util.DBUtils;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Shared service used to execute SQL against a registered data source.
 *
 * <p>Both the REST layer ({@code DataSourceResource}) and the MCP server reuse this
 * service so that query semantics stay consistent across entry points.
 */
@Service
public class DataSourceQueryService {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceQueryService.class);

    private static final int MAX_ROWS = 10000;

    /**
     * Converts a {@link DataSourceDTO} into the {@link DatasourceInfo} used by the
     * lower-level {@link DBUtils} connection helpers.
     */
    public static DatasourceInfo toDatasourceInfo(DataSourceDTO dto) {
        DatasourceInfo info = new DatasourceInfo();
        info.setType(dto.getType());
        info.setUrl(dto.getUrl());
        info.setUsername(dto.getUsername());
        info.setPassword(dto.getPassword());
        info.setDbschema(dto.getSchemaName());
        info.setPort(dto.getPort());
        info.setHostname(dto.getHostname());
        info.setExtraParams(dto.getExtraParams());
        return info;
    }

    /**
     * Executes the given SQL against the supplied data source and returns a result map
     * containing {@code columns}, {@code rows} and {@code affectedRows}.
     *
     * @param dataSource the resolved data source to query against
     * @param sql the SQL statement to execute
     * @return a result map with {@code columns}, {@code rows} and {@code affectedRows}
     * @throws IllegalArgumentException if the SQL is blank
     * @throws SQLException if the SQL execution fails
     */
    public Map<String, Object> executeQuery(DataSourceDTO dataSource, String sql) throws SQLException {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL语句不能为空");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        try (Connection connection = DBUtils.getConnection(toDatasourceInfo(dataSource))) {
            boolean isQuack = dataSource.getUrl() != null && dataSource.getUrl().startsWith("quack:");
            String trimmedSql = sql.trim();
            boolean isSelect =
                trimmedSql.toUpperCase().startsWith("SELECT") ||
                trimmedSql.toUpperCase().startsWith("WITH") ||
                trimmedSql.toUpperCase().startsWith("SHOW") ||
                trimmedSql.toUpperCase().startsWith("DESCRIBE") ||
                trimmedSql.toUpperCase().startsWith("DESC") ||
                trimmedSql.toUpperCase().startsWith("EXPLAIN");

            // Quack 1.5.3 对非 main schema 的目录/模式限定表引用有缺陷，统一改由 quack.query 在远端执行
            String executeSql = isQuack ? DBUtils.wrapQuackQuery(sql) : sql;

            if (isSelect) {
                try (
                    Statement stmt = connection.createStatement();
                    ResultSet rs = stmt.executeQuery(executeSql)
                ) {
                    result.putAll(readSelectResult(rs));
                }
            } else {
                try (Statement stmt = connection.createStatement()) {
                    int affectedRows = stmt.executeUpdate(sql);
                    result.put("columns", Collections.emptyList());
                    result.put("rows", Collections.emptyList());
                    result.put("affectedRows", affectedRows);
                }
            }
        } catch (SQLException e) {
            LOG.error("SQL execution error", e);
            throw e;
        }

        return result;
    }

    /**
     * 以预编译参数执行只读查询，用于看板数据集等需要绑定筛选参数的安全查询。
     *
     * @param dataSource 目标数据源
     * @param sql        含 {@code ?} 占位符的只读 SQL
     * @param params     与占位符顺序一致的绑定参数
     * @return 结果 map，包含 {@code columns} / {@code rows} / {@code affectedRows}
     */
    public Map<String, Object> executeReadOnlyQuery(DataSourceDTO dataSource, String sql, List<Object> params) throws SQLException {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL语句不能为空");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        try (
            Connection connection = DBUtils.getConnection(toDatasourceInfo(dataSource));
            PreparedStatement stmt = connection.prepareStatement(sql)
        ) {
            if (params != null) {
                for (int i = 0; i < params.size(); i++) {
                    stmt.setObject(i + 1, params.get(i));
                }
            }
            try (ResultSet rs = stmt.executeQuery()) {
                result.putAll(readSelectResult(rs));
            }
        } catch (SQLException e) {
            LOG.error("Parameterized SQL execution error", e);
            throw e;
        }
        return result;
    }

    private Map<String, Object> readSelectResult(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            columns.add(meta.getColumnLabel(i));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int rowCount = 0;
        while (rs.next() && rowCount < MAX_ROWS) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String colName = meta.getColumnLabel(i);
                Object value = rs.getObject(i);
                if (value == null) {
                    row.put(colName, null);
                } else if (value instanceof java.sql.Timestamp || value instanceof java.sql.Date || value instanceof java.sql.Time) {
                    row.put(colName, value.toString());
                } else if (value instanceof BigDecimal) {
                    row.put(colName, ((BigDecimal) value).doubleValue());
                } else if (value instanceof byte[]) {
                    row.put(colName, "[BLOB]");
                } else if (value instanceof java.sql.Clob) {
                    java.sql.Clob clob = (java.sql.Clob) value;
                    row.put(colName, clob.getSubString(1, (int) clob.length()));
                } else if (value instanceof java.sql.Blob) {
                    row.put(colName, "[BLOB]");
                } else {
                    row.put(colName, value);
                }
            }
            rows.add(row);
            rowCount++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("affectedRows", rowCount);
        return result;
    }
}