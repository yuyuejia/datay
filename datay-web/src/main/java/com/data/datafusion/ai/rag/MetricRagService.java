package com.data.datafusion.ai.rag;

import com.data.datafusion.ai.rag.embedding.EmbeddingClient;
import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.Metric;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.DataSourceQueryService;
import com.data.datafusion.service.DataSourceService;
import com.data.datafusion.service.MetricService;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.dto.MetricDTO;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 指标知识库（RAG）。
 *
 * <p>把指标、维度字段与维度成员值向量化后存入独立 DuckDB 文件，并提供基于
 * 余弦相似度的检索：
 * <ul>
 *     <li>指标检索：根据「销售额」匹配出最精确的指标（如「销售金额」）；</li>
 *     <li>维度字段检索：根据「品类」匹配维度与字段；</li>
 *     <li>维度成员检索：根据「北京」匹配出其所属维度与字段（如 dim_store.city）。</li>
 * </ul>
 *
 * <p>向量以 JSON 字符串存储，检索使用 DuckDB 核心函数 {@code list_cosine_similarity}，
 * 不依赖 vss 扩展；若该函数不可用则回退到 Java 端余弦计算。
 */
@Service
public class MetricRagService {

    private static final Logger LOG = LoggerFactory.getLogger(MetricRagService.class);

    private static final String TABLE_META = "rag_meta";
    private static final String TABLE_METRIC = "rag_metric";
    private static final String TABLE_DIMENSION = "rag_dimension";
    private static final String TABLE_MEMBER = "rag_member";

    private static final String KEY_PROVIDER = "provider";
    private static final String KEY_DIMENSIONS = "dimensions";
    private static final String KEY_BUILT_AT = "built_at";
    private static final String KEY_METRIC_COUNT = "metric_count";
    private static final String KEY_DIMENSION_COUNT = "dimension_count";
    private static final String KEY_MEMBER_COUNT = "member_count";

    private final MetricRagProperties properties;
    private final EmbeddingClient embeddingClient;
    private final MetricService metricService;
    private final DataModelRepository dataModelRepository;
    private final ModelFieldRepository modelFieldRepository;
    private final DataSourceService dataSourceService;
    private final DataSourceQueryService dataSourceQueryService;

    private volatile boolean schemaReady = false;
    private final Set<String> readyTenants = ConcurrentHashMap.newKeySet();

    public MetricRagService(
        MetricRagProperties properties,
        EmbeddingClient embeddingClient,
        MetricService metricService,
        DataModelRepository dataModelRepository,
        ModelFieldRepository modelFieldRepository,
        DataSourceService dataSourceService,
        DataSourceQueryService dataSourceQueryService
    ) {
        this.properties = properties;
        this.embeddingClient = embeddingClient;
        this.metricService = metricService;
        this.dataModelRepository = dataModelRepository;
        this.modelFieldRepository = modelFieldRepository;
        this.dataSourceService = dataSourceService;
        this.dataSourceQueryService = dataSourceQueryService;
    }

