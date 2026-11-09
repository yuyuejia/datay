package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.domain.DataApi;
import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.data.datafusion.repository.DataApiRepository;
import com.data.datafusion.service.dto.DataApiDTO;
import com.data.datafusion.service.mapper.DataApiMapper;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link DataApiService} 数据集来源发布校验的单元测试。
 */
class DataApiServiceTest {

    private DataApiRepository dataApiRepository;

    private AnalysisDatasetRepository analysisDatasetRepository;

    private DataApiService service;

    @BeforeEach
    void setUp() {
        dataApiRepository = mock(DataApiRepository.class);
        analysisDatasetRepository = mock(AnalysisDatasetRepository.class);
        service = new DataApiService(dataApiRepository, mock(DataApiMapper.class), analysisDatasetRepository);
    }

    @Test
    void shouldRequireDatasetWhenPublishingDatasetSource() {
        DataApiDTO dto = datasetApi(null);

        assertThatThrownBy(() -> service.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("请选择要发布的分析数据集");
    }

    @Test
    void shouldRejectUnknownDataset() {
        DataApiDTO dto = datasetApi("ds-1");
        when(analysisDatasetRepository.findById("ds-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("数据集不存在");
    }

    @Test
    void shouldRejectDuplicateDatasetPublish() {
        DataApiDTO dto = datasetApi("ds-1");
        AnalysisDataset dataset = new AnalysisDataset();
        dataset.setId("ds-1");
        dataset.setName("订单明细");
        dataset.setStatus(AnalysisDataset.STATUS_ENABLED);
        DataApi existing = new DataApi();
        existing.setId("api-1");
        existing.setCode("orders_api");
        when(analysisDatasetRepository.findById("ds-1")).thenReturn(Optional.of(dataset));
        when(dataApiRepository.findByDatasetId("ds-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("该数据集已发布为数据服务");
    }

    private static DataApiDTO datasetApi(String datasetId) {
        DataApiDTO dto = new DataApiDTO();
        dto.setName("订单数据集服务");
        dto.setCode("orders_dataset_api");
        dto.setSourceType(DataApi.SOURCE_TYPE_DATASET);
        dto.setDatasetId(datasetId);
        return dto;
    }
}
