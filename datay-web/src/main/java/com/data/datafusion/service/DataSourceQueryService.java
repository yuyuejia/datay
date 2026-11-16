package com.data.datafusion.service;

import com.data.datasource.DataSourceConnection;
import com.data.datasource.DataSourceProviderFactory;
import com.data.datasource.QueryResult;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.job.DatasourceInfo;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Shared service used to execute queries against a registered data source.
 *
 * <p>Both the REST layer ({@code DataSourceResource}) and the MCP server reuse this
 * service so that query semantics stay consistent across entry points.
 *
 * <p>查询不再直接依赖 JDBC：通过 {@link DataSourceProviderFactory} 解析数据源类型对应的
 * {@link DataSourceConnection}，因此 JDBC 与 Elasticsearch 等非 JDBC 数据源共用同一入口。
 */
@Service
public class DataSourceQueryService {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceQueryService.class);

    /** 单次查询返回行数的硬上限，任何入口传入的限制都会被收敛到该值以内。 */
    @Value("${datay.query.max-rows:10000}")
    private int maxRows = 10000;

    /** 数据查询入口未显式指定行数时使用的默认值，避免无条数限制的全量查询。 */
    @Value("${datay.query.default-rows:1000}")
    private int defaultRows = 1000;

    /**
     * Converts a {@link DataSourceDTO} into the {@link DatasourceInfo} used by the
     * underlying data source providers.
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
     * Executes the given statement against the supplied data source and returns a result map
     * containing {@code columns}, {@code rows} and {@code affectedRows}.
     *
     * @param dataSource the resolved data source to query against
     * @param sql the statement to execute
     * @return a result map with {@code columns}, {@code rows} and {@code affectedRows}
     * @throws IllegalArgumentException if the statement is blank
     * @throws SQLException if the execution fails
     */
    public Map<String, Object> executeQuery(DataSourceDTO dataSource, String sql) throws SQLException {
        return execute(dataSource, sql, null, Math.max(1, maxRows));
    }

    /**
     * 数据查询入口：按调用方指定的行数执行只读查询。
     *
     * <p>未传 {@code rowLimit} 时使用默认行数，且始终收敛到 {@link #maxRows} 以内，
     * 避免用户不写 LIMIT 时触发全量查询，造成服务内存溢出与数据库高负载。
     *
     * @param dataSource 目标数据源
     * @param sql        查询语句
     * @param rowLimit   期望的最大返回行数，可为 {@code null}
     * @return 结果 map，包含 {@code columns} / {@code rows} / {@code affectedRows} 等
     */
    public Map<String, Object> executeQuery(DataSourceDTO dataSource, String sql, Integer rowLimit) throws SQLException {
        return execute(dataSource, sql, null, resolveRowLimit(rowLimit));
    }

    /**
     * 以预编译参数执行只读查询，用于看板数据集等需要绑定筛选参数的安全查询。
     *
     * @param dataSource 目标数据源
     * @param sql        含 {@code ?} 占位符的只读语句
     * @param params     与占位符顺序一致的绑定参数
     * @return 结果 map，包含 {@code columns} / {@code rows} / {@code affectedRows}
     */
    public Map<String, Object> executeReadOnlyQuery(DataSourceDTO dataSource, String sql, List<Object> params) throws SQLException {
        return execute(dataSource, sql, params, Math.max(1, maxRows));
    }

    /**
     * 把入口传入的行数限制收敛到 [{@code 1}, {@code maxRows}] 区间。
     */
    private int resolveRowLimit(Integer rowLimit) {
        int cap = Math.max(1, maxRows);
        int fallback = defaultRows > 0 ? defaultRows : cap;
        int requested = rowLimit == null ? fallback : rowLimit;
        return Math.max(1, Math.min(requested, cap));
    }

    private Map<String, Object> execute(DataSourceDTO dataSource, String sql, List<Object> params, int rowLimit) throws SQLException {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL语句不能为空");
        }
        try (DataSourceConnection connection = DataSourceProviderFactory.connect(toDatasourceInfo(dataSource))) {
            QueryResult result = connection.executeQuery(sql, params, rowLimit);
            Map<String, Object> map = result.toResultMap();
            map.put("rowLimit", rowLimit);
            if (map.get("rows") instanceof List<?> rows && rows.size() >= rowLimit) {
                map.put("truncated", true);
            }
            return map;
        } catch (SQLException e) {
            LOG.error("SQL execution error", e);
            throw e;
        } catch (Exception e) {
            LOG.error("SQL execution error", e);
            throw new SQLException(e.getMessage(), e);
        }
    }
}
