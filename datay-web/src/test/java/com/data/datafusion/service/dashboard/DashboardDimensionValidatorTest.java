package com.data.datafusion.service.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.service.DimensionValueService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link DashboardDimensionValidator} 筛选器维度可用性清洗测试。
 */
class DashboardDimensionValidatorTest {

    private DataModelRepository dataModelRepository;
    private DimensionValueService dimensionValueService;
    private DashboardDimensionValidator validator;

    @BeforeEach
    void setUp() {
        dataModelRepository = mock(DataModelRepository.class);
        dimensionValueService = mock(DimensionValueService.class);
        validator = new DashboardDimensionValidator(dataModelRepository, dimensionValueService);
    }

    private Map<String, Object> filter(String id, Map<String, Object> pushdown) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("id", id);
        filter.put("type", "multiSelect");
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("datasetId", "ds1");
        if (pushdown == null) {
            target.put("field", "region");
            target.put("mode", "server");
        } else {
            target.put("mode", "server");
            target.put("pushdown", pushdown);
        }
        filter.put("targets", List.of(target));
        return filter;
    }

    private Map<String, Object> metricDimension(String modelCode, String fieldName) {
        Map<String, Object> pushdown = new LinkedHashMap<>();
        pushdown.put("kind", "metricDimension");
        pushdown.put("dimensionModelCode", modelCode);
        pushdown.put("dimensionFieldName", fieldName);
        return pushdown;
    }

    private Map<String, Object> specWith(List<Map<String, Object>> filters) {
        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("filters", new ArrayList<>(filters));
        return spec;
    }

    @Test
    void shouldKeepFilterWhenDimensionHasData() {
        DataModel model = new DataModel();
        model.setId("1");
        model.setCode("dim_region");
        when(dataModelRepository.findFirstByCode("dim_region")).thenReturn(Optional.of(model));
        when(dimensionValueService.pageValues(eq("1"), eq("region_name"), any(), anyInt(), anyInt()))
            .thenReturn(Map.of("values", List.of("华东")));

        Map<String, Object> spec = validator.sanitizeFilters(
            specWith(List.of(filter("region", metricDimension("dim_region", "region_name"))))
        );

        assertThat((List<?>) spec.get("filters")).hasSize(1);
    }

    @Test
    void shouldDropFilterWhenDimensionMissing() {
        when(dataModelRepository.findFirstByCode("dim_missing")).thenReturn(Optional.empty());

        Map<String, Object> spec = validator.sanitizeFilters(
            specWith(List.of(filter("region", metricDimension("dim_missing", "region_name"))))
        );

        assertThat((List<?>) spec.get("filters")).isEmpty();
    }

    @Test
    void shouldDropFilterWhenDimensionHasNoData() {
        DataModel model = new DataModel();
        model.setId("2");
        model.setCode("dim_empty");
        when(dataModelRepository.findFirstByCode("dim_empty")).thenReturn(Optional.of(model));
        when(dimensionValueService.pageValues(eq("2"), eq("name"), any(), anyInt(), anyInt())).thenReturn(Map.of("values", List.of()));

        Map<String, Object> spec = validator.sanitizeFilters(
            specWith(List.of(filter("x", metricDimension("dim_empty", "name"))))
        );

        assertThat((List<?>) spec.get("filters")).isEmpty();
    }

    @Test
    void shouldDropFilterWhenMetricDimensionPushdownIsUnavailable() {
        when(dataModelRepository.findFirstByCode("dim_bad")).thenReturn(Optional.empty());

        Map<String, Object> spec = validator.sanitizeFilters(
            specWith(List.of(filter("region", metricDimension("dim_bad", "region_name"))))
        );

        assertThat((List<?>) spec.get("filters")).isEmpty();
    }

    @Test
    void shouldKeepServerFieldTargetWithoutDimensionReference() {
        when(dataModelRepository.findFirstByCode("dim_missing")).thenReturn(Optional.empty());

        Map<String, Object> spec = validator.sanitizeFilters(specWith(List.of(filter("region", null))));

        assertThat((List<?>) spec.get("filters")).hasSize(1);
    }

    @Test
    void shouldKeepFilterWithNonDimensionPushdown() {
        Map<String, Object> pushdown = new LinkedHashMap<>();
        pushdown.put("kind", "metricTime");

        Map<String, Object> spec = validator.sanitizeFilters(specWith(List.of(filter("date_range", pushdown))));

        assertThat((List<?>) spec.get("filters")).hasSize(1);
    }

    @Test
    void shouldDropFilterWithoutTargets() {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("id", "empty");
        filter.put("type", "dateRange");
        filter.put("targets", List.of());

        Map<String, Object> spec = validator.sanitizeFilters(specWith(List.of(filter)));

        assertThat((List<?>) spec.get("filters")).isEmpty();
    }
}
