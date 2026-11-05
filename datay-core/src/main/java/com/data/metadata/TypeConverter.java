package com.data.metadata;

import com.data.metadata.util.DBUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public interface TypeConverter {

    // 转换为目标物理类型
    ColumnMeta toTargetColumnType(ColumnMeta columnMeta);

    String generateColumnDDL(ColumnMeta column);

    String generateTableDDL(TableMeta tableMeta);

    // 新增：生成INSERT语句（append模式）
    String generateInsertSQL(TableMeta tableMeta);

    // 新增：生成UPSERT语句（update模式）
    String generateUpsertSQL(TableMeta tableMeta);

    // 新增：生成DELETE语句（delete模式）
    String generateDeleteSQL(TableMeta tableMeta);

    // 新增：生成REPLACE语句（replace模式）
    String generateReplaceSQL(TableMeta tableMeta);
    //    String generateSchemaDDL(TableMeta tableMeta);
    //    TableMeta getTableMetaData(Connection conn, String table) throws SQLException;

    /**
     * 按目标数据库方言绑定单个 SQL 参数。
     *
     * <p>默认实现只做通用值归一化；某些数据库（如 PostgreSQL 的 BOOLEAN/BIT/JSONB）
     * 需要特殊绑定方式，由对应方言覆写。</p>
     */
    default void bindParameter(PreparedStatement statement, int index, ColumnMeta column, Object value)
        throws SQLException {
        statement.setObject(index, DBUtils.normalizeParameter(column.getType(), value));
    }

    /**
     * 将从源库读取到的原始值转换为通用值。默认原样返回，
     * 源库特有的 JDBC 类型差异由对应方言覆写。
     */
    default Object normalizeSourceValue(ColumnMeta column, Object value) {
        return value;
    }

    /**
     * 补充源表元数据中 JDBC 标准接口取不到的信息（如 MySQL ENUM/SET 的成员定义）。
     * 默认无需补充。
     */
    default void enrichSourceTableMeta(Connection connection, String schema, String table, TableMeta tableMeta)
        throws SQLException {}

    /**
     * 将源库 DDL 转换为目标库可执行的 DDL。默认原样返回。
     */
    default String translateDDL(String sql, String sourceSchema, String targetSchema) {
        return sql;
    }

    /**
     * 执行 DDL 前为目标连接设置默认 schema。默认无需处理。
     */
    default void prepareDDLConnection(Connection connection, String targetSchema) throws SQLException {}

    // 返回此数据库支持的所有物理字段类型
    default List<String> getSupportedTypes() {
        return Collections.emptyList();
    }
}