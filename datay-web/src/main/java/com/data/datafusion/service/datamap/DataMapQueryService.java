package com.data.datafusion.service.datamap;

import com.data.datafusion.service.dto.datamap.DataMapAssetDetailDTO;
import com.data.datafusion.service.dto.datamap.DataMapAssetTypes;
import com.data.datafusion.service.dto.datamap.DataMapGraphDTO;
import com.data.datafusion.service.dto.datamap.DataMapIndexStatusDTO;
import com.data.datafusion.service.dto.datamap.DataMapNodeDTO;
import com.data.datafusion.service.dto.datamap.DataMapOverviewDTO;
import com.data.datafusion.service.dto.datamap.DataMapSearchResultDTO;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 数据地图查询服务，负责租户上下文、索引懒构建与存储层调用。
 */
@Service
public class DataMapQueryService {

    private final DataMapProperties properties;
    private final DataMapStore store;
    private final DataMapIndexService indexService;
    private final DataMapTokenizer tokenizer;

    public DataMapQueryService(
        DataMapProperties properties,
        DataMapStore store,
        DataMapIndexService indexService,
        DataMapTokenizer tokenizer
    ) {
        this.properties = properties;
        this.store = store;
        this.indexService = indexService;
        this.tokenizer = tokenizer;
    }

    public DataMapOverviewDTO overview() {
        String tenantId = requireTenantId();
        String tenantCode = indexService.currentTenantCode();
        indexService.ensureIndex(tenantId, tenantCode);
        DataMapIndexStatusDTO status = store.status(tenantId);
        return new DataMapOverviewDTO(
            properties.isEnabled(),
            status,
            store.nodeCount(tenantId),
            store.edgeCount(tenantId),
            store.typeCounts(tenantId),
            status.coverage()
        );
    }

    public DataMapIndexStatusDTO status() {
        return store.status(requireTenantId());
    }

    public DataMapSearchResultDTO search(
        String query,
        String types,
        String dataSourceId,
        Integer limit,
        Integer offset
    ) {
        String tenantId = requireTenantId();
        String tenantCode = indexService.currentTenantCode();
        indexService.ensureIndex(tenantId, tenantCode);
        DataMapIndexStatusDTO status = store.status(tenantId);
        int safeLimit = limit == null || limit <= 0 ? properties.getTopK() : Math.min(limit, 200);
        int safeOffset = offset == null || offset < 0 ? 0 : offset;
        boolean indexing =
            DataMapStore.Status.RUNNING.equals(status.status()) ||
            (DataMapStore.Status.EMPTY.equals(status.status()) && properties.isAutoBuild());
        if (
            (DataMapStore.Status.EMPTY.equals(status.status()) || DataMapStore.Status.RUNNING.equals(status.status())) &&
            status.nodeCount() == 0
        ) {
            return new DataMapSearchResultDTO(List.of(), 0, Map.of("types", List.of()), 0, indexing, status.status());
        }
        DataMapStore.SearchPage page = store.search(
            tenantId,
            new DataMapStore.SearchQuery(
                query,
                tokenizer.queryTokens(query),
                parseTypes(types),
                blankToNull(dataSourceId),
                safeLimit,
                safeOffset
            )
        );
        return new DataMapSearchResultDTO(
            page.items(),
            page.total(),
            page.facets(),
            page.tookMs(),
            indexing,
            status.status()
        );
    }

