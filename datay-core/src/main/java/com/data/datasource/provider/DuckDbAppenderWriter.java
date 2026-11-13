package com.data.datasource.provider;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.data.metadata.ColumnMeta;
import com.data.metadata.TableMeta;
import com.data.metadata.util.DBUtils;
import com.zaxxer.hikari.pool.HikariProxyConnection;
import org.duckdb.DuckDBAppender;
import org.duckdb.DuckDBConnection;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;

/**
 * DuckDB / DuckLake 通用的大批量写入实现。
 *
 * <p>使用 {@link DuckDBAppender} 直接向存储层提交行数据，绕过 SQL 解析、查询规划与参数绑定。
 * 实测在 1000~10000 行/批时，比拼接多值 {@code INSERT ... VALUES} 快一个数量级。</p>
 *
 * <p><b>事件类型约束：</b>只允许 INSERT / APPEND / OVERWRITE。DuckLake 没有主键/唯一约束，
 * UPDATE / DELETE 必须继续走 SQL（CDC 场景依赖变更前镜像），不能使用 Appender。</p>
 *
 * <p><b>回退语义：</b>{@link #append} 返回 {@code false} 表示 Appender 尚未写入任何一行
 * （连接不是 DuckDB，或 Appender 创建失败），调用方可以安全回退到 SQL 写入；
 * 若已开始写行后失败，则抛出 {@link SQLException}，调用方不得回退，避免重复数据。</p>
 */
public final class DuckDbAppenderWriter {

    private DuckDbAppenderWriter() {}

    /**
     * 当前事件类型是否允许使用 Appender。
     */
    public static boolean supports(String eventType) {
        if (eventType == null) {
            return false;
        }
        String type = eventType.toUpperCase(Locale.ROOT);
        return "INSERT".equals(type) || "APPEND".equals(type) || "OVERWRITE".equals(type);
    }

    /**
     * 尝试使用 Appender 写入一批记录。
     *
     * @param connection    目标连接（可以是 Hikari 代理，也可以原生 DuckDB 连接）
     * @param table         目标表元数据；{@code catalog} 非空时按 catalog.schema.table 定位（DuckLake）
     * @param records       待写入记录
     * @param columnMapping 目标字段 -> 源字段映射，可为 {@code null}
     * @return {@code true} 写入完成；{@code false} 未写入任何行，调用方可安全回退 SQL
     * @throws SQLException Appender 已开始写行后失败，可能已部分写入，调用方不得回退
     */
    public static boolean append(Connection connection, TableMeta table, JSONArray records,
                                 Map<String, String> columnMapping) throws SQLException {
        if (connection == null || table == null || records == null || records.isEmpty()) {
            return false;
        }

        DuckDBConnection duckDBConnection;
        try {
            Connection raw = connection instanceof HikariProxyConnection proxy
                ? DBUtils.getRawConnection(proxy)
                : connection;
            if (!(raw instanceof DuckDBConnection)) {
                return false;
            }
            duckDBConnection = (DuckDBConnection) raw;
        } catch (Exception e) {
            // 无法拿到原生连接时，交给调用方回退到 SQL 写入
            return false;
        }

        DuckDBAppender appender;
        try {
            appender = createAppender(duckDBConnection, table);
        } catch (Exception e) {
            // Appender 尚未创建成功，没有任何行写入，可安全回退
            return false;
        }

        try (DuckDBAppender target = appender) {
            for (Object o : records) {
                JSONObject record = (JSONObject) o;
                target.beginRow();
                for (ColumnMeta column : table.columns()) {
                    String sourceColumn = column.getName();
                    if (columnMapping != null) {
                        sourceColumn = columnMapping.getOrDefault(sourceColumn, sourceColumn);
                    }
                    DBUtils.appendValue(target, column, record.get(sourceColumn));
                }
                target.endRow();
            }
        } catch (SQLException | RuntimeException e) {
            // 已经进入写行阶段，可能已部分落盘：必须向上抛出，禁止调用方回退后重复写入
            throw e instanceof SQLException sqlException
                ? sqlException
                : new SQLException("DuckDB Appender 写入失败: " + e.getMessage(), e);
        }
        return true;
    }

    private static DuckDBAppender createAppender(DuckDBConnection connection, TableMeta table) throws SQLException {
        String catalog = trimToNull(table.getCatalog());
        String schema = trimToNull(table.getSchema());
        String name = table.getTable();
        if (name == null || name.isBlank()) {
            throw new SQLException("Appender 写入缺少目标表名");
        }
        if (catalog != null && schema != null) {
            return connection.createAppender(catalog, schema, name);
        }
        if (schema != null) {
            return connection.createAppender(schema, name);
        }
        return connection.createAppender(name);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
