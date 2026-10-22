package com.data.datafusion.service.dashboard;

import com.data.datafusion.domain.AnalysisDashboard;
import com.data.datafusion.domain.DataModel;
import com.data.datafusion.repository.AnalysisDashboardRepository;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.service.DimensionValueService;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        Map<String, Object> dataset = findDataset(resolved, datasetId);
        return datasetQueryService.query(dataset, fallbackDataSourceId, asMapList(resolved.get("filters")), filterValues);
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
        String mode = DatasetQueryService.asString(options.get("mode"));
        int size = limit == null ? 50 : Math.max(1, Math.min(limit, 100));
        if ("dimension".equalsIgnoreCase(mode)) {
            String modelCode = DatasetQueryService.asString(options.get("dimensionModelCode"));
            String fieldName = DatasetQueryService.asString(options.get("dimensionFieldName"));
            if (modelCode == null || fieldName == null) {
                throw new IllegalArgumentException("筛选器 " + filterId + " 未配置维度字段");
            }
            DataModel model = dataModelRepository
                .findFirstByCode(modelCode)
                .orElseThrow(() -> new IllegalArgumentException("维度模型不存在：" + modelCode));
            return dimensionValueService.pageValues(model.getId(), fieldName, keyword, 1, size);
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
