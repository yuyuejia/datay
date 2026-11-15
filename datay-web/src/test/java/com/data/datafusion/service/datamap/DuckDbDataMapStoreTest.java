package com.data.datafusion.service.datamap;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.service.datamap.DataMapStore.SearchQuery;
import com.data.datafusion.service.datamap.DataMapStore.TenantIndex;
import com.data.datafusion.service.datamap.DataMapStore.TokenRow;
import com.data.datafusion.service.dto.datamap.DataMapAssetTypes;
import com.data.datafusion.service.dto.datamap.DataMapEdgeDTO;
import com.data.datafusion.service.dto.datamap.DataMapGraphDTO;
import com.data.datafusion.service.dto.datamap.DataMapNodeDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DuckDbDataMapStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldStoreSearchAndTraverseTenantGraph() {
        DataMapProperties properties = new DataMapProperties();
        properties.setDbFile(tempDir.resolve("data-map.duckdb").toString());
        DuckDbDataMapStore store = new DuckDbDataMapStore(properties, new ObjectMapper());
        store.init();

        DataMapNodeDTO source = node("source:ds1", DataMapAssetTypes.DATASOURCE, "订单库", "ds1", null, null);
        DataMapNodeDTO table = node("table:ds1:public.orders", DataMapAssetTypes.TABLE, "订单表", "ds1", "public", "orders");
        DataMapNodeDTO column = node("column:table:ds1:public.orders.order_id", DataMapAssetTypes.COLUMN, "order_id", "ds1", "public", "orders");
        DataMapNodeDTO etl = node("etl:task1", DataMapAssetTypes.ETL_TASK, "同步订单", null, null, null);

        List<DataMapEdgeDTO> edges = List.of(
            new DataMapEdgeDTO("e1", source.id(), table.id(), "CONTAINS", "包含", "CONFIG"),
            new DataMapEdgeDTO("e2", table.id(), column.id(), "CONTAINS", "包含字段", "CONFIG"),
            new DataMapEdgeDTO("e3", etl.id(), table.id(), "WRITES", "写入", "CONFIG")
        );
        List<TokenRow> tokens = List.of(
            new TokenRow(table.id(), "订单", "name", 8),
            new TokenRow(table.id(), "orders", "name", 8),
            new TokenRow(source.id(), "订单", "name", 8)
        );
        store.replaceTenant("1", new TenantIndex(List.of(source, table, column, etl), edges, tokens, "1/1", "ok"));

        assertThat(store.status("1").status()).isEqualTo(DataMapStore.Status.READY);
        assertThat(store.status("1").nodeCount()).isEqualTo(4);

        var page = store.search("1", new SearchQuery("订单", List.of("订单"), Set.of(), null, 20, 0));
        assertThat(page.items()).extracting(item -> item.node().id()).contains(table.id(), source.id());
        assertThat(page.total()).isGreaterThanOrEqualTo(2);

        DataMapGraphDTO fullGraph = store.fullGraph(
            "1",
            new DataMapStore.GraphQuery(Set.of(), null, true, 100, 100)
        );
        assertThat(fullGraph.nodes()).hasSize(4);
        assertThat(fullGraph.links()).hasSize(3);

        DataMapGraphDTO withoutColumns = store.fullGraph(
            "1",
            new DataMapStore.GraphQuery(Set.of(), null, false, 100, 100)
        );
        assertThat(withoutColumns.nodes()).extracting(DataMapNodeDTO::type).doesNotContain(DataMapAssetTypes.COLUMN);

        DataMapGraphDTO subgraph = store.subgraph("1", table.id(), "both", 1, Set.of(), 100, true);
        assertThat(subgraph.nodes()).extracting(DataMapNodeDTO::id).contains(source.id(), table.id(), column.id(), etl.id());
        assertThat(subgraph.nodes()).filteredOn(node -> node.id().equals(table.id())).extracting(DataMapNodeDTO::layer).contains(0);

        store.updateStatus("1", DataMapStore.Status.RUNNING, "重建中", 0, 0, null);
        assertThat(store.status("1").status()).isEqualTo(DataMapStore.Status.RUNNING);
        assertThat(store.status("1").nodeCount()).isEqualTo(4);

        store.updateStatus("1", DataMapStore.Status.READY, "完成", 4, 3, "1/1");
        store.markStale("1");
        assertThat(store.status("1").status()).isEqualTo(DataMapStore.Status.STALE);
    }

    private DataMapNodeDTO node(String id, String type, String name, String dataSourceId, String schema, String table) {
        return new DataMapNodeDTO(
            id,
            type,
            name,
            null,
            null,
            name,
            dataSourceId,
            dataSourceId == null ? null : "数据源",
            schema,
            table,
            0,
            null,
            null,
            0,
            Map.of()
        );
    }
}
