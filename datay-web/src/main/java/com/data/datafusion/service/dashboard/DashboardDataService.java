package com.data.datafusion.service.dashboard;

import com.data.datafusion.domain.AnalysisDashboard;
import com.data.datafusion.domain.DataModel;
import com.data.datafusion.repository.AnalysisDashboardRepository;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.service.DimensionValueService;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 看板数据查询服务。
 *
 * <p>按看板 spec 中的数据集定义执行查询。数据集既可以是内嵌定义，也可以是对
 * {@link com.data.datafusion.domain.AnalysisDataset} 的引用，引用会在执行前解析为最新定义。
 * 实际的指标 / SQL 执行由 {@link DatasetQueryService} 完成。
 */
@Service
public class DashboardDataService {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardDataService.class);

    /** 已保存看板的批量查询结果缓存；设计页预览不缓存，避免草稿互相污染。 */
    private static final long QUERY_CACHE_TTL_MS = 30_000L;
    private static final int QUERY_CACHE_MAX_ENTRIES = 256;
    private final Map<String, CacheEntry> queryCache = Collections.synchronizedMap(
        new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CacheEntry> eldest) {
                return size() > QUERY_CACHE_MAX_ENTRIES;
            }
        }
    );

    private final AnalysisDashboardRepository dashboardRepository;
    private final DashboardSpecValidator specValidator;
    private final AnalysisDatasetResolver datasetResolver;
    private final DatasetQueryService datasetQueryService;
    private final DimensionValueService dimensionValueService;
    private final DataModelRepository dataModelRepository;

    public DashboardDataService(
        AnalysisDashboardRepository dashboardRepository,
        DashboardSpecValidator specValidator,
        AnalysisDatasetResolver datasetResolver,
        DatasetQueryService datasetQueryService,
        DimensionValueService dimensionValueService,
        DataModelRepository dataModelRepository
    ) {
        this.dashboardRepository = dashboardRepository;
        this.specValidator = specValidator;
        this.datasetResolver = datasetResolver;
        this.datasetQueryService = datasetQueryService;
        this.dimensionValueService = dimensionValueService;
        this.dataModelRepository = dataModelRepository;
    }

    /**
     * 执行看板的某个数据集。
     *
     * @param dashboardId  看板 id
     * @param datasetId    数据集 id（看板本地别名）
     * @param filterValues 运行时筛选器值，键为筛选器 id；缺省时回退到 spec 中的 defaultValue
     * @return {@code columns} / {@code rows} / {@code affectedRows} / {@code sql}
     */
    @Transactional(readOnly = true)
    public Map<String, Object> queryDataset(String dashboardId, String datasetId, Map<String, Object> filterValues) throws SQLException {
        AnalysisDashboard dashboard = dashboardRepository
            .findById(dashboardId)
            .orElseThrow(() -> new IllegalArgumentException("看板不存在：" + dashboardId));
        Map<String, Object> spec = datasetResolver.resolveSpec(specValidator.parse(dashboard.getSpec()));
        return queryDataset(spec, dashboard.getDataSourceId(), datasetId, filterValues);
    }

    /**
     * 基于内存中的看板 spec 执行数据集，用于设计页未保存时的实时预览。
     */
    public Map<String, Object> queryDataset(
        Map<String, Object> spec,
        String fallbackDataSourceId,
        String datasetId,
        Map<String, Object> filterValues
    ) throws SQLException {
        Map<String, Object> resolved = datasetResolver.resolveSpec(spec);
        return executeDataset(resolved, fallbackDataSourceId, datasetId, filterValues);
    }

    /**
     * 批量执行看板的多个数据集；单个数据集失败不影响其他结果。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> queryDatasets(String dashboardId, List<String> datasetIds, Map<String, Object> filterValues) {
        AnalysisDashboard dashboard = dashboardRepository
            .findById(dashboardId)
            .orElseThrow(() -> new IllegalArgumentException("看板不存在：" + dashboardId));
        Map<String, Object> spec = datasetResolver.resolveSpec(specValidator.parse(dashboard.getSpec()));
        Map<String, Object> result = queryAll(spec, dashboard.getDataSourceId(), datasetIds, List.of(), filterValues);
        result.remove("widgets");
        result.remove("widgetErrors");
        return result;
    }

    /**
     * 基于内存 spec 批量执行数据集，供设计器预览复用。
     */
    public Map<String, Object> queryDatasets(
        Map<String, Object> spec,
        String fallbackDataSourceId,
        List<String> datasetIds,
        Map<String, Object> filterValues
    ) {
        Map<String, Object> result = queryAll(spec, fallbackDataSourceId, datasetIds, List.of(), filterValues);
        result.remove("widgets");
        result.remove("widgetErrors");
        return result;
    }

    /**
     * 批量执行数据集原始查询与组件级查询（带 encode.sort/limit）。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> queryWidgets(
        String dashboardId,
        List<String> datasetIds,
        List<Map<String, Object>> queries,
        Map<String, Object> filterValues
    ) {
        String cacheKey = queryCacheKey(dashboardId, datasetIds, queries, filterValues);
        Map<String, Object> cached = cachedResult(cacheKey);
        if (cached != null) {
            LOG.debug("Dashboard query cache hit: {}", dashboardId);
            return cached;
        }
        AnalysisDashboard dashboard = dashboardRepository
            .findById(dashboardId)
            .orElseThrow(() -> new IllegalArgumentException("看板不存在：" + dashboardId));
        Map<String, Object> spec = datasetResolver.resolveSpec(specValidator.parse(dashboard.getSpec()));
        Map<String, Object> result = queryAll(spec, dashboard.getDataSourceId(), datasetIds, queries, filterValues);
        putCachedResult(cacheKey, result);
        return result;
    }

    /** 清除某个看板的查询缓存；看板保存/删除后可主动调用。 */
    public void evictDashboardCache(String dashboardId) {
        if (dashboardId == null || dashboardId.isBlank()) {
            return;
        }
        synchronized (queryCache) {
            queryCache.keySet().removeIf(key -> key.startsWith(dashboardId + "|"));
        }
    }

    /** 清空全部查询缓存，供测试或运维手动刷新使用。 */
    public void clearQueryCache() {
        queryCache.clear();
    }

    private Map<String, Object> cachedResult(String key) {
        CacheEntry entry = queryCache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.expiresAt() < System.currentTimeMillis()) {
            queryCache.remove(key);
            return null;
        }
        return entry.result();
    }

    private void putCachedResult(String key, Map<String, Object> result) {
        if (result == null || hasErrors(result.get("errors")) || hasErrors(result.get("widgetErrors"))) {
            return;
        }
        queryCache.put(key, new CacheEntry(result, System.currentTimeMillis() + QUERY_CACHE_TTL_MS));
    }

    private boolean hasErrors(Object raw) {
        return raw instanceof Map<?, ?> map && !map.isEmpty();
    }

    private String queryCacheKey(
        String dashboardId,
        List<String> datasetIds,
        List<Map<String, Object>> queries,
        Map<String, Object> filterValues
    ) {
        List<String> ids = datasetIds == null ? new ArrayList<>() : new ArrayList<>(datasetIds);
        Collections.sort(ids);
        List<String> queryParts = new ArrayList<>();
        for (Map<String, Object> query : queries == null ? List.<Map<String, Object>>of() : queries) {
            queryParts.add(
                String.join(
                    "|",
                    String.valueOf(query.get("id")),
                    String.valueOf(query.get("datasetId")),
                    String.valueOf(query.get("sortField")),
                    String.valueOf(query.get("sortOrder")),
                    String.valueOf(query.get("limit")),
                    String.valueOf(query.get("offset")),
                    String.valueOf(query.get("groupFields")),
                    String.valueOf(query.get("aggregations"))
                )
            );
        }
        Collections.sort(queryParts);
        Map<String, Object> filters = filterValues == null ? Map.of() : new TreeMap<>(filterValues);
        return dashboardId + "|" + ids + "|" + queryParts + "|" + filters;
    }

    private record CacheEntry(Map<String, Object> result, long expiresAt) {}

    public Map<String, Object> queryWidgets(
        Map<String, Object> spec,
        String fallbackDataSourceId,
        List<String> datasetIds,
        List<Map<String, Object>> queries,
        Map<String, Object> filterValues
    ) {
        return queryAll(spec, fallbackDataSourceId, datasetIds, queries, filterValues);
    }

    private Map<String, Object> queryAll(
        Map<String, Object> spec,
        String fallbackDataSourceId,
        List<String> datasetIds,
        List<Map<String, Object>> queries,
        Map<String, Object> filterValues
    ) {
        Map<String, Object> resolved = datasetResolver.resolveSpec(spec);
        boolean hasQueries = queries != null && !queries.isEmpty();
        List<String> requested = datasetIds == null || (datasetIds.isEmpty() && !hasQueries)
            ? asMapList(resolved.get("datasets")).stream().map(dataset -> DatasetQueryService.asString(dataset.get("id"))).toList()
            : datasetIds;
        Map<String, Object> datasetsResult = new LinkedHashMap<>();
        Map<String, Object> errors = new LinkedHashMap<>();
        Map<String, Object> widgetResult = new LinkedHashMap<>();
        Map<String, Object> widgetErrors = new LinkedHashMap<>();
        Map<String, Map<String, Object>> datasetById = new LinkedHashMap<>();
        for (Map<String, Object> dataset : asMapList(resolved.get("datasets"))) {
            String id = DatasetQueryService.asString(dataset.get("id"));
            if (id != null && !id.isBlank()) {
                datasetById.put(id, dataset);
            }
        }
        for (String datasetId : requested) {
            if (datasetId == null || datasetId.isBlank()) {
                continue;
            }
            Map<String, Object> dataset = datasetById.get(datasetId);
            if (dataset == null) {
                errors.put(datasetId, "数据集不存在：" + datasetId);
                continue;
            }
            try {
                datasetsResult.put(
                    datasetId,
                    executeDataset(dataset, resolved, fallbackDataSourceId, filterValues, DatasetQueryOptions.NONE)
                );
            } catch (SQLException | RuntimeException e) {
                errors.put(datasetId, e.getMessage() == null ? "查询失败" : e.getMessage());
            }
        }
        for (Map<String, Object> query : queries == null ? List.<Map<String, Object>>of() : queries) {
            String queryId = DatasetQueryService.asString(query.get("id"));
            String datasetId = DatasetQueryService.asString(query.get("datasetId"));
            if (queryId == null || queryId.isBlank() || datasetId == null || datasetId.isBlank()) {
                continue;
            }
            Map<String, Object> dataset = datasetById.get(datasetId);
            if (dataset == null) {
                widgetErrors.put(queryId, "数据集不存在：" + datasetId);
                continue;
            }
            try {
                DatasetQueryOptions options = queryOptions(query);
                widgetResult.put(queryId, executeDataset(dataset, resolved, fallbackDataSourceId, filterValues, options));
            } catch (SQLException | RuntimeException e) {
                widgetErrors.put(queryId, e.getMessage() == null ? "查询失败" : e.getMessage());
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("datasets", datasetsResult);
        response.put("widgets", widgetResult);
        if (!errors.isEmpty()) {
            response.put("errors", errors);
        }
        if (!widgetErrors.isEmpty()) {
            response.put("widgetErrors", widgetErrors);
        }
        return response;
    }

    private DatasetQueryOptions queryOptions(Map<String, Object> query) {
        Long limit = DatasetQueryService.asLong(query.get("limit"));
        Long offset = DatasetQueryService.asLong(query.get("offset"));
        String sortOrder = DatasetQueryService.asString(query.get("sortOrder"));
        if (sortOrder != null && !"asc".equalsIgnoreCase(sortOrder) && !"desc".equalsIgnoreCase(sortOrder)) {
            throw new IllegalArgumentException("排序方向只能是 asc 或 desc：" + sortOrder);
        }
        List<String> groupFields = DatasetQueryService.asStringList(query.get("groupFields"));
        List<DatasetQueryOptions.Aggregation> aggregations = new ArrayList<>();
        for (Map<String, Object> item : DatasetQueryService.asMapList(query.get("aggregations"))) {
            String field = DatasetQueryService.asString(item.get("field"));
            if (field == null || field.isBlank()) {
                continue;
            }
            String agg = DatasetQueryService.asString(item.get("agg"));
            aggregations.add(new DatasetQueryOptions.Aggregation(field.trim(), agg == null ? "sum" : agg.trim()));
        }
        return new DatasetQueryOptions(
            DatasetQueryService.asString(query.get("sortField")),
            sortOrder,
            limit == null ? null : limit.intValue(),
            offset == null ? null : offset.intValue(),
            groupFields,
            aggregations
        );
    }

    private Map<String, Object> executeDataset(
        Map<String, Object> resolved,
        String fallbackDataSourceId,
        String datasetId,
        Map<String, Object> filterValues
    ) throws SQLException {
        return executeDataset(
            findDataset(resolved, datasetId),
            resolved,
            fallbackDataSourceId,
            filterValues,
            DatasetQueryOptions.NONE
        );
    }

    private Map<String, Object> executeDataset(
        Map<String, Object> dataset,
        Map<String, Object> resolved,
        String fallbackDataSourceId,
        Map<String, Object> filterValues,
        DatasetQueryOptions options
    ) throws SQLException {
        String datasetScopeId = DatasetQueryService.asString(dataset.get("refId"));
        return datasetQueryService.query(
            dataset,
            fallbackDataSourceId,
            asMapList(resolved.get("filters")),
            filterValues,
            datasetScopeId,
            options
        );
    }

    /**
     * 加载筛选器选项：动态维度成员或静态值。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> loadFilterOptions(String dashboardId, String filterId, String keyword, Integer limit) {
        AnalysisDashboard dashboard = dashboardRepository
            .findById(dashboardId)
            .orElseThrow(() -> new IllegalArgumentException("看板不存在：" + dashboardId));
        Map<String, Object> spec = datasetResolver.resolveSpec(specValidator.parse(dashboard.getSpec()));
        return loadFilterOptions(spec, filterId, keyword, limit);
    }

    /**
     * 基于内存中的看板 spec 加载筛选器选项。
     */
    public Map<String, Object> loadFilterOptions(Map<String, Object> spec, String filterId, String keyword, Integer limit) {
        Map<String, Object> filter = findFilter(spec, filterId);
        Map<String, Object> options = DatasetQueryService.asMap(filter.get("options"));
        int size = limit == null ? 50 : Math.max(1, Math.min(limit, 100));
        for (Map<String, Object> target : FilterTargetResolver.targets(filter)) {
            Map<String, Object> pushdown = FilterTargetResolver.pushdown(target);
            if (!"metricDimension".equals(DatasetQueryService.asString(pushdown.get("kind")))) {
                continue;
            }
            String modelCode = DatasetQueryService.asString(pushdown.get("dimensionModelCode"));
            String fieldName = DatasetQueryService.asString(pushdown.get("dimensionFieldName"));
            if (modelCode == null || fieldName == null) {
                throw new IllegalArgumentException("筛选器 " + filterId + " 未配置维度字段");
            }
            DataModel model = dataModelRepository
                .findFirstByCode(modelCode)
                .orElseThrow(() -> new IllegalArgumentException("维度模型不存在：" + modelCode));
            return dimensionValueService.pageValues(model.getId(), fieldName, keyword, 1, size);
        }
        for (Map<String, Object> target : FilterTargetResolver.targets(filter)) {
            String field = FilterTargetResolver.field(target);
            String datasetId = DatasetQueryService.asString(target.get("datasetId"));
            if (field == null || datasetId == null || datasetId.isBlank()) {
                continue;
            }
            Map<String, Object> dataset = findDataset(spec, datasetId);
            String refId = DatasetQueryService.asString(dataset.get("refId"));
            if (refId == null || refId.isBlank()) {
                continue;
            }
            try {
                return datasetQueryService.loadFieldValues(refId, field, keyword, 1, size);
            } catch (SQLException | RuntimeException e) {
                LOG.warn("加载筛选器字段选项失败 {}.{}: {}", datasetId, field, e.getMessage());
            }
        }
        List<String> staticValues = DatasetQueryService.asStringList(options.get("values"));
        List<String> filtered = new ArrayList<>();
        for (String value : staticValues) {
            if (keyword == null || keyword.isBlank() || value.toLowerCase().contains(keyword.toLowerCase())) {
                filtered.add(value);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("values", filtered);
        result.put("total", filtered.size());
        return result;
    }

    // ---------------------------------------------------------------- helpers

    private Map<String, Object> findDataset(Map<String, Object> spec, String datasetId) {
        for (Map<String, Object> dataset : asMapList(spec.get("datasets"))) {
            if (datasetId.equals(DatasetQueryService.asString(dataset.get("id")))) {
                return dataset;
            }
        }
        throw new IllegalArgumentException("数据集不存在：" + datasetId);
    }

    private Map<String, Object> findFilter(Map<String, Object> spec, String filterId) {
        for (Map<String, Object> filter : asMapList(spec.get("filters"))) {
            if (filterId.equals(DatasetQueryService.asString(filter.get("id")))) {
                return filter;
            }
        }
        throw new IllegalArgumentException("筛选器不存在：" + filterId);
    }

    public static Map<String, Object> asMap(Object raw) {
        return DatasetQueryService.asMap(raw);
    }

    public static List<Map<String, Object>> asMapList(Object raw) {
        return DatasetQueryService.asMapList(raw);
    }

    public static Long asLong(Object raw) {
        return DatasetQueryService.asLong(raw);
    }

    public static String neutralizeEmptyParams(String sql, Map<String, Object> namedParams) {
        return DatasetQueryService.neutralizeEmptyParams(sql, namedParams);
    }

    public static String renderSql(String sql, Map<String, Object> namedParams) {
        return DatasetQueryService.renderSql(sql, namedParams);
    }
}