    /**
     * 检索指标/维度字段/维度成员候选。
     *
     * @param query             用户问题或关键词
     * @param topK              每类候选数量，&le;0 时使用配置默认值
     * @param dimensionModelCodes 可选的维度模型编码过滤（成员与字段检索限定在这些维度内）
     */
    public Map<String, Object> search(String query, int topK, List<String> dimensionModelCodes) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!properties.isEnabled()) {
            result.put("enabled", false);
            result.put("hint", "指标知识库未启用（datay.ai.rag.enabled=false）");
            return result;
        }
        if (query == null || query.isBlank()) {
            result.put("error", "检索关键词不能为空");
            return result;
        }
        int limit = topK > 0 ? topK : properties.getTopK();
        ensureIndex();

        float[] queryVector = embeddingClient.embed(query);
        String vector = toVectorString(queryVector);
        String tenantId = currentTenantId();
        List<String> dimensionIds = resolveDimensionIds(dimensionModelCodes);

        result.put("enabled", true);
        result.put("provider", embeddingClient.provider());
        result.put("query", query);
        result.put("metrics", searchMetrics(vector, tenantId, limit));
        result.put("dimensionFields", searchDimensionFields(vector, tenantId, limit, dimensionIds));
        result.put("members", searchMembers(vector, tenantId, limit, dimensionIds));
        return result;
    }

    /**
     * 返回知识库状态：文档数量、向量提供方与构建时间。
     */
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", properties.isEnabled());
        result.put("provider", embeddingClient.provider());
        result.put("dimensions", embeddingClient.dimensions());
        result.put("memberIndexEnabled", properties.isMemberIndexEnabled());
        if (!properties.isEnabled()) {
            return result;
        }
        try (Connection connection = connect()) {
            initSchema(connection);
            Map<String, String> meta = readMeta(connection);
            result.put("builtAt", meta.get(KEY_BUILT_AT));
            result.put("metricCount", parseInt(meta.get(KEY_METRIC_COUNT)));
            result.put("dimensionCount", parseInt(meta.get(KEY_DIMENSION_COUNT)));
            result.put("memberCount", parseInt(meta.get(KEY_MEMBER_COUNT)));
            result.put("indexed", parseInt(meta.get(KEY_METRIC_COUNT)) > 0);
        } catch (SQLException e) {
            result.put("error", "读取知识库状态失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 重建当前租户的知识库索引。
     */
    public synchronized Map<String, Object> rebuild() {
        if (!properties.isEnabled()) {
            return Map.of("enabled", false, "hint", "指标知识库未启用");
        }
        String tenantId = currentTenantId();
        int metricCount = 0;
        int dimensionCount = 0;
        int memberCount = 0;
        try (Connection connection = connect()) {
            initSchema(connection);
            clearTenant(connection, tenantId);

            metricCount = indexMetrics(connection, tenantId);
            dimensionCount = indexDimensionFields(connection, tenantId);
            if (properties.isMemberIndexEnabled()) {
                memberCount = indexMembers(connection, tenantId);
            }

            writeMeta(connection, KEY_PROVIDER, embeddingClient.provider());
            writeMeta(connection, KEY_DIMENSIONS, String.valueOf(embeddingClient.dimensions()));
            writeMeta(connection, KEY_BUILT_AT, Instant.now().toString());
            writeMeta(connection, KEY_METRIC_COUNT, String.valueOf(metricCount));
            writeMeta(connection, KEY_DIMENSION_COUNT, String.valueOf(dimensionCount));
            writeMeta(connection, KEY_MEMBER_COUNT, String.valueOf(memberCount));
        } catch (SQLException e) {
            LOG.error("Rebuild metric RAG index failed", e);
            throw new IllegalStateException("重建指标知识库失败: " + e.getMessage(), e);
        }
        readyTenants.add(tenantId == null ? "__null__" : tenantId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", true);
        result.put("provider", embeddingClient.provider());
        result.put("metricCount", metricCount);
        result.put("dimensionCount", dimensionCount);
        result.put("memberCount", memberCount);
        result.put("builtAt", Instant.now().toString());
        return result;
    }

    private void ensureIndex() {
        String tenantId = currentTenantId();
        String tenantKey = tenantId == null ? "__null__" : tenantId;
        if (readyTenants.contains(tenantKey)) {
            return;
        }
        synchronized (this) {
            if (readyTenants.contains(tenantKey)) {
                return;
            }
            try (Connection connection = connect()) {
                initSchema(connection);
                Map<String, String> meta = readMeta(connection);
                boolean providerChanged = !embeddingClient.provider().equals(meta.get(KEY_PROVIDER));
                boolean empty = countMetrics(connection, tenantId) == 0;
                if (providerChanged || empty) {
                    LOG.info("Metric RAG index missing or stale, rebuilding (providerChanged={}, empty={})", providerChanged, empty);
                    rebuild();
                }
            } catch (SQLException e) {
                LOG.warn("检查指标知识库索引失败: {}", e.getMessage());
            }
            readyTenants.add(tenantKey);
        }
    }

    private int countMetrics(Connection connection, String tenantId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + TABLE_METRIC + tenantWhere(tenantId);
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (tenantId != null) {
                ps.setString(1, tenantId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    private int indexMetrics(Connection connection, String tenantId) {
        List<MetricDTO> metrics = metricService
            .findAllSimple()
            .stream()
            .filter(m -> Metric.STATUS_ENABLED.equals(m.getStatus()))
            .collect(Collectors.toList());
        if (metrics.isEmpty()) {
            return 0;
        }
        // 指标名称重复加权，避免精确名称被描述中的相近词稀释（如「销售额」被「平均销售价格」压过）
        List<String> contents = metrics
            .stream()
            .map(m -> joinNonBlank(m.getName(), m.getName(), m.getName(), m.getCode(), m.getDescription(), m.getUnit()))
            .collect(Collectors.toList());
        List<float[]> vectors = embeddingClient.embedAll(contents);

        String sql = "INSERT INTO " + TABLE_METRIC + "(tenant_id, metric_id, code, name, content, embedding) VALUES (?, ?, ?, ?, ?, ?)";
        int count = 0;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < metrics.size(); i++) {
                MetricDTO metric = metrics.get(i);
                ps.setString(1, tenantId);
                ps.setObject(2, metric.getId());
                ps.setString(3, metric.getCode());
                ps.setString(4, metric.getName());
                ps.setString(5, contents.get(i));
                ps.setString(6, toVectorString(vectors.get(i)));
                ps.addBatch();
                count++;
            }
            ps.executeBatch();
        } catch (SQLException e) {
            LOG.warn("写入指标向量失败: {}", e.getMessage());
        }
        return count;
    }

    private int indexDimensionFields(Connection connection, String tenantId) {
        List<DataModel> dimensions = dataModelRepository.findByModelType("DIMENSION");
        List<Object[]> rows = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        for (DataModel dimension : dimensions) {
            for (ModelField field : modelFieldRepository.findByModelIdOrderBySortOrderAsc(dimension.getId())) {
                rows.add(new Object[] { dimension, field });
                contents.add(joinNonBlank(dimension.getName(), dimension.getCode(), field.getFieldName(), field.getDescription()));
            }
        }
        if (rows.isEmpty()) {
            return 0;
        }
        List<float[]> vectors = embeddingClient.embedAll(contents);
        String sql =
            "INSERT INTO " +
            TABLE_DIMENSION +
            "(tenant_id, dimension_model_id, dimension_code, dimension_name, field_name, field_desc, hierarchy, content, embedding) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        int count = 0;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < rows.size(); i++) {
                DataModel dimension = (DataModel) rows.get(i)[0];
                ModelField field = (ModelField) rows.get(i)[1];
                ps.setString(1, tenantId);
                ps.setObject(2, dimension.getId());
                ps.setString(3, dimension.getCode());
                ps.setString(4, dimension.getName());
                ps.setString(5, field.getFieldName());
                ps.setString(6, field.getDescription());
                ps.setBoolean(7, "HIERARCHY".equalsIgnoreCase(dimension.getDimensionKind()));
                ps.setString(8, contents.get(i));
                ps.setString(9, toVectorString(vectors.get(i)));
                ps.addBatch();
                count++;
            }
            ps.executeBatch();
        } catch (SQLException e) {
            LOG.warn("写入维度字段向量失败: {}", e.getMessage());
        }
        return count;
    }

    private int indexMembers(Connection connection, String tenantId) {
        List<DataModel> dimensions = dataModelRepository.findByModelType("DIMENSION");
        List<Object[]> members = new ArrayList<>();
        for (DataModel dimension : dimensions) {
            if (dimension.getDataSourceId() == null) {
                continue;
            }
            DataSourceDTO dataSource = dataSourceService.findOne(dimension.getDataSourceId()).orElse(null);
            if (dataSource == null) {
                continue;
            }
            for (ModelField field : modelFieldRepository.findByModelIdOrderBySortOrderAsc(dimension.getId())) {
                if (!isMemberField(field)) {
                    continue;
                }
                List<String> values = sampleMembers(dataSource, dimension, field);
                for (String value : values) {
                    members.add(new Object[] { dimension, field, value });
                }
            }
        }
        if (members.isEmpty()) {
            return 0;
        }
        List<String> contents = members.stream().map(m -> String.valueOf(m[2])).collect(Collectors.toList());
        List<float[]> vectors = embeddingClient.embedAll(contents);
        String sql = "INSERT INTO " + TABLE_MEMBER + "(tenant_id, dimension_model_id, dimension_code, field_name, member_value, embedding) VALUES (?, ?, ?, ?, ?, ?)";
        int count = 0;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < members.size(); i++) {
                DataModel dimension = (DataModel) members.get(i)[0];
                ModelField field = (ModelField) members.get(i)[1];
                ps.setString(1, tenantId);
                ps.setObject(2, dimension.getId());
                ps.setString(3, dimension.getCode());
                ps.setString(4, field.getFieldName());
                ps.setString(5, String.valueOf(members.get(i)[2]));
                ps.setString(6, toVectorString(vectors.get(i)));
                ps.addBatch();
                count++;
            }
            ps.executeBatch();
        } catch (SQLException e) {
            LOG.warn("写入维度成员向量失败: {}", e.getMessage());
        }
        return count;
    }

    private List<String> sampleMembers(DataSourceDTO dataSource, DataModel dimension, ModelField field) {
        StringBuilder sql = new StringBuilder("SELECT DISTINCT ")
            .append(field.getFieldName())
            .append(" FROM ")
            .append(physicalTableName(dimension))
            .append(" WHERE ")
            .append(field.getFieldName())
            .append(" IS NOT NULL LIMIT ")
            .append(Math.max(1, properties.getMemberSampleLimit()));
        List<String> values = new ArrayList<>();
        try {
            Map<String, Object> queryResult = dataSourceQueryService.executeQuery(dataSource, sql.toString());
            Object rows = queryResult.get("rows");
            if (rows instanceof List<?> list) {
                for (Object row : list) {
                    if (row instanceof Map<?, ?> map && !map.isEmpty()) {
                        Object value = map.values().iterator().next();
                        if (value != null && !String.valueOf(value).isBlank()) {
                            values.add(String.valueOf(value));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("抽样维度成员失败 {}.{}: {}", dimension.getCode(), field.getFieldName(), e.getMessage());
        }
        return values;
    }

    private boolean isMemberField(ModelField field) {
        if (Boolean.TRUE.equals(field.getIsPrimaryKey())) {
            return false;
        }
        if ("HIERARCHY".equalsIgnoreCase(field.getFieldRole())) {
            return false;
        }
        String type = field.getFieldType() == null ? "" : field.getFieldType().toUpperCase();
        boolean stringType = type.contains("CHAR") || type.contains("TEXT") || type.equals("STRING") || type.equals("CLOB");
        if (!stringType) {
            return false;
        }
        String name = field.getFieldName() == null ? "" : field.getFieldName().toLowerCase();
        return !name.matches(".*(_id|_sk|_code|code|_key|date|time)$");
    }

    private List<Map<String, Object>> searchMetrics(String vector, String tenantId, int limit) {
        String sql =
            "SELECT code, name, list_cosine_similarity(embedding::FLOAT[], ?::FLOAT[]) AS score FROM " +
            TABLE_METRIC +
            tenantWhere(tenantId) +
            " ORDER BY score DESC LIMIT ?";
        return queryScored(sql, vector, tenantId, null, limit, new String[] { "code", "name" });
    }

    private List<Map<String, Object>> searchDimensionFields(String vector, String tenantId, int limit, List<String> dimensionIds) {
        String sql =
            "SELECT dimension_model_id, dimension_code, dimension_name, field_name, field_desc, hierarchy, " +
            "list_cosine_similarity(embedding::FLOAT[], ?::FLOAT[]) AS score FROM " +
            TABLE_DIMENSION +
            tenantAndDimensions(tenantId, dimensionIds) +
            " ORDER BY score DESC LIMIT ?";
        return queryScored(
            sql,
            vector,
            tenantId,
            dimensionIds,
            limit,
            new String[] { "dimension_model_id", "dimension_code", "dimension_name", "field_name", "field_desc", "hierarchy" }
        );
    }

    private List<Map<String, Object>> searchMembers(String vector, String tenantId, int limit, List<String> dimensionIds) {
        String sql =
            "SELECT dimension_model_id, dimension_code, field_name, member_value, " +
            "list_cosine_similarity(embedding::FLOAT[], ?::FLOAT[]) AS score FROM " +
            TABLE_MEMBER +
            tenantAndDimensions(tenantId, dimensionIds) +
            " ORDER BY score DESC LIMIT ?";
        return queryScored(
            sql,
            vector,
            tenantId,
            dimensionIds,
            limit,
            new String[] { "dimension_model_id", "dimension_code", "field_name", "member_value" }
        );
    }

    private List<Map<String, Object>> queryScored(
        String sql,
        String vector,
        String tenantId,
        List<String> dimensionIds,
        int limit,
        String[] columns
    ) {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 1;
            ps.setString(index++, vector);
            if (tenantId != null) {
                ps.setString(index++, tenantId);
            }
            if (dimensionIds != null && !dimensionIds.isEmpty()) {
                for (String id : dimensionIds) {
                    ps.setObject(index++, id);
                }
            }
            ps.setInt(index, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    for (String column : columns) {
                        item.put(column, rs.getObject(column));
                    }
                    item.put("score", rs.getDouble("score"));
                    rows.add(item);
                }
            }
        } catch (SQLException e) {
            LOG.warn("向量检索失败，回退内存计算: {}", e.getMessage());
            return fallbackSearch(sql, vector, tenantId, dimensionIds, limit, columns);
        }
        return rows;
    }

    /**
     * 当 {@code list_cosine_similarity} 不可用时的兜底：拉取候选行并在 Java 端计算余弦。
     */
    private List<Map<String, Object>> fallbackSearch(
        String sql,
        String vector,
        String tenantId,
        List<String> dimensionIds,
        int limit,
        String[] columns
    ) {
        String table = extractTable(sql);
        String selectColumns = String.join(", ", columns);
        String fallbackSql = "SELECT " + selectColumns + ", embedding FROM " + table + tenantAndDimensions(tenantId, dimensionIds);
        float[] queryVector = parseVector(vector);
        List<Map<String, Object>> scored = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement ps = connection.prepareStatement(fallbackSql)) {
            bindTenantAndDimensions(ps, tenantId, dimensionIds);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> item = new LinkedHashMap<>();
                    for (String column : columns) {
                        item.put(column, rs.getObject(column));
                    }
                    item.put("score", cosine(queryVector, parseVector(rs.getString("embedding"))));
                    scored.add(item);
                }
            }
        } catch (SQLException e) {
            LOG.error("内存回退检索失败: {}", e.getMessage());
            return List.of();
        }
        scored.sort((a, b) -> Double.compare((double) b.get("score"), (double) a.get("score")));
        return scored.size() > limit ? new ArrayList<>(scored.subList(0, limit)) : scored;
    }

    private static void bindTenantAndDimensions(PreparedStatement ps, String tenantId, List<String> dimensionIds) throws SQLException {
        int index = 1;
        if (tenantId != null) {
            ps.setString(index++, tenantId);
        }
        if (dimensionIds != null && !dimensionIds.isEmpty()) {
            for (String id : dimensionIds) {
                ps.setObject(index++, id);
            }
        }
    }

    private List<String> resolveDimensionIds(List<String> dimensionModelCodes) {
        if (dimensionModelCodes == null || dimensionModelCodes.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (String code : dimensionModelCodes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            dataModelRepository.findFirstByCode(code.trim()).ifPresent(model -> ids.add(model.getId()));
        }
        return ids;
    }

    private static String tenantWhere(String tenantId) {
        return tenantId == null ? " WHERE tenant_id IS NULL" : " WHERE tenant_id = ?";
    }

    private static String tenantAndDimensions(String tenantId, List<String> dimensionIds) {
        StringBuilder sb = new StringBuilder(tenantWhere(tenantId));
        if (dimensionIds != null && !dimensionIds.isEmpty()) {
            sb.append(" AND dimension_model_id IN (");
            sb.append(dimensionIds.stream().map(id -> "?").collect(Collectors.joining(", ")));
            sb.append(")");
        }
        return sb.toString();
    }

    private static String extractTable(String sql) {
        int from = sql.indexOf(" FROM ");
        String rest = sql.substring(from + 6).trim();
        int end = rest.indexOf(' ');
        return end < 0 ? rest : rest.substring(0, end);
    }

    private void clearTenant(Connection connection, String tenantId) throws SQLException {
        for (String table : List.of(TABLE_METRIC, TABLE_DIMENSION, TABLE_MEMBER)) {
            String sql = "DELETE FROM " + table + tenantWhere(tenantId);
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                if (tenantId != null) {
                    ps.setString(1, tenantId);
                }
                ps.executeUpdate();
            }
        }
    }

    private void initSchema(Connection connection) throws SQLException {
        if (schemaReady) {
            return;
        }
        synchronized (this) {
            if (schemaReady) {
                return;
            }
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS " + TABLE_META + "(k VARCHAR PRIMARY KEY, v VARCHAR)");
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS " +
                    TABLE_METRIC +
                    "(tenant_id VARCHAR, metric_id BIGINT, code VARCHAR, name VARCHAR, content VARCHAR, embedding VARCHAR)"
                );
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS " +
                    TABLE_DIMENSION +
                    "(tenant_id VARCHAR, dimension_model_id BIGINT, dimension_code VARCHAR, dimension_name VARCHAR, field_name VARCHAR, field_desc VARCHAR, hierarchy BOOLEAN, content VARCHAR, embedding VARCHAR)"
                );
                stmt.execute(
                    "CREATE TABLE IF NOT EXISTS " +
                    TABLE_MEMBER +
                    "(tenant_id VARCHAR, dimension_model_id BIGINT, dimension_code VARCHAR, field_name VARCHAR, member_value VARCHAR, embedding VARCHAR)"
                );
            }
            schemaReady = true;
        }
    }

    private Map<String, String> readMeta(Connection connection) throws SQLException {
        Map<String, String> meta = new LinkedHashMap<>();
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery("SELECT k, v FROM " + TABLE_META)) {
            while (rs.next()) {
                meta.put(rs.getString("k"), rs.getString("v"));
            }
        }
        return meta;
    }

    private void writeMeta(Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + TABLE_META + " WHERE k = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = connection.prepareStatement("INSERT INTO " + TABLE_META + "(k, v) VALUES (?, ?)")) {
            ps.setString(1, key);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    private Connection connect() throws SQLException {
        try {
            Class.forName("org.duckdb.DuckDBDriver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("DuckDB 驱动未找到", e);
        }
        File file = new File(properties.getDbFile()).getAbsoluteFile();
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("无法创建知识库目录: " + parent);
        }
        return DriverManager.getConnection("jdbc:duckdb:" + file.getAbsolutePath());
    }

    private String currentTenantId() {
        Long tenantId = TenantContext.getTenantId();
        return tenantId == null ? null : String.valueOf(tenantId);
    }

    private static String physicalTableName(DataModel model) {
        List<String> parts = new ArrayList<>();
        if (model.getSchemaName() != null && !model.getSchemaName().isBlank()) {
            parts.add(model.getSchemaName());
        }
        if (model.getTableName() != null && !model.getTableName().isBlank()) {
            parts.add(model.getTableName());
        } else if (model.getCode() != null && !model.getCode().isBlank()) {
            parts.add(model.getCode());
        }
        return String.join(".", parts);
    }

    private static String joinNonBlank(String... values) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(value.trim());
            }
        }
        return sb.toString();
    }

    private static String toVectorString(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    private static float[] parseVector(String vector) {
        if (vector == null || vector.isBlank()) {
            return new float[0];
        }
        String trimmed = vector.trim();
        if (trimmed.startsWith("[")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("]")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isBlank()) {
            return new float[0];
        }
        String[] parts = trimmed.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }

    private static double cosine(float[] a, float[] b) {
        int length = Math.min(a.length, b.length);
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
        }
        for (int i = 0; i < b.length; i++) {
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
