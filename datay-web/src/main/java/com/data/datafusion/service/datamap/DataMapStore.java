package com.data.datafusion.service.datamap;

import com.data.datafusion.service.dto.datamap.DataMapEdgeDTO;
import com.data.datafusion.service.dto.datamap.DataMapFacetDTO;
import com.data.datafusion.service.dto.datamap.DataMapGraphDTO;
import com.data.datafusion.service.dto.datamap.DataMapIndexStatusDTO;
import com.data.datafusion.service.dto.datamap.DataMapNodeDTO;
import com.data.datafusion.service.dto.datamap.DataMapSearchItemDTO;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 数据地图索引存储 SPI。
 *
 * <p>当前默认实现为 DuckDB；后续可替换为 Elasticsearch（检索）+ Neo4j（图）等实现。
 * 实现类需要保证同一租户内节点、边与 token 的强一致替换。</p>
 */
public interface DataMapStore {
    String provider();

    void replaceTenant(String tenantId, TenantIndex index);

    DataMapIndexStatusDTO status(String tenantId);

    void updateStatus(String tenantId, String status, String message, long nodeCount, long edgeCount, String coverage);

    void markStale(String tenantId);

    SearchPage search(String tenantId, SearchQuery query);

    DataMapGraphDTO fullGraph(String tenantId, GraphQuery query);

    DataMapGraphDTO subgraph(
        String tenantId,
        String rootId,
        String direction,
        int depth,
        Set<String> types,
        int maxNodes,
        boolean includeColumns
    );

    Optional<DataMapNodeDTO> findNode(String tenantId, String nodeId);

    List<DataMapNodeDTO> children(String tenantId, String parentId);

    List<DataMapEdgeDTO> edges(String tenantId, String nodeId);

    Map<String, Long> typeCounts(String tenantId);

    long nodeCount(String tenantId);

    long edgeCount(String tenantId);

    record TenantIndex(
        List<DataMapNodeDTO> nodes,
        List<DataMapEdgeDTO> edges,
        List<TokenRow> tokens,
        String coverage,
        String message
    ) {}

    record TokenRow(String nodeId, String token, String field, double weight) {}

    record SearchQuery(String query, List<String> tokens, Set<String> types, String dataSourceId, int limit, int offset) {}

    record SearchPage(List<DataMapSearchItemDTO> items, long total, Map<String, List<DataMapFacetDTO>> facets, long tookMs) {}

    record GraphQuery(Set<String> types, String dataSourceId, boolean includeColumns, int maxNodes, int maxEdges) {}

    final class Status {
        public static final String EMPTY = "EMPTY";
        public static final String RUNNING = "RUNNING";
        public static final String READY = "READY";
        public static final String STALE = "STALE";
        public static final String FAILED = "FAILED";

        private Status() {}
    }
}
