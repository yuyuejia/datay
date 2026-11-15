package com.data.datafusion.service.datamap;

import com.data.datafusion.service.dto.datamap.DataMapAssetTypes;
import com.data.datafusion.service.dto.datamap.DataMapEdgeDTO;
import com.data.datafusion.service.dto.datamap.DataMapFacetDTO;
import com.data.datafusion.service.dto.datamap.DataMapGraphDTO;
import com.data.datafusion.service.dto.datamap.DataMapIndexStatusDTO;
import com.data.datafusion.service.dto.datamap.DataMapNodeDTO;
import com.data.datafusion.service.dto.datamap.DataMapSearchItemDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DuckDB 数据地图索引实现。
 *
 * <p>节点、边与全文 token 存放在独立 DuckDB 文件中；写操作串行化，
 * 读操作使用独立连接。后续新增其他实现时，只需提供新的 {@link DataMapStore} Bean。</p>
 */
@Component
@ConditionalOnProperty(name = "datay.data-map.provider", havingValue = "duckdb", matchIfMissing = true)
public class DuckDbDataMapStore implements DataMapStore {

    private static final Logger LOG = LoggerFactory.getLogger(DuckDbDataMapStore.class);

    private static final String TABLE_NODE = "data_map_node";
    private static final String TABLE_EDGE = "data_map_edge";
    private static final String TABLE_TOKEN = "data_map_token";
    private static final String TABLE_STATUS = "data_map_index_status";

    private final DataMapProperties properties;
    private final ObjectMapper objectMapper;
    private final ReentrantLock writeLock = new ReentrantLock();

    private volatile boolean schemaReady = false;

