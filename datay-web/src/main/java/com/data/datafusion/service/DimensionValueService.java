package com.data.datafusion.service;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.dto.DataSourceDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 维度取值查询服务：按维度字段抽样去重取值，供维度成员选择（数据范围、查询条件等）使用。
 */
@Service
@Transactional(readOnly = true)
public class DimensionValueService {

    private static final Logger LOG = LoggerFactory.getLogger(DimensionValueService.class);

    private static final String MODEL_TYPE_DIMENSION = "DIMENSION";

    private static final int DEFAULT_PAGE_SIZE = 20;

    private static final int MAX_PAGE_SIZE = 100;

    private final DataModelRepository dataModelRepository;
    private final ModelFieldRepository modelFieldRepository;
    private final DataSourceService dataSourceService;
    private final DataSourceQueryService dataSourceQueryService;

    public DimensionValueService(
        DataModelRepository dataModelRepository,
        ModelFieldRepository modelFieldRepository,
        DataSourceService dataSourceService,
        DataSourceQueryService dataSourceQueryService
    ) {
        this.dataModelRepository = dataModelRepository;
        this.modelFieldRepository = modelFieldRepository;
        this.dataSourceService = dataSourceService;
        this.dataSourceQueryService = dataSourceQueryService;
    }

    /**
     * 分页查询某个维度字段的去重取值。
     *
     * @param dimensionModelId 维度模型 id
     * @param fieldName        维度字段名
     * @param keyword          取值模糊过滤关键字（可选）
     * @param page             页码，从 1 开始
     * @param size             每页条数
     * @return values / total / page / size
     */
    public Map<String, Object> pageValues(Long dimensionModelId, String fieldName, String keyword, Integer page, Integer size) {
        DataModel dimension = dataModelRepository
            .findById(dimensionModelId)
            .orElseThrow(() -> new IllegalArgumentException("维度模型不存在：" + dimensionModelId));
        if (!MODEL_TYPE_DIMENSION.equalsIgnoreCase(dimension.getModelType())) {
            throw new IllegalArgumentException("只能查询维度模型的取值：" + dimension.getCode());
        }
        boolean fieldExists = modelFieldRepository
            .findByModelIdOrderBySortOrderAsc(dimensionModelId)
            .stream()
            .map(ModelField::getFieldName)
            .anyMatch(fieldName::equals);
        if (!fieldExists) {
            throw new IllegalArgumentException("维度字段不存在：" + dimension.getCode() + "." + fieldName);
        }
        if (dimension.getDataSourceId() == null) {
            throw new IllegalArgumentException("维度模型未绑定数据源：" + dimension.getCode());
        }
        DataSourceDTO dataSource = dataSourceService
            .findOne(dimension.getDataSourceId())
            .orElseThrow(() -> new IllegalArgumentException("数据源不存在：" + dimension.getDataSourceId()));

        int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int currentPage = page == null || page < 1 ? 1 : page;
        String table = physicalTableName(dimension);
        String where = buildWhereClause(fieldName, keyword);

        long total = countDistinct(dataSource, table, fieldName, where);
        long offset = (long) (currentPage - 1) * pageSize;
        String sql =
            "SELECT DISTINCT " +
            fieldName +
            " FROM " +
            table +
            where +
            " ORDER BY 1 LIMIT " +
            pageSize +
            " OFFSET " +
            offset;

        List<String> values = queryValues(dataSource, dimension, fieldName, sql);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("values", values);
        result.put("total", total);
        result.put("page", currentPage);
        result.put("size", pageSize);
        return result;
    }

    private String buildWhereClause(String fieldName, String keyword) {
        StringBuilder where = new StringBuilder(" WHERE ").append(fieldName).append(" IS NOT NULL");
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND ").append(fieldName).append(" LIKE ").append(quote("%" + keyword.trim() + "%"));
        }
        return where.toString();
    }

    private long countDistinct(DataSourceDTO dataSource, String table, String fieldName, String where) {
        String sql = "SELECT COUNT(DISTINCT " + fieldName + ") AS cnt FROM " + table + where;
        try {
            Map<String, Object> result = dataSourceQueryService.executeQuery(dataSource, sql);
            if (result.get("rows") instanceof List<?> rows && !rows.isEmpty()) {
                Object row = rows.get(0);
                if (row instanceof Map<?, ?> map && !map.isEmpty()) {
                    Object value = map.values().iterator().next();
                    if (value instanceof Number number) {
                        return number.longValue();
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("统计维度取值数量失败 {}: {}", fieldName, e.getMessage());
        }
        return 0L;
    }

    private List<String> queryValues(DataSourceDTO dataSource, DataModel dimension, String fieldName, String sql) {
        List<String> values = new ArrayList<>();
        try {
            Map<String, Object> result = dataSourceQueryService.executeQuery(dataSource, sql);
            if (result.get("rows") instanceof List<?> rows) {
                for (Object row : rows) {
                    if (row instanceof Map<?, ?> map && !map.isEmpty()) {
                        Object value = map.values().iterator().next();
                        if (value != null && !String.valueOf(value).isBlank()) {
                            values.add(String.valueOf(value));
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.warn("查询维度取值失败 {}.{}: {}", dimension.getCode(), fieldName, e.getMessage());
            throw new IllegalArgumentException("维度取值查询失败：" + e.getMessage());
        }
        return values;
    }

    private String physicalTableName(DataModel model) {
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

    private String quote(String value) {
        return "'" + value.replace("'", "''") + "'";
    }
}
