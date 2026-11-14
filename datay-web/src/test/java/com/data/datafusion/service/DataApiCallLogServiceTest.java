package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.DataApiCallLog;
import com.data.datafusion.repository.DataApiCallLogRepository;
import com.data.datafusion.repository.DataApiCallLogRepository.DailyStat;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.dto.DataApiCallStatsDTO;
import com.data.datafusion.service.dto.DataApiDTO;
import com.data.datafusion.service.mapper.DataApiCallLogMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class DataApiCallLogServiceTest {

    private DataApiCallLogRepository callLogRepository;

    private DataApiService dataApiService;

    private DataApiCallLogService service;

    @BeforeEach
    void setUp() {
        callLogRepository = mock(DataApiCallLogRepository.class);
        dataApiService = mock(DataApiService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        service = new DataApiCallLogService(callLogRepository, mock(DataApiCallLogMapper.class), dataApiService, transactionManager);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void shouldFillMissingDaysAndCalculateSummary() {
        LocalDate start = LocalDate.of(2026, 11, 10);
        LocalDate end = LocalDate.of(2026, 11, 12);
        when(dataApiService.findOne("api-1")).thenReturn(Optional.of(api()));
        when(callLogRepository.findDailyStats("api-1", start, end)).thenReturn(
            List.of(stat(start, 10, 9, 1, 500, 120), stat(end, 30, 30, 0, 900, 60))
        );

        DataApiCallStatsDTO result = service.getStats("api-1", start, end);

        assertThat(result.getTotalCount()).isEqualTo(40);
        assertThat(result.getSuccessCount()).isEqualTo(39);
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getSuccessRate()).isEqualTo(97.5);
        assertThat(result.getAvgDurationMs()).isEqualTo(35.0);
        assertThat(result.getMaxDurationMs()).isEqualTo(120);
        assertThat(result.getTrend()).hasSize(3);
        assertThat(result.getTrend().get(1).getDate()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(result.getTrend().get(1).getTotalCount()).isZero();
    }

    @Test
    void shouldReturnZeroStatsWhenNoLogs() {
        LocalDate start = LocalDate.of(2026, 11, 10);
        LocalDate end = LocalDate.of(2026, 11, 11);
        when(dataApiService.findOne("api-1")).thenReturn(Optional.of(api()));
        when(callLogRepository.findDailyStats("api-1", start, end)).thenReturn(List.of());

        DataApiCallStatsDTO result = service.getStats("api-1", start, end);

        assertThat(result.getTotalCount()).isZero();
        assertThat(result.getSuccessRate()).isZero();
        assertThat(result.getTrend()).hasSize(2);
    }

    @Test
    void shouldRejectReversedDateRange() {
        LocalDate start = LocalDate.of(2026, 11, 12);
        LocalDate end = LocalDate.of(2026, 11, 10);

        assertThatThrownBy(() -> service.getStats("api-1", start, end))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("结束日期不能早于开始日期");
    }

    @Test
    void shouldMaskSensitiveParamsWhenRecording() {
        TenantContext.setTenantId(9L);
        ArgumentCaptor<DataApiCallLog> captor = ArgumentCaptor.forClass(DataApiCallLog.class);

        service.record(
            api(),
            "orders",
            DataApiCallMeta.openApi("GET", "/open-api/data/orders", "127.0.0.1", "JUnit"),
            Map.of("status", "PAID", "access_token", "secret-token", "password", "123456"),
            DataApiCallLog.STATUS_SUCCESS,
            200,
            null,
            12,
            3L
        );

        verify(callLogRepository).save(captor.capture());
        DataApiCallLog saved = captor.getValue();
        assertThat(saved.getApiId()).isEqualTo("api-1");
        assertThat(saved.getApiCode()).isEqualTo("orders_api");
        assertThat(saved.getStatus()).isEqualTo(DataApiCallLog.STATUS_SUCCESS);
        assertThat(saved.getDurationMs()).isEqualTo(12);
        assertThat(saved.getResultRows()).isEqualTo(3L);
        assertThat(saved.getTenantId()).isEqualTo("9");
        assertThat(saved.getRequestParams()).contains("\"status\":\"PAID\"");
        assertThat(saved.getRequestParams()).doesNotContain("secret-token").doesNotContain("123456");
    }

    private static DataApiDTO api() {
        DataApiDTO dto = new DataApiDTO();
        dto.setId("api-1");
        dto.setCode("orders_api");
        dto.setName("订单查询服务");
        return dto;
    }

    private static DailyStat stat(LocalDate date, long total, long success, long failure, long duration, long max) {
        return new DailyStat() {
            @Override
            public LocalDate getStatDate() {
                return date;
            }

            @Override
            public Long getTotalCount() {
                return total;
            }

            @Override
            public Long getSuccessCount() {
                return success;
            }

            @Override
            public Long getFailureCount() {
                return failure;
            }

            @Override
            public Long getTotalDurationMs() {
                return duration;
            }

            @Override
            public Long getMaxDurationMs() {
                return max;
            }
        };
    }
}
