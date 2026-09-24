package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.time.TimeGranularity;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link TimeDimensionDataService} 预置数据计算的单元测试。
 */
class TimeDimensionDataServiceTest {

    private final TimeDimensionDataService service = new TimeDimensionDataService(
        mock(DataModelRepository.class),
        mock(ModelFieldRepository.class),
        mock(DataSourceService.class)
    );

    @Test
    void shouldGenerateMonthlyRowsWhenFinestIsMonth() {
        List<Map<String, String>> rows = service.buildRows(
            List.of(TimeGranularity.YEAR, TimeGranularity.MONTH),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 3, 31)
        );
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0))
            .containsEntry("year_id", "2024")
            .containsEntry("year_name", "2024年")
            .containsEntry("month_id", "202401")
            .containsEntry("month_name", "2024-01")
            .containsEntry("date_key", "202401")
            .containsEntry("hierarchy", "2024年/2024-01");
    }

    @Test
    void shouldGenerateDailyRowsWithFullHierarchy() {
        List<Map<String, String>> rows = service.buildRows(
            List.of(TimeGranularity.YEAR, TimeGranularity.QUARTER, TimeGranularity.MONTH, TimeGranularity.DAY),
            LocalDate.of(2024, 1, 1),
            LocalDate.of(2024, 1, 3)
        );
        assertThat(rows).hasSize(3);
        assertThat(rows.get(0))
            .containsEntry("date_key", "20240101")
            .containsEntry("day_id", "20240101")
            .containsEntry("day_name", "2024-01-01")
            .containsEntry("hierarchy", "2024年/2024年Q1/2024-01/2024-01-01");
    }
}
