package com.data.datasource;

import com.data.metadata.TableMeta;
import java.util.List;

/**
 * 与具体数据源无关的连接抽象。
 *
 * <p>JDBC 数据源由 {@code JdbcDataSourceConnection} 基于 {@link java.sql.Connection} 实现，
 * 非 JDBC 数据源（如 Elasticsearch）基于各自的协议实现。上层的数据源工具（查询、元数据浏览、
 * 连通性测试）统一面向本接口，因此新增数据源类型无需改动这些调用点。
 */
public interface DataSourceConnection extends AutoCloseable {

    /**
     * 执行查询并返回统一结果模型。
     *
     * @param statement 查询语句（JDBC 数据源为 SQL，非 JDBC 数据源可为各自的查询语法）
     * @param params    绑定参数，可为 {@code null}
     * @param maxRows   最大返回行数，{@code <= 0} 表示不限制
     */
    QueryResult executeQuery(String statement, List<Object> params, int maxRows) throws Exception;

    /**
     * 执行写操作（INSERT / UPDATE / DELETE / DDL 等），返回影响行数。
     */
    int executeUpdate(String statement, List<Object> params) throws Exception;

    /**
     * 列出 catalog（数据库实例 / 挂载库）。默认无 catalog 概念，返回空列表。
     *
     * <p>DuckDB 等支持 {@code ATTACH} 的数据源会返回主库与已挂载库；
     * 普通关系型数据库可继续按 {@link #getSchemas()} 使用 schema/数据库概念。
     */
    default List<String> getCatalogs() throws Exception {
        return List.of();
    }

    /**
     * 列出 schema / 数据库 / 命名空间。
     */
    List<String> getSchemas() throws Exception;

    /**
     * 列出指定 catalog 下的 schema。默认忽略 catalog，退化为 {@link #getSchemas()}。
     */
    default List<String> getSchemas(String catalog) throws Exception {
        return getSchemas();
    }

    /**
     * 列出指定 schema 下的表清单。
     *
     * @param schema schema / 数据库名，可为 {@code null}
     * @param limit  最大返回数量，可为 {@code null}
     * @param search 表名模糊过滤关键字，可为 {@code null}
     */
    List<TableMeta> getTables(String schema, Integer limit, String search) throws Exception;

    /**
     * 列出指定 catalog + schema 下的表清单。默认忽略 catalog。
     */
    default List<TableMeta> getTables(String catalog, String schema, Integer limit, String search) throws Exception {
        return getTables(schema, limit, search);
    }

    /**
     * 获取表结构（字段清单等）。
     */
    TableMeta getTableMeta(String schema, String table) throws Exception;

    /**
     * 获取指定 catalog 下表结构。默认忽略 catalog。
     */
    default TableMeta getTableMeta(String catalog, String schema, String table) throws Exception {
        return getTableMeta(schema, table);
    }

    /**
     * 获取表详细信息（字段、索引、注释等）。默认与 {@link #getTableMeta} 相同，
     * 支持索引的数据源可覆写。
     */
    default TableMeta getTableDetail(String schema, String table) throws Exception {
        return getTableMeta(schema, table);
    }

    /**
     * 获取指定 catalog 下表的详细信息。默认忽略 catalog。
     */
    default TableMeta getTableDetail(String catalog, String schema, String table) throws Exception {
        return getTableDetail(schema, table);
    }

    /**
     * 测试连接是否可用。
     */
    boolean testConnection() throws Exception;

    @Override
    default void close() throws Exception {}
}
