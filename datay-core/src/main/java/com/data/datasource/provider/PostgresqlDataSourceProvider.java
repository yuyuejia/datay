package com.data.datasource.provider;

import com.data.datasource.DataSourceRegister;
import com.data.datasource.DataSourceTypeDescriptor;
import com.data.datasource.provider.jdbc.JdbcMetadata;
import com.data.job.DatasourceInfo;
import com.data.metadata.ColumnMeta;
import com.data.metadata.DBType;
import com.data.metadata.TableMeta;
import com.data.metadata.impl.PostgresqlConverter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * PostgreSQL 数据源 Provider，复用 {@link JdbcDataSourceProvider} 的 JDBC 标准能力。
 *
 * <p>表元数据以 {@code (null, schema, table)} 组合读取。
 */
@DataSourceRegister("postgresql")
public class PostgresqlDataSourceProvider extends JdbcDataSourceProvider {

    @Override
    protected DataSourceTypeDescriptor descriptor() {
        return descriptorOf(DBType.POSTGRESQL);
    }

    @Override
    public String normalizeTargetTableName(DatasourceInfo info, String tableName) {
        // PostgreSQL 自动建表使用小写表名，DDL 与后续 DML/元数据回读保持同一名称
        return tableName == null ? null : tableName.toLowerCase(Locale.ROOT);
    }

    @Override
    public Map<String, String> dataSourceProperties() {
        Map<String, String> props = new LinkedHashMap<>();
        // 批量写入时将多条 INSERT 重写为单条多值 INSERT，减少网络往返与服务端解析。
        // 仅影响批量 INSERT，不影响查询、UPDATE、DELETE；实测 PG→PG 全量同步吞吐提升约 15%。
        props.put("reWriteBatchedInserts", "true");
        return props;
    }

    @Override
    public TableMeta getTableMeta(Connection conn, String schema, String table) throws SQLException {
        TableMeta tableMeta = JdbcMetadata.readTableMeta(conn, null, schema, table);
        normalizeColumnTypes(tableMeta);
        tableMeta.setDbType(typeName());
        return tableMeta;
    }

    @Override
    public TableMeta getTableDetail(Connection conn, String schema, String table) throws SQLException {
        TableMeta tableMeta = JdbcMetadata.readTableMeta(conn, null, schema, table);
        normalizeColumnTypes(tableMeta);
        tableMeta.setDbType(typeName());
        tableMeta.setComment(JdbcMetadata.getTableComment(conn, null, schema, table));
        tableMeta.setIndexes(JdbcMetadata.getTableIndexes(conn, null, schema, table));
        return tableMeta;
    }

    /**
     * 将 pgJDBC 返回的内部类型名（如 {@code int8}）归一化为标准 SQL 类型，
     * 避免下游方言把 PG 的 64 位整数误判为 ClickHouse {@code Int8}。
     */
    private static void normalizeColumnTypes(TableMeta tableMeta) {
        if (tableMeta == null || tableMeta.columns() == null) {
            return;
        }
        for (ColumnMeta column : tableMeta.columns()) {
            column.setType(PostgresqlConverter.normalizeSourceTypeName(column.getType()));
        }
    }

    @Override
    public boolean tableExists(Connection conn, String schema, String table) throws SQLException {
        return tableExists(conn, null, schema, table);
    }
}
