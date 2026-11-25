package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.datasource.provider.jdbc.JdbcMetadata;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DBType;
import com.data.metadata.TableMeta;
import com.data.metadata.util.JdbcValueNormalizer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * ClickHouse 数据源 Provider，复用 {@link JdbcDataSourceProvider} 的 JDBC 标准能力。
 *
 * <p>ClickHouse 的 JDBC 元数据模型与标准 schema 模型不同：database 对应 JDBC catalog，
 * 而 schema 参数会被驱动忽略。因此这里统一把 database 当作 schema 对外暴露，并在读取表时
 * 显式使用 catalog 过滤，避免跨库表泄漏。</p>
 */
@DataSourceRegister("clickhouse")
public class ClickHouseDataSourceProvider extends JdbcDataSourceProvider {

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.CLICKHOUSE);
    }

    @Override
    public List<String> getCatalogs(Connection conn) throws SQLException {
        return databases(conn);
    }

    @Override
    public List<String> getSchemas(Connection conn) throws SQLException {
        return databases(conn);
    }

    @Override
    public List<String> getSchemas(Connection conn, String catalog) throws SQLException {
        return databases(conn);
    }

    @Override
    public List<TableMeta> getTables(Connection conn, String schema, Integer limit, String search) throws SQLException {
        String database = resolveDatabase(conn, null, schema);
        return readTables(conn, database, limit, search);
    }

    @Override
    public List<TableMeta> getTables(Connection conn, String catalog, String schema, Integer limit, String search)
        throws SQLException {
        String database = resolveDatabase(conn, catalog, schema);
        return readTables(conn, database, limit, search);
    }

    @Override
    public TableMeta getTableMeta(Connection conn, String schema, String table) throws SQLException {
        String database = resolveDatabase(conn, null, schema);
        TableMeta tableMeta = super.getTableMeta(conn, database, table);
        enrichTableMeta(conn, database, table, tableMeta);
        return tableMeta;
    }

    @Override
    public TableMeta getTableMeta(Connection conn, String catalog, String schema, String table) throws SQLException {
        String database = resolveDatabase(conn, catalog, schema);
        TableMeta tableMeta = super.getTableMeta(conn, database, table);
        enrichTableMeta(conn, database, table, tableMeta);
        return tableMeta;
    }

    @Override
    public TableMeta getTableDetail(Connection conn, String schema, String table) throws SQLException {
        String database = resolveDatabase(conn, null, schema);
        TableMeta tableMeta = super.getTableDetail(conn, database, table);
        enrichTableMeta(conn, database, table, tableMeta);
        return tableMeta;
    }

    @Override
    public TableMeta getTableDetail(Connection conn, String catalog, String schema, String table) throws SQLException {
        String database = resolveDatabase(conn, catalog, schema);
        TableMeta tableMeta = super.getTableDetail(conn, database, table);
        enrichTableMeta(conn, database, table, tableMeta);
        return tableMeta;
    }

    @Override
    public boolean tableExists(Connection conn, String schema, String table) throws SQLException {
        String database = resolveDatabase(conn, null, schema);
        return tableExists(conn, database, null, table);
    }

    @Override
    public boolean useTruncateForOverwrite() {
        return true;
    }

    @Override
    public Object normalizeReadValue(ColumnMeta column, Object value) {
        return JdbcValueNormalizer.normalize(value);
    }

    @Override
    public Object readSourceValue(ResultSet rs, int index, int sqlType, String typeName, ColumnMeta column)
        throws SQLException {
        return JdbcValueNormalizer.normalize(super.readSourceValue(rs, index, sqlType, typeName, column));
    }

    private List<TableMeta> readTables(Connection conn, String database, Integer limit, String search) throws SQLException {
        List<TableMeta> tables = JdbcMetadata.readTableList(conn, database, null, new String[] { "TABLE", "VIEW" }, limit, search, false);
        for (TableMeta table : tables) {
            table.setSchema(database);
            table.setCatalog(database);
        }
        return tables;
    }

    /**
     * ClickHouse JDBC 的 getPrimaryKeys 通常为空；MergeTree 的主键/排序键需要从 system.columns 补齐。
     */
    private void enrichTableMeta(Connection conn, String database, String table, TableMeta tableMeta) {
        if (tableMeta == null) {
            return;
        }
        tableMeta.setSchema(database);
        tableMeta.setCatalog(database);

        Set<String> primaryKeys = new LinkedHashSet<>();
        Set<String> sortingKeys = new LinkedHashSet<>();
        try (
            PreparedStatement ps = conn.prepareStatement(
                "SELECT name, is_in_primary_key, is_in_sorting_key FROM system.columns WHERE database = ? AND table = ?"
            )
        ) {
            ps.setString(1, database);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("name");
                    if (rs.getInt("is_in_primary_key") == 1) {
                        primaryKeys.add(name);
                    }
                    if (rs.getInt("is_in_sorting_key") == 1) {
                        sortingKeys.add(name);
                    }
                }
            }
        } catch (SQLException | RuntimeException e) {
            // 老版本或权限不足时忽略，主键不是读取表结构的必要条件
        }

        Set<String> effectiveKeys = primaryKeys.isEmpty() ? sortingKeys : primaryKeys;
        if (!effectiveKeys.isEmpty()) {
            for (ColumnMeta column : tableMeta.columns()) {
                if (effectiveKeys.contains(column.getName())) {
                    column.setPrimaryKey(true);
                }
            }
        }

        try (PreparedStatement ps = conn.prepareStatement("SELECT comment FROM system.tables WHERE database = ? AND name = ?")) {
            ps.setString(1, database);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String comment = rs.getString("comment");
                    if (comment != null && !comment.isBlank()) {
                        tableMeta.setComment(comment);
                    }
                }
            }
        } catch (SQLException | RuntimeException e) {
            // 注释读取失败不影响表结构
        }
    }

    /**
     * ClickHouse database 列表。驱动会同时返回 INFORMATION_SCHEMA 与 information_schema，
     * 这里按忽略大小写去重，并优先保留真实的小写库名。
     */
    private List<String> databases(Connection conn) throws SQLException {
        Map<String, String> byLower = new LinkedHashMap<>();
        for (String catalog : JdbcMetadata.getCatalogs(conn)) {
            if (catalog == null || catalog.isBlank()) {
                continue;
            }
            String lower = catalog.toLowerCase(Locale.ROOT);
            String existing = byLower.get(lower);
            if (existing == null || (catalog.equals(lower) && !existing.equals(lower))) {
                byLower.put(lower, catalog);
            }
        }
        return new ArrayList<>(byLower.values());
    }

    private static String resolveDatabase(Connection conn, String catalog, String schema) throws SQLException {
        if (schema != null && !schema.isBlank()) {
            return schema;
        }
        if (catalog != null && !catalog.isBlank()) {
            return catalog;
        }
        String current = conn.getCatalog();
        return current == null || current.isBlank() ? "default" : current;
    }
}
