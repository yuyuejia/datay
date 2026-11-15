package com.data.datafusion.service.datamap;

import com.data.datafusion.config.DeploymentProperties;
import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.datamap.DataMapGraphAssembler.GraphBuildResult;
import com.data.datafusion.service.datamap.DataMapStore.TenantIndex;
import com.data.datafusion.service.datamap.DataMapStore.TokenRow;
import com.data.datafusion.service.datamap.DataMapTokenizer.DataMapToken;
import com.data.datafusion.service.dto.datamap.DataMapIndexStatusDTO;
import com.data.datafusion.service.dto.datamap.DataMapNodeDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 数据地图索引构建编排。
 *
 * <p>索引是派生数据，可随时重建；同一租户同时只允许一个构建任务。</p>
 */
@Service
public class DataMapIndexService {

    private static final Logger LOG = LoggerFactory.getLogger(DataMapIndexService.class);

    private final DataMapProperties properties;
    private final DataMapStore store;
    private final DataMapGraphAssembler graphAssembler;
    private final DataMapTokenizer tokenizer;
    private final ObjectProvider<Executor> executorProvider;
    private final Set<String> runningTenants = ConcurrentHashMap.newKeySet();

    public DataMapIndexService(
        DataMapProperties properties,
        DataMapStore store,
        DataMapGraphAssembler graphAssembler,
        DataMapTokenizer tokenizer,
        @Qualifier("taskExecutor") ObjectProvider<Executor> executorProvider
    ) {
        this.properties = properties;
        this.store = store;
        this.graphAssembler = graphAssembler;
        this.tokenizer = tokenizer;
        this.executorProvider = executorProvider;
    }

    /**
     * 当前租户索引未就绪时按配置触发后台构建。
     */
    public void ensureIndex(String tenantId, String tenantCode) {
        if (!properties.isEnabled() || tenantId == null || tenantId.isBlank()) {
            return;
        }
        DataMapIndexStatusDTO status = store.status(tenantId);
        if (
            DataMapStore.Status.READY.equals(status.status()) ||
            DataMapStore.Status.RUNNING.equals(status.status())
        ) {
            return;
        }
        boolean stale = DataMapStore.Status.STALE.equals(status.status());
        boolean empty = DataMapStore.Status.EMPTY.equals(status.status());
        if ((stale || empty) && properties.isAutoBuild()) {
            rebuildAsync(tenantId, tenantCode);
        }
    }

    public boolean rebuildAsync(String tenantId, String tenantCode) {
        if (!properties.isEnabled() || tenantId == null || tenantId.isBlank()) {
            return false;
        }
        if (!runningTenants.add(tenantId)) {
            return false;
        }
        Long tenantIdValue = parseTenantId(tenantId);
        store.updateStatus(tenantId, DataMapStore.Status.RUNNING, "索引构建中", 0, 0, null);
        try {
            executor().execute(() -> {
                try {
                    TenantContext.setTenantId(tenantIdValue);
                    TenantContext.setTenantCode(tenantCode);
                    rebuild(tenantId, tenantCode);
                } catch (Exception e) {
                    LOG.error("数据地图索引构建失败 tenant={}", tenantId, e);
                    store.updateStatus(tenantId, DataMapStore.Status.FAILED, e.getMessage(), 0, 0, null);
                } finally {
                    TenantContext.clear();
                    runningTenants.remove(tenantId);
                }
            });
        } catch (RuntimeException e) {
            runningTenants.remove(tenantId);
            store.updateStatus(tenantId, DataMapStore.Status.FAILED, "无法提交索引构建任务: " + e.getMessage(), 0, 0, null);
            LOG.warn("提交数据地图索引构建任务失败 tenant={}: {}", tenantId, e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * 同步重建，供内部调用与测试使用。
     */
    public void rebuild(String tenantId, String tenantCode) {
        long started = System.currentTimeMillis();
        GraphBuildResult result = graphAssembler.build(tenantCode);
        List<TokenRow> tokens = new ArrayList<>();
        for (DataMapNodeDTO node : result.nodes()) {
            for (DataMapToken token : tokenizer.tokensFor(node)) {
                tokens.add(new TokenRow(node.id(), token.token(), token.field(), token.weight()));
            }
        }
        String message = "节点 " + result.nodes().size() + "，关系 " + result.edges().size() + "，耗时 " + (System.currentTimeMillis() - started) + " ms";
        store.replaceTenant(
            tenantId,
            new TenantIndex(result.nodes(), result.edges(), tokens, result.coverage(), message)
        );
        LOG.info("数据地图索引已更新 tenant={}, {}", tenantId, message);
    }

    public DataMapIndexStatusDTO status(String tenantId) {
        return store.status(tenantId);
    }

    public void markStale(String tenantId) {
        if (!properties.isEnabled() || !properties.isRebuildOnMetadataCollect() || tenantId == null) {
            return;
        }
        // 纯 worker 节点没有 web 索引文件，标记过期没有意义，还会产生孤立 DuckDB 文件。
        if (!DeploymentProperties.get().isSchedulerEnabled()) {
            return;
        }
        store.markStale(tenantId);
    }

    public String currentTenantId() {
        return SecurityUtils.getCurrentTenantIdString().orElse(null);
    }

    public String currentTenantCode() {
        String code = TenantContext.getTenantCode();
        if (code != null && !code.isBlank()) {
            return code;
        }
        return SecurityUtils.getCurrentTenantCode().orElse(null);
    }

    private Executor executor() {
        Executor executor = executorProvider.getIfAvailable();
        return executor == null ? ForkJoinPool.commonPool() : executor;
    }

    private Long parseTenantId(String tenantId) {
        try {
            return Long.valueOf(tenantId);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
