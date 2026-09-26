package com.data.datafusion.service.dashboard;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.service.DimensionValueService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 看板筛选器维度可用性校验。
 *
 * <p>下拉/维度类筛选器依赖的维度若不存在、字段不存在或没有数据，就不应在看板中展示。
 * 这里在入库与预检时对 spec 的 filters 做一次清洗：剔除引用了不可用维度的筛选器。
 */
@Component
public class DashboardDimensionValidator {

    private static final Logger LOG = LoggerFactory.getLogger(DashboardDimensionValidator.class);

    private static final String BINDING_METRIC_DIMENSION = "metricDimension";

    private final DataModelRepository dataModelRepository;
    private final DimensionValueService dimensionValueService;

    public DashboardDimensionValidator(DataModelRepository dataModelRepository, DimensionValueService dimensionValueService) {
        this.dataModelRepository = dataModelRepository;
        this.dimensionValueService = dimensionValueService;
    }

    /**
     * 清洗 spec 中的筛选器：移除引用了不可用维度的筛选器，原地修改并返回 spec。
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> sanitizeFilters(Map<String, Object> spec) {
        if (spec == null || !(spec.get("filters") instanceof List<?> filters)) {
            return spec;
        }
        List<Map<String, Object>> kept = new ArrayList<>();
        Map<String, Boolean> cache = new HashMap<>();
        for (Object element : filters) {
            if (!(element instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> filter = new LinkedHashMap<>((Map<String, Object>) map);
            if (isFilterUsable(filter, cache)) {
                kept.add(filter);
            }
        }
        spec.put("filters", kept);
        return spec;
    }

    private boolean isFilterUsable(Map<String, Object> filter, Map<String, Boolean> cache) {
        List<String[]> dimensions = referencedDimensions(filter);
        if (dimensions.isEmpty()) {
            return true;
        }
        for (String[] dimension : dimensions) {
            String modelCode = dimension[0];
            String fieldName = dimension[1];
            if (modelCode == null || modelCode.isBlank() || fieldName == null || fieldName.isBlank()) {
                return false;
            }
            if (!dimensionHasData(modelCode, fieldName, cache)) {
                LOG.debug("剔除不可用维度筛选器 {}: {}.{}", filter.get("id"), modelCode, fieldName);
                return false;
            }
        }
        return true;
    }

    /** 收集筛选器引用的维度（下拉选项维度 + 指标维度绑定）。 */
    private List<String[]> referencedDimensions(Map<String, Object> filter) {
        List<String[]> dimensions = new ArrayList<>();
        Map<String, Object> options = asMap(filter.get("options"));
        if ("dimension".equalsIgnoreCase(asString(options.get("mode")))) {
            dimensions.add(new String[] { asString(options.get("dimensionModelCode")), asString(options.get("dimensionFieldName")) });
        }
        for (Object element : asList(filter.get("bindings"))) {
            Map<String, Object> binding = asMap(element);
            if (BINDING_METRIC_DIMENSION.equals(asString(binding.get("kind")))) {
                dimensions.add(
                    new String[] { asString(binding.get("dimensionModelCode")), asString(binding.get("dimensionFieldName")) }
                );
            }
        }
        return dimensions;
    }

    private boolean dimensionHasData(String modelCode, String fieldName, Map<String, Boolean> cache) {
        return cache.computeIfAbsent(modelCode + "." + fieldName, key -> {
            Optional<DataModel> model = dataModelRepository.findFirstByCode(modelCode);
            if (model.isEmpty()) {
                return false;
            }
            try {
                Map<String, Object> result = dimensionValueService.pageValues(model.get().getId(), fieldName, null, 1, 1);
                Object values = result.get("values");
                return values instanceof List<?> list && !list.isEmpty();
            } catch (RuntimeException e) {
                LOG.debug("校验维度取值失败 {}.{}: {}", modelCode, fieldName, e.getMessage());
                return false;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object raw) {
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return new LinkedHashMap<>();
    }

    private static List<?> asList(Object raw) {
        return raw instanceof List<?> list ? list : List.of();
    }

    private static String asString(Object raw) {
        return raw == null ? null : String.valueOf(raw);
    }
}
