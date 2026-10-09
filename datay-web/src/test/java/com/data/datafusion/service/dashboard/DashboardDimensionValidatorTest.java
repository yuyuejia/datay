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

    private Map<String, Object> filter(String id, String mode, String modelCode, String fieldName) {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("id", id);
        filter.put("type", "multiSelect");
        if (mode != null) {
            Map<String, Object> options = new LinkedHashMap<>();
            options.put("mode", mode);
            options.put("dimensionModelCode", modelCode);
            options.put("dimensionFieldName", fieldName);
            filter.put("options", options);
        }
        filter.put("bindings", new ArrayList<>());
        return filter;
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

        Map<String, Object> spec = validator.sanitizeFilters(specWith(List.of(filter("region", "dimension", "dim_region", "region_name"))));

        assertThat((List<?>) spec.get("filters")).hasSize(1);
    }

    @Test
    void shouldDropFilterWhenDimensionMissing() {
        when(dataModelRepository.findFirstByCode("dim_missing")).thenReturn(Optional.empty());

        Map<String, Object> spec = validator.sanitizeFilters(specWith(List.of(filter("region", "dimension", "dim_missing", "region_name"))));

        assertThat((List<?>) spec.get("filters")).isEmpty();
    }

    @Test
    void shouldDropFilterWhenDimensionHasNoData() {
        DataModel model = new DataModel();
        model.setId("2");
        model.setCode("dim_empty");
        when(dataModelRepository.findFirstByCode("dim_empty")).thenReturn(Optional.of(model));
        when(dimensionValueService.pageValues(eq("2"), eq("name"), any(), anyInt(), anyInt())).thenReturn(Map.of("values", List.of()));

        Map<String, Object> spec = validator.sanitizeFilters(specWith(List.of(filter("x", "dimension", "dim_empty", "name"))));

        assertThat((List<?>) spec.get("filters")).isEmpty();
    }

    @Test
    void shouldDropFilterWhenMetricDimensionBindingIsUnavailable() {
        when(dataModelRepository.findFirstByCode("dim_bad")).thenReturn(Optional.empty());
        Map<String, Object> filter = filter("region", null, null, null);
        Map<String, Object> binding = new LinkedHashMap<>();
        binding.put("datasetId", "ds1");
        binding.put("kind", "metricDimension");
        binding.put("dimensionModelCode", "dim_bad");
        binding.put("dimensionFieldName", "region_name");
        filter.put("bindings", List.of(binding));

        Map<String, Object> spec = validator.sanitizeFilters(specWith(List.of(filter)));

        assertThat((List<?>) spec.get("filters")).isEmpty();
    }

    @Test
    void shouldKeepFilterWithoutDimensionReference() {
        Map<String, Object> dateRange = new LinkedHashMap<>();
        dateRange.put("id", "date_range");
        dateRange.put("type", "dateRange");
        dateRange.put("bindings", new ArrayList<>());

        Map<String, Object> spec = validator.sanitizeFilters(specWith(List.of(dateRange)));

        assertThat((List<?>) spec.get("filters")).hasSize(1);
    }
}
