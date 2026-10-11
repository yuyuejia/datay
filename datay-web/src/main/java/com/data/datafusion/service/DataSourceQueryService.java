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

    private static final int MAX_ROWS = 10000;

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
        return execute(dataSource, sql, null);
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
        return execute(dataSource, sql, params);
    }

    private Map<String, Object> execute(DataSourceDTO dataSource, String sql, List<Object> params) throws SQLException {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL语句不能为空");
        }
        try (DataSourceConnection connection = DataSourceProviderFactory.connect(toDatasourceInfo(dataSource))) {
            QueryResult result = connection.executeQuery(sql, params, MAX_ROWS);
            return result.toResultMap();
        } catch (SQLException e) {
            LOG.error("SQL execution error", e);
            throw e;
        } catch (Exception e) {
            LOG.error("SQL execution error", e);
            throw new SQLException(e.getMessage(), e);
        }
    }
}