    public DataMapGraphDTO graph(
        String scope,
        String rootId,
        String direction,
        Integer depth,
        String types,
        String dataSourceId,
        Boolean includeColumns,
        Integer limit
    ) {
        String tenantId = requireTenantId();
        String tenantCode = indexService.currentTenantCode();
        indexService.ensureIndex(tenantId, tenantCode);
        boolean withColumns = includeColumns == null ? properties.isIncludeColumns() : includeColumns;
        if ("subgraph".equalsIgnoreCase(scope)) {
            String id = rootId;
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("聚焦模式需要提供 rootId");
            }
            int safeDepth = depth == null || depth <= 0 ? Math.min(2, properties.getMaxDepth()) : Math.min(depth, properties.getMaxDepth());
            int maxNodes = limit == null || limit <= 0 ? properties.getMaxSubgraphNodes() : Math.min(limit, properties.getMaxSubgraphNodes());
            return store.subgraph(tenantId, id, direction, safeDepth, parseTypes(types), maxNodes, withColumns);
        }
        int maxNodes = limit == null || limit <= 0 ? properties.getMaxFullNodes() : Math.min(limit, properties.getMaxFullNodes());
        return store.fullGraph(
            tenantId,
            new DataMapStore.GraphQuery(
                parseTypes(types),
                blankToNull(dataSourceId),
                withColumns,
                maxNodes,
                properties.getMaxFullEdges()
            )
        );
    }

    public DataMapAssetDetailDTO detail(String nodeId) {
        String tenantId = requireTenantId();
        String tenantCode = indexService.currentTenantCode();
        indexService.ensureIndex(tenantId, tenantCode);
        DataMapNodeDTO node = store
            .findNode(tenantId, nodeId)
            .orElseThrow(() -> new IllegalArgumentException("数据地图资产不存在: " + nodeId));
        List<DataMapNodeDTO> fields = store
            .children(tenantId, nodeId)
            .stream()
            .filter(child -> DataMapAssetTypes.isFieldType(child.type()))
            .toList();
        DataMapGraphDTO graph = store.subgraph(
            tenantId,
            nodeId,
            "both",
            1,
            Set.of(),
            properties.getMaxSubgraphNodes(),
            false
        );
        return new DataMapAssetDetailDTO(node, fields, graph, assetLink(node));
    }

    public List<DataMapNodeDTO> children(String parentId) {
        return store.children(requireTenantId(), parentId);
    }

    public Map<String, String> types() {
        return DataMapAssetTypes.labels();
    }

    public DataMapIndexStatusDTO rebuild() {
        String tenantId = requireTenantId();
        indexService.rebuildAsync(tenantId, indexService.currentTenantCode());
        return store.status(tenantId);
    }

    private Set<String> parseTypes(String types) {
        if (types == null || types.isBlank()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (String item : types.split(",")) {
            if (!item.isBlank()) {
                result.add(item.trim().toUpperCase(Locale.ROOT));
            }
        }
        return result;
    }

    private String requireTenantId() {
        String tenantId = indexService.currentTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalStateException("当前会话缺少租户信息");
        }
        return tenantId;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String assetLink(DataMapNodeDTO node) {
        String type = node.type();
        String id = refId(node.id());
        if (DataMapAssetTypes.DATASOURCE.equals(type) || DataMapAssetTypes.SCHEMA.equals(type)) {
            return "/data-source/" + node.dataSourceId() + "/view";
        }
        if (DataMapAssetTypes.TABLE.equals(type)) {
            return "/data-source/" + node.dataSourceId() + "/query";
        }
        if (DataMapAssetTypes.COLUMN.equals(type)) {
            return "/data-source/" + node.dataSourceId() + "/query";
        }
        if (DataMapAssetTypes.ETL_TASK.equals(type)) {
            return "/etl-task/" + id + "/design";
        }
        if (DataMapAssetTypes.DATA_SYNC.equals(type)) {
            return "/data-sync/" + id + "/view";
        }
        if (DataMapAssetTypes.DATA_MODEL.equals(type)) {
            return "/data-model";
        }
        if (DataMapAssetTypes.MODEL_FIELD.equals(type)) {
            return "/data-model";
        }
        if (DataMapAssetTypes.METRIC.equals(type)) {
            return "/metric";
        }
        if (DataMapAssetTypes.DATASET.equals(type)) {
            return "/analysis-dataset";
        }
        if (DataMapAssetTypes.DASHBOARD.equals(type)) {
            return "/analysis-dashboard/designer?id=" + id;
        }
        if (DataMapAssetTypes.DATA_API.equals(type)) {
            return "/data-api";
        }
        if (DataMapAssetTypes.QUALITY_TASK.equals(type)) {
            return "/data-quality/task";
        }
        return null;
    }

    private String refId(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        int index = nodeId.indexOf(':');
        return index >= 0 ? nodeId.substring(index + 1) : nodeId;
    }
}
