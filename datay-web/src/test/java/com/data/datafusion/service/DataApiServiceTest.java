package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.domain.DataApi;
import com.data.datafusion.domain.ETLTaskDirectory;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.data.datafusion.repository.DataApiCallLogRepository;
import com.data.datafusion.repository.DataApiRepository;
import com.data.datafusion.repository.ETLTaskDirectoryRepository;
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

    private ETLTaskDirectoryRepository etlTaskDirectoryRepository;

    private DataApiCallLogRepository dataApiCallLogRepository;

    private DataApiService service;

    @BeforeEach
    void setUp() {
        dataApiRepository = mock(DataApiRepository.class);
        analysisDatasetRepository = mock(AnalysisDatasetRepository.class);
        etlTaskDirectoryRepository = mock(ETLTaskDirectoryRepository.class);
        dataApiCallLogRepository = mock(DataApiCallLogRepository.class);
        service = new DataApiService(
            dataApiRepository,
            mock(DataApiMapper.class),
            analysisDatasetRepository,
            etlTaskDirectoryRepository,
            dataApiCallLogRepository
        );
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

    @Test
    void shouldRejectDirectoryFromAnotherModule() {
        ETLTaskDirectory directory = new ETLTaskDirectory().id("dir-1").taskType(TaskConstants.ETL_TASK_KIND_BATCH);
        when(etlTaskDirectoryRepository.findById("dir-1")).thenReturn(Optional.of(directory));

        DataApiDTO dto = tableApi();
        dto.setDirectoryId("dir-1");

        assertThatThrownBy(() -> service.save(dto))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("目录与数据服务类型不匹配");
    }

    @Test
    void shouldRejectUnknownDirectory() {
        when(etlTaskDirectoryRepository.findById("dir-1")).thenReturn(Optional.empty());

        DataApiDTO dto = tableApi();
        dto.setDirectoryId("dir-1");

        assertThatThrownBy(() -> service.save(dto)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("目录不存在");
    }

    @Test
    void shouldRejectMovingToDirectoryFromAnotherModule() {
        ETLTaskDirectory directory = new ETLTaskDirectory().id("dir-1").taskType(TaskConstants.ETL_TASK_KIND_BATCH);
        when(dataApiRepository.findById("api-1")).thenReturn(Optional.of(new DataApi().id("api-1")));
        when(etlTaskDirectoryRepository.findById("dir-1")).thenReturn(Optional.of(directory));

        assertThatThrownBy(() -> service.moveToDirectory("api-1", "dir-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("目录与数据服务类型不匹配");
    }

    @Test
    void shouldClearCallLogsWhenDeletingApi() {
        service.delete("api-1");

        verify(dataApiCallLogRepository).deleteByApiId("api-1");
        verify(dataApiRepository).deleteById("api-1");
    }

    private static DataApiDTO tableApi() {
        DataApiDTO dto = new DataApiDTO();
        dto.setName("订单查询服务");
        dto.setCode("orders_api");
        dto.setSourceType(DataApi.SOURCE_TYPE_TABLE);
        dto.setDataSourceId("ds-1");
        dto.setSchemaName("public");
        dto.setTableName("orders");
        return dto;
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
