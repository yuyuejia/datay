package com.data.datafusion.job.sql;

import com.alibaba.fastjson2.JSONObject;
import com.data.datafusion.config.SpringUtil;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.metadata.util.DBUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SQL 任务下发器。
 *
 * <p>SQL 任务保存时只记录数据源 id（{@code dataSourceId}），工作线程无法访问数据库查询
 * 数据源信息。因此任务下发（生成任务实例）前由本类将数据源连接信息组装进
 * {@code jobContext}，供 {@link SqlTask} 直接使用，避免在工作线程中查询数据源。
 */
public final class SqlTaskContextAssembler {

    private static final Logger LOG = LoggerFactory.getLogger(SqlTaskContextAssembler.class);

    private SqlTaskContextAssembler() {}

    /**
     * 将数据源连接信息组装进 SQL 任务的 jobContext。
     *
     * <p>仅当 jobContext 中包含 {@code dataSourceId} 且尚未组装（不存在 {@code dataSource} 字段）
     * 时进行组装；其余类型或已组装的任务原样返回。
     *
     * @param jobContext 任务上下文 JSON
     * @param tenantId   任务所属租户 id，用于查询数据源
     * @return 组装后的任务上下文 JSON
     */
    public static String assemble(String jobContext, String tenantId) {
        if (jobContext == null || jobContext.trim().isEmpty()) {
            return jobContext;
        }
        JSONObject config;
        try {
            config = JSONObject.parseObject(jobContext);
        } catch (Exception e) {
            return jobContext;
        }
        if (config == null || config.containsKey("dataSource")) {
            return jobContext;
        }
        Long dataSourceId = config.getLong("dataSourceId");
        if (dataSourceId == null) {
            return jobContext;
        }

        DataSourceService dataSourceService = SpringUtil.getBean(DataSourceService.class);
        boolean tenantSet = false;
        try {
            if (tenantId != null && !tenantId.isEmpty()) {
                TenantContext.setTenantId(Long.valueOf(tenantId));
                tenantSet = true;
            }
            DataSourceDTO dataSource = dataSourceService
                .findOne(dataSourceId)
                .orElseThrow(() -> new IllegalStateException("SQL 任务绑定的数据源不存在: " + dataSourceId));

            JSONObject connection = new JSONObject();
            connection.put("type", dataSource.getType());
            connection.put("url", dataSource.getUrl());
            connection.put("hostname", dataSource.getHostname());
            connection.put("port", dataSource.getPort());
            connection.put("username", dataSource.getUsername());
            connection.put("password", dataSource.getPassword());
            String schema = config.getString("schema");
            connection.put("dbschema", schema != null && !schema.isEmpty() ? schema : dataSource.getSchemaName());
            connection.put("driver", DBUtils.getDriverClassName(dataSource.getUrl()));
            config.put("dataSource", connection);
            return config.toJSONString();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("Assemble SQL task data source info failed: {}", e.getMessage(), e);
            throw new IllegalStateException("组装 SQL 任务数据源信息失败: " + e.getMessage(), e);
        } finally {
            if (tenantSet) {
                TenantContext.clear();
            }
        }
    }
}