    public DuckDbDataMapStore(DataMapProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public String provider() {
        return "duckdb";
    }

    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            ensureSchema();
        } catch (SQLException e) {
            LOG.warn("初始化数据地图 DuckDB 索引失败: {}", e.getMessage());
        }
    }

    @Override
    public void replaceTenant(String tenantId, TenantIndex index) {
        writeLock.lock();
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                deleteTenantData(connection, tenantId);
                insertNodes(connection, tenantId, index.nodes());
                insertEdges(connection, tenantId, index.edges());
                insertTokens(connection, tenantId, index.tokens());
                writeStatus(
                    connection,
                    tenantId,
                    Status.READY,
                    Instant.now().toString(),
                    Instant.now().toString(),
                    index.nodes().size(),
                    index.edges().size(),
                    index.message(),
                    index.coverage()
                );
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (Exception e) {
            throw new IllegalStateException("写入数据地图索引失败: " + e.getMessage(), e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public DataMapIndexStatusDTO status(String tenantId) {
        try (Connection connection = connect()) {
            ensureSchema(connection);
            String sql = "SELECT * FROM " + TABLE_STATUS + " WHERE tenant_id = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return new DataMapIndexStatusDTO(Status.EMPTY, null, null, 0, 0, null, false, null);
                    }
                    String status = rs.getString("status");
                    return new DataMapIndexStatusDTO(
                        status,
                        rs.getString("started_at"),
                        rs.getString("finished_at"),
                        rs.getLong("node_count"),
                        rs.getLong("edge_count"),
                        rs.getString("message"),
                        Status.STALE.equals(status) || Status.FAILED.equals(status),
                        rs.getString("coverage")
                    );
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取数据地图索引状态失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateStatus(String tenantId, String status, String message, long nodeCount, long edgeCount, String coverage) {
        writeLock.lock();
        try (Connection connection = connect()) {
            ensureSchema(connection);
            DataMapIndexStatusDTO existing = status(tenantId);
            boolean running = Status.RUNNING.equals(status);
            boolean preserveCounts = running || Status.FAILED.equals(status);
            String startedAt = running ? Instant.now().toString() : existing.startedAt();
            String finishedAt = running ? null : Instant.now().toString();
            // 构建期间/构建失败时保留上一版索引的统计值，旧索引仍可被检索。
            long effectiveNodes = preserveCounts && existing.nodeCount() > 0 ? existing.nodeCount() : nodeCount;
            long effectiveEdges = preserveCounts && existing.edgeCount() > 0 ? existing.edgeCount() : edgeCount;
            String effectiveCoverage = coverage == null ? existing.coverage() : coverage;
            writeStatus(
                connection,
                tenantId,
                status,
                startedAt,
                finishedAt,
                effectiveNodes,
                effectiveEdges,
                message,
                effectiveCoverage
            );
        } catch (Exception e) {
            throw new IllegalStateException("更新数据地图索引状态失败: " + e.getMessage(), e);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void markStale(String tenantId) {
        writeLock.lock();
        try (Connection connection = connect()) {
            ensureSchema(connection);
            String sql = "UPDATE " + TABLE_STATUS + " SET status = ? WHERE tenant_id = ? AND status = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, Status.STALE);
                ps.setString(2, tenantId);
                ps.setString(3, Status.READY);
                ps.executeUpdate();
            }
        } catch (Exception e) {
            LOG.warn("标记数据地图索引过期失败: {}", e.getMessage());
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public SearchPage search(String tenantId, SearchQuery query) {
        long started = System.nanoTime();
        ensureSchemaQuietly();
        List<String> tokens = query.tokens() == null ? List.of() : query.tokens();
        if (tokens.isEmpty()) {
            return recentSearch(tenantId, query, started);
        }
        FilterSql filter = buildFilter(tenantId, query.types(), query.dataSourceId(), null, true);
        String tokenPlaceholders = String.join(",", Collections.nCopies(tokens.size(), "?"));
        String where = filter.sql() + " AND t.token IN (" + tokenPlaceholders + ")";
        String sql =
            "SELECT n.*, SUM(t.weight) AS score, COUNT(DISTINCT t.token) AS hit_count, " +
            "MAX(CASE WHEN lower(n.name) = lower(?) THEN 1 ELSE 0 END) AS exact_name, " +
            "MAX(CASE WHEN lower(n.name) LIKE lower(?) THEN 1 ELSE 0 END) AS prefix_name " +
            "FROM " +
            TABLE_NODE +
            " n JOIN " +
            TABLE_TOKEN +
            " t ON n.tenant_id = t.tenant_id AND n.node_id = t.node_id " +
            where +
            " GROUP BY ALL " +
            "ORDER BY hit_count DESC, exact_name DESC, prefix_name DESC, score DESC, n.degree DESC, n.name ASC " +
            "LIMIT ? OFFSET ?";

        List<Object> params = new ArrayList<>();
        params.add(query.query());
        params.add(escapeLike(query.query()) + "%");
        params.addAll(filter.params());
        params.addAll(tokens);
        params.add(query.limit());
        params.add(query.offset());

        List<DataMapSearchItemDTO> items = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DataMapNodeDTO node = mapNode(rs);
                    double score = rs.getDouble("score") + rs.getInt("exact_name") * 100.0 + rs.getInt("prefix_name") * 20.0;
                    items.add(new DataMapSearchItemDTO(node, score, highlight(node, query.query())));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("数据地图检索失败: " + e.getMessage(), e);
        }

        long total = countSearch(tenantId, query, filter, tokens, where);
        Map<String, List<DataMapFacetDTO>> facets = facets(tenantId, query, filter, tokens, where);
        return new SearchPage(items, total, facets, (System.nanoTime() - started) / 1_000_000);
    }

    private SearchPage recentSearch(String tenantId, SearchQuery query, long started) {
        FilterSql filter = buildFilter(tenantId, query.types(), query.dataSourceId(), null, false);
        String sql = "SELECT * FROM " + TABLE_NODE + filter.sql() + " ORDER BY degree DESC, updated_at DESC, name ASC LIMIT ? OFFSET ?";
        List<DataMapSearchItemDTO> items = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = bind(ps, filter.params());
            ps.setInt(index++, query.limit());
            ps.setInt(index, query.offset());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new DataMapSearchItemDTO(mapNode(rs), 0, null));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取数据地图资产失败: " + e.getMessage(), e);
        }
        long total = countRecent(tenantId, query, filter);
        Map<String, List<DataMapFacetDTO>> facets = facetsRecent(tenantId, query, filter);
        return new SearchPage(items, total, facets, (System.nanoTime() - started) / 1_000_000);
    }

    private long countSearch(String tenantId, SearchQuery query, FilterSql filter, List<String> tokens, String where) {
        String sql = "SELECT COUNT(*) FROM (SELECT n.node_id FROM " + TABLE_NODE + " n JOIN " + TABLE_TOKEN + " t ON n.tenant_id = t.tenant_id AND n.node_id = t.node_id " +
            where + " GROUP BY n.node_id) x";
        List<Object> params = new ArrayList<>(filter.params());
        params.addAll(tokens);
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (Exception e) {
            LOG.warn("统计数据地图检索结果失败: {}", e.getMessage());
            return 0;
        }
    }

    private Map<String, List<DataMapFacetDTO>> facets(String tenantId, SearchQuery query, FilterSql filter, List<String> tokens, String where) {
        String sql = "SELECT asset_type, COUNT(*) AS cnt FROM (SELECT n.asset_type, n.node_id FROM " + TABLE_NODE + " n JOIN " + TABLE_TOKEN + " t ON n.tenant_id = t.tenant_id AND n.node_id = t.node_id " +
            where + " GROUP BY n.asset_type, n.node_id) x GROUP BY asset_type ORDER BY cnt DESC";
        List<Object> params = new ArrayList<>(filter.params());
        params.addAll(tokens);
        return readFacets(sql, params);
    }

    private long countRecent(String tenantId, SearchQuery query, FilterSql filter) {
        String sql = "SELECT COUNT(*) FROM " + TABLE_NODE + filter.sql();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, filter.params());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (Exception e) {
            LOG.warn("统计数据地图资产失败: {}", e.getMessage());
            return 0;
        }
    }

    private Map<String, List<DataMapFacetDTO>> facetsRecent(String tenantId, SearchQuery query, FilterSql filter) {
        String sql = "SELECT asset_type, COUNT(*) AS cnt FROM " + TABLE_NODE + filter.sql() + " GROUP BY asset_type ORDER BY cnt DESC";
        return readFacets(sql, filter.params());
    }

    private Map<String, List<DataMapFacetDTO>> readFacets(String sql, List<Object> params) {
        Map<String, List<DataMapFacetDTO>> facets = new LinkedHashMap<>();
        List<DataMapFacetDTO> types = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("asset_type");
                    types.add(new DataMapFacetDTO(type, DataMapAssetTypes.label(type), rs.getLong("cnt")));
                }
            }
        } catch (Exception e) {
            LOG.warn("统计数据地图分面失败: {}", e.getMessage());
        }
        facets.put("types", types);
        return facets;
    }

    @Override
    public DataMapGraphDTO fullGraph(String tenantId, GraphQuery query) {
        ensureSchemaQuietly();
        String filter = buildNodeFilter(tenantId, query.types(), query.dataSourceId(), query.includeColumns(), true);
        List<Object> filterParams = filterParams(tenantId, query.types(), query.dataSourceId(), query.includeColumns());
        long totalNodes = count("SELECT COUNT(*) FROM " + TABLE_NODE + filter, filterParams);
        long totalEdges = count("SELECT COUNT(*) FROM " + TABLE_EDGE + " WHERE tenant_id = ?", List.of(tenantId));

        String nodeSql = "SELECT * FROM " + TABLE_NODE + filter + " ORDER BY degree DESC, updated_at DESC, name ASC LIMIT ?";
        List<Object> nodeParams = new ArrayList<>(filterParams);
        nodeParams.add(query.maxNodes() + 1);
        List<DataMapNodeDTO> nodes = readNodes(nodeSql, nodeParams);
        boolean nodeTruncated = nodes.size() > query.maxNodes();
        if (nodeTruncated) {
            nodes = new ArrayList<>(nodes.subList(0, query.maxNodes()));
        }
        Set<String> nodeIds = nodes.stream().map(DataMapNodeDTO::id).collect(Collectors.toCollection(LinkedHashSet::new));

        List<Object> edgeParams = new ArrayList<>();
        edgeParams.add(tenantId);
        edgeParams.add(query.maxEdges() + 1);
        List<DataMapEdgeDTO> allEdges = readEdges("SELECT * FROM " + TABLE_EDGE + " WHERE tenant_id = ? LIMIT ?", edgeParams);
        boolean edgeTruncated = allEdges.size() > query.maxEdges();
        if (edgeTruncated) {
            allEdges = new ArrayList<>(allEdges.subList(0, query.maxEdges()));
        }
        List<DataMapEdgeDTO> links = allEdges
            .stream()
            .filter(edge -> nodeIds.contains(edge.source()) && nodeIds.contains(edge.target()))
            .toList();
        List<String> categories = categories(nodes);
        return new DataMapGraphDTO(
            "full",
            nodes,
            links,
            categories,
            totalNodes,
            totalEdges,
            nodeTruncated || edgeTruncated
        );
    }

    @Override
    public DataMapGraphDTO subgraph(
        String tenantId,
        String rootId,
        String direction,
        int depth,
        Set<String> types,
        int maxNodes,
        boolean includeColumns
    ) {
        ensureSchemaQuietly();
        List<DataMapEdgeDTO> allEdges = readEdges(
            "SELECT * FROM " + TABLE_EDGE + " WHERE tenant_id = ?",
            List.of(tenantId)
        );
        Map<String, List<Adjacent>> adjacency = buildAdjacency(allEdges, direction);
        if (!adjacency.containsKey(rootId)) {
            adjacency.put(rootId, List.of());
        }
        LinkedHashMap<String, Integer> layers = new LinkedHashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        layers.put(rootId, 0);
        queue.add(rootId);
        boolean truncated = false;
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentLayer = layers.get(current);
            if (Math.abs(currentLayer) >= depth) {
                continue;
            }
            for (Adjacent adjacent : adjacency.getOrDefault(current, List.of())) {
                if (layers.containsKey(adjacent.nodeId())) {
                    continue;
                }
                if (layers.size() >= maxNodes) {
                    truncated = true;
                    break;
                }
                layers.put(adjacent.nodeId(), currentLayer + adjacent.delta());
                queue.add(adjacent.nodeId());
            }
            if (truncated) {
                break;
            }
        }
        List<DataMapNodeDTO> nodes = readNodesByIds(tenantId, layers.keySet());
        if (!includeColumns) {
            nodes = nodes
                .stream()
                .filter(node -> !DataMapAssetTypes.isFieldType(node.type()) || node.id().equals(rootId))
                .toList();
        }
        if (types != null && !types.isEmpty()) {
            nodes = nodes.stream().filter(node -> types.contains(node.type()) || node.id().equals(rootId)).toList();
        }
        Set<String> nodeIds = nodes.stream().map(DataMapNodeDTO::id).collect(Collectors.toCollection(LinkedHashSet::new));
        List<DataMapNodeDTO> layered = nodes
            .stream()
            .map(node -> node.withLayer(layers.getOrDefault(node.id(), 0)))
            .sorted((left, right) -> {
                int byLayer = Integer.compare(left.layer(), right.layer());
                return byLayer != 0 ? byLayer : left.name().compareToIgnoreCase(right.name());
            })
            .toList();
        List<DataMapEdgeDTO> links = allEdges
            .stream()
            .filter(edge -> nodeIds.contains(edge.source()) && nodeIds.contains(edge.target()))
            .toList();
        return new DataMapGraphDTO(
            "subgraph",
            layered,
            links,
            categories(layered),
            layers.size(),
            links.size(),
            truncated
        );
    }

    @Override
    public Optional<DataMapNodeDTO> findNode(String tenantId, String nodeId) {
        String sql = "SELECT * FROM " + TABLE_NODE + " WHERE tenant_id = ? AND node_id = ?";
        List<DataMapNodeDTO> nodes = readNodes(sql, List.of(tenantId, nodeId));
        return nodes.stream().findFirst();
    }

    @Override
    public List<DataMapNodeDTO> children(String tenantId, String parentId) {
        String sql =
            "SELECT n.* FROM " +
            TABLE_EDGE +
            " e JOIN " +
            TABLE_NODE +
            " n ON e.tenant_id = n.tenant_id AND e.target_id = n.node_id " +
            "WHERE e.tenant_id = ? AND e.source_id = ? AND e.relation = 'CONTAINS' ORDER BY n.name ASC";
        return readNodes(sql, List.of(tenantId, parentId));
    }

    @Override
    public List<DataMapEdgeDTO> edges(String tenantId, String nodeId) {
        String sql = "SELECT * FROM " + TABLE_EDGE + " WHERE tenant_id = ? AND (source_id = ? OR target_id = ?)";
        return readEdges(sql, List.of(tenantId, nodeId, nodeId));
    }

    @Override
    public Map<String, Long> typeCounts(String tenantId) {
        String sql = "SELECT asset_type, COUNT(*) AS cnt FROM " + TABLE_NODE + " WHERE tenant_id = ? GROUP BY asset_type ORDER BY cnt DESC";
        Map<String, Long> result = new LinkedHashMap<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString("asset_type"), rs.getLong("cnt"));
                }
            }
        } catch (Exception e) {
            LOG.warn("统计数据地图资产类型失败: {}", e.getMessage());
        }
        return result;
    }

    @Override
    public long nodeCount(String tenantId) {
        return count("SELECT COUNT(*) FROM " + TABLE_NODE + " WHERE tenant_id = ?", List.of(tenantId));
    }

    @Override
    public long edgeCount(String tenantId) {
        return count("SELECT COUNT(*) FROM " + TABLE_EDGE + " WHERE tenant_id = ?", List.of(tenantId));
    }

    private void deleteTenantData(Connection connection, String tenantId) throws SQLException {
        for (String table : List.of(TABLE_TOKEN, TABLE_EDGE, TABLE_NODE, TABLE_STATUS)) {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + table + " WHERE tenant_id = ?")) {
                ps.setString(1, tenantId);
                ps.executeUpdate();
            }
        }
    }

    private void insertNodes(Connection connection, String tenantId, List<DataMapNodeDTO> nodes) throws SQLException {
        String sql =
            "INSERT INTO " +
            TABLE_NODE +
            "(tenant_id, node_id, asset_type, name, code, description, qualified_name, data_source_id, data_source_name, " +
            "schema_name, table_name, degree, status, updated_at, meta_json) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 0;
            for (DataMapNodeDTO node : nodes) {
                ps.setString(1, tenantId);
                ps.setString(2, node.id());
                ps.setString(3, node.type());
                ps.setString(4, node.name());
                ps.setString(5, node.code());
                ps.setString(6, node.description());
                ps.setString(7, node.qualifiedName());
                ps.setString(8, node.dataSourceId());
                ps.setString(9, node.dataSourceName());
                ps.setString(10, node.schema());
                ps.setString(11, node.table());
                ps.setInt(12, node.degree());
                ps.setString(13, node.status());
                ps.setString(14, node.updatedAt());
                ps.setString(15, toJson(node.meta()));
                ps.addBatch();
                if (++index % 500 == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
    }

    private void insertEdges(Connection connection, String tenantId, List<DataMapEdgeDTO> edges) throws SQLException {
        String sql = "INSERT INTO " + TABLE_EDGE + "(tenant_id, edge_id, source_id, target_id, relation, label, confidence) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 0;
            for (DataMapEdgeDTO edge : edges) {
                ps.setString(1, tenantId);
                ps.setString(2, edge.id());
                ps.setString(3, edge.source());
                ps.setString(4, edge.target());
                ps.setString(5, edge.relation());
                ps.setString(6, edge.label());
                ps.setString(7, edge.confidence());
                ps.addBatch();
                if (++index % 1000 == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
    }

    private void insertTokens(Connection connection, String tenantId, List<TokenRow> tokens) throws SQLException {
        String sql = "INSERT INTO " + TABLE_TOKEN + "(tenant_id, node_id, token, field, weight) VALUES (?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 0;
            for (TokenRow token : tokens) {
                ps.setString(1, tenantId);
                ps.setString(2, token.nodeId());
                ps.setString(3, token.token());
                ps.setString(4, token.field());
                ps.setDouble(5, token.weight());
                ps.addBatch();
                if (++index % 2000 == 0) {
                    ps.executeBatch();
                }
            }
            ps.executeBatch();
        }
    }

    private void writeStatus(
        Connection connection,
        String tenantId,
        String status,
        String startedAt,
        String finishedAt,
        long nodeCount,
        long edgeCount,
        String message,
        String coverage
    ) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + TABLE_STATUS + " WHERE tenant_id = ?")) {
            ps.setString(1, tenantId);
            ps.executeUpdate();
        }
        String sql =
            "INSERT INTO " +
            TABLE_STATUS +
            "(tenant_id, status, started_at, finished_at, node_count, edge_count, message, coverage) VALUES (?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tenantId);
            ps.setString(2, status);
            ps.setString(3, startedAt);
            ps.setString(4, finishedAt);
            ps.setLong(5, nodeCount);
            ps.setLong(6, edgeCount);
            ps.setString(7, message);
            ps.setString(8, coverage);
            ps.executeUpdate();
        }
    }

    private List<DataMapNodeDTO> readNodesByIds(String tenantId, Set<String> nodeIds) {
        if (nodeIds == null || nodeIds.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(nodeIds);
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        String sql = "SELECT * FROM " + TABLE_NODE + " WHERE tenant_id = ? AND node_id IN (" + placeholders + ")";
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        params.addAll(ids);
        return readNodes(sql, params);
    }

    private List<DataMapNodeDTO> readNodes(String sql, List<Object> params) {
        List<DataMapNodeDTO> result = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapNode(rs));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取数据地图节点失败: " + e.getMessage(), e);
        }
        return result;
    }

    private List<DataMapEdgeDTO> readEdges(String sql, List<Object> params) {
        List<DataMapEdgeDTO> result = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapEdge(rs));
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取数据地图关系失败: " + e.getMessage(), e);
        }
        return result;
    }

    private DataMapNodeDTO mapNode(ResultSet rs) throws SQLException {
        return new DataMapNodeDTO(
            rs.getString("node_id"),
            rs.getString("asset_type"),
            rs.getString("name"),
            rs.getString("code"),
            rs.getString("description"),
            rs.getString("qualified_name"),
            rs.getString("data_source_id"),
            rs.getString("data_source_name"),
            rs.getString("schema_name"),
            rs.getString("table_name"),
            rs.getInt("degree"),
            rs.getString("status"),
            rs.getString("updated_at"),
            0,
            fromJson(rs.getString("meta_json"))
        );
    }

    private DataMapEdgeDTO mapEdge(ResultSet rs) throws SQLException {
        return new DataMapEdgeDTO(
            rs.getString("edge_id"),
            rs.getString("source_id"),
            rs.getString("target_id"),
            rs.getString("relation"),
            rs.getString("label"),
            rs.getString("confidence")
        );
    }

    private long count(String sql, List<Object> params) {
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0;
            }
        } catch (Exception e) {
            LOG.warn("统计数据地图索引失败: {}", e.getMessage());
            return 0;
        }
    }

    private Map<String, List<Adjacent>> buildAdjacency(List<DataMapEdgeDTO> edges, String direction) {
        Map<String, List<Adjacent>> adjacency = new LinkedHashMap<>();
        String normalized = direction == null ? "both" : direction.toLowerCase();
        for (DataMapEdgeDTO edge : edges) {
            if (!"up".equals(normalized)) {
                adjacency.computeIfAbsent(edge.source(), key -> new ArrayList<>()).add(new Adjacent(edge.target(), 1));
            }
            if (!"down".equals(normalized)) {
                adjacency.computeIfAbsent(edge.target(), key -> new ArrayList<>()).add(new Adjacent(edge.source(), -1));
            }
        }
        return adjacency;
    }

    private List<String> categories(List<DataMapNodeDTO> nodes) {
        return nodes.stream().map(DataMapNodeDTO::type).distinct().toList();
    }

    private String highlight(DataMapNodeDTO node, String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String lower = query.toLowerCase();
        for (String value : java.util.Arrays.asList(node.name(), node.code(), node.qualifiedName(), node.description())) {
            if (value != null && value.toLowerCase().contains(lower)) {
                int index = value.toLowerCase().indexOf(lower);
                int start = Math.max(0, index - 30);
                int end = Math.min(value.length(), index + query.length() + 60);
                return (start > 0 ? "…" : "") + value.substring(start, end) + (end < value.length() ? "…" : "");
            }
        }
        return node.description();
    }

    private String buildNodeFilter(String tenantId, Set<String> types, String dataSourceId, boolean includeColumns, boolean includeTenant) {
        StringBuilder sql = new StringBuilder(includeTenant ? " WHERE tenant_id = ?" : " WHERE tenant_id = ?");
        if (types != null && !types.isEmpty()) {
            sql.append(" AND asset_type IN (").append(String.join(",", Collections.nCopies(types.size(), "?"))).append(")");
        }
        if (!includeColumns) {
            sql.append(" AND asset_type NOT IN ('COLUMN','MODEL_FIELD')");
        }
        if (dataSourceId != null && !dataSourceId.isBlank()) {
            sql.append(" AND data_source_id = ?");
        }
        return sql.toString();
    }

    private List<Object> filterParams(String tenantId, Set<String> types, String dataSourceId, boolean includeColumns) {
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (types != null && !types.isEmpty()) {
            params.addAll(types);
        }
        if (dataSourceId != null && !dataSourceId.isBlank()) {
            params.add(dataSourceId);
        }
        return params;
    }

    private FilterSql buildFilter(String tenantId, Set<String> types, String dataSourceId, String alias, boolean tokenJoin) {
        String prefix = tokenJoin ? " WHERE n.tenant_id = ?" : " WHERE tenant_id = ?";
        StringBuilder sql = new StringBuilder(prefix);
        List<Object> params = new ArrayList<>();
        params.add(tenantId);
        if (types != null && !types.isEmpty()) {
            sql.append(tokenJoin ? " AND n.asset_type IN (" : " AND asset_type IN (");
            sql.append(String.join(",", Collections.nCopies(types.size(), "?"))).append(")");
            params.addAll(types);
        }
        if (dataSourceId != null && !dataSourceId.isBlank()) {
            sql.append(tokenJoin ? " AND n.data_source_id = ?" : " AND data_source_id = ?");
            params.add(dataSourceId);
        }
        return new FilterSql(sql.toString(), params);
    }

    private int bind(PreparedStatement ps, List<Object> params) throws SQLException {
        int index = 1;
        if (params != null) {
            for (Object param : params) {
                ps.setObject(index++, param);
            }
        }
        return index;
    }

    private String escapeLike(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String toJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private void ensureSchemaQuietly() {
        try {
            ensureSchema();
        } catch (SQLException e) {
            throw new IllegalStateException("初始化数据地图索引失败: " + e.getMessage(), e);
        }
    }

    private void ensureSchema() throws SQLException {
        if (schemaReady) {
            return;
        }
        synchronized (this) {
            if (schemaReady) {
                return;
            }
            try (Connection connection = connect()) {
                ensureSchema(connection);
            }
            schemaReady = true;
        }
    }

    private void ensureSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(
                "CREATE TABLE IF NOT EXISTS " +
                TABLE_NODE +
                " (tenant_id VARCHAR NOT NULL, node_id VARCHAR NOT NULL, asset_type VARCHAR NOT NULL, name VARCHAR, " +
                "code VARCHAR, description VARCHAR, qualified_name VARCHAR, data_source_id VARCHAR, data_source_name VARCHAR, " +
                "schema_name VARCHAR, table_name VARCHAR, degree INTEGER DEFAULT 0, status VARCHAR, updated_at VARCHAR, " +
                "meta_json VARCHAR, PRIMARY KEY (tenant_id, node_id))"
            );
            statement.execute(
                "CREATE TABLE IF NOT EXISTS " +
                TABLE_EDGE +
                " (tenant_id VARCHAR NOT NULL, edge_id VARCHAR NOT NULL, source_id VARCHAR NOT NULL, target_id VARCHAR NOT NULL, " +
                "relation VARCHAR, label VARCHAR, confidence VARCHAR, PRIMARY KEY (tenant_id, edge_id))"
            );
            statement.execute(
                "CREATE TABLE IF NOT EXISTS " +
                TABLE_TOKEN +
                " (tenant_id VARCHAR NOT NULL, node_id VARCHAR NOT NULL, token VARCHAR NOT NULL, field VARCHAR, weight DOUBLE)"
            );
            statement.execute(
                "CREATE TABLE IF NOT EXISTS " +
                TABLE_STATUS +
                " (tenant_id VARCHAR PRIMARY KEY, status VARCHAR, started_at VARCHAR, finished_at VARCHAR, node_count BIGINT, " +
                "edge_count BIGINT, message VARCHAR, coverage VARCHAR)"
            );
            statement.execute("CREATE INDEX IF NOT EXISTS idx_data_map_token ON " + TABLE_TOKEN + "(tenant_id, token)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_data_map_edge_source ON " + TABLE_EDGE + "(tenant_id, source_id)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_data_map_edge_target ON " + TABLE_EDGE + "(tenant_id, target_id)");
        }
    }

    private Connection connect() throws SQLException {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            Class.forName("org.duckdb.DuckDBDriver", true, loader);
        } catch (ClassNotFoundException e) {
            throw new SQLException("DuckDB 驱动未找到", e);
        }
        Path path = Paths.get(properties.getDbFile()).toAbsolutePath();
        if (path.getParent() != null) {
            try {
                Files.createDirectories(path.getParent());
            } catch (Exception e) {
                LOG.warn("创建数据地图索引目录失败: {}", e.getMessage());
            }
        }
        return DriverManager.getConnection("jdbc:duckdb:" + path);
    }

    private record FilterSql(String sql, List<Object> params) {}

    private record Adjacent(String nodeId, int delta) {}
}
