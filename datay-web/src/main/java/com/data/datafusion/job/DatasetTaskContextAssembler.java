package com.data.datafusion.job;

import com.alibaba.fastjson2.JSONObject;
import com.data.datafusion.config.SpringUtil;
import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.DatasetPersistenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 数据集任务下发器。
 *
 * <p>数据集任务保存时只记录数据集 id，不保存指标 SQL 快照。任务真正下发前，
 * 根据最新数据集定义重新生成 ETL 节点与 jobContext，避免指标口径或数据源变化后
 * 仍执行旧 SQL。
 */
public final class DatasetTaskContextAssembler {

    private static final Logger LOG = LoggerFactory.getLogger(DatasetTaskContextAssembler.class);

    private DatasetTaskContextAssembler() {}

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
        String datasetId = config == null ? null : config.getString("datasetId");
        if (datasetId == null || datasetId.isBlank()) {
            return jobContext;
        }

        boolean tenantSet = false;
        try {
            if (tenantId != null && !tenantId.isEmpty()) {
                TenantContext.setTenantId(Long.valueOf(tenantId));
                tenantSet = true;
            }
            AnalysisDataset dataset = SpringUtil
                .getBean(AnalysisDatasetRepository.class)
                .findById(datasetId)
                .orElseThrow(() -> new IllegalStateException("数据集持久化任务绑定的数据集不存在: " + datasetId));
            return SpringUtil.getBean(DatasetPersistenceService.class).buildJobContext(dataset);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            LOG.warn("Assemble dataset task context failed for dataset {}: {}", datasetId, e.getMessage(), e);
            throw new IllegalStateException("组装数据集任务上下文失败: " + e.getMessage(), e);
        } finally {
            if (tenantSet) {
                TenantContext.clear();
            }
        }
    }
}
