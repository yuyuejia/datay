package com.data.datafusion.service.apppackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.data.datafusion.service.AnalysisDatasetService;
import com.data.datafusion.service.apppackage.model.AppPackageContent;
import com.data.datafusion.service.apppackage.model.AppPackageDataset;
import com.data.datafusion.service.dto.AnalysisDatasetDTO;
import com.data.datafusion.service.dto.AppPackageInitResultDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 资产包覆盖导入数据集时，不沿用旧的持久化配置。
 */
class AppPackageDatasetImportTest {

    @Test
    void shouldResetPersistenceWhenOverwritingDataset() throws Exception {
        AnalysisDatasetRepository datasetRepository = mock(AnalysisDatasetRepository.class);
        AnalysisDatasetService datasetService = mock(AnalysisDatasetService.class);

        AnalysisDataset existing = new AnalysisDataset();
        existing.setId("existing-id");
        existing.setCode("qs_dataset_store_sales");
        existing.setPersistEnabled(Boolean.TRUE);
        when(datasetRepository.findAll()).thenReturn(List.of(existing));
        when(datasetService.update(eq("existing-id"), any())).thenAnswer(invocation -> {
            AnalysisDatasetDTO dto = invocation.getArgument(1);
            dto.setId("new-id");
            return Optional.of(dto);
        });

        AppPackageImportService service = new AppPackageImportService(
            null,
            null,
            null,
            null,
            null,
            null,
            datasetRepository,
            null,
            null,
            null,
            null,
            null,
            null,
            datasetService,
            null,
            null,
            null,
            null,
            null
        );

        AppPackageDataset item = new AppPackageDataset();
        item.oldId = "9600";
        item.dataSourceOldId = "9001";
        item.name = "入门门店月度销售数据集";
        item.code = "qs_dataset_store_sales";
        item.type = "SQL";
        item.definition = new ObjectMapper().readTree(
            """
            { "sql": "SELECT * FROM dim_store WHERE store_name = ${store_name}" }
            """
        );
        AppPackageContent content = new AppPackageContent();
        content.getDatasets().add(item);

        Method method = AppPackageImportService.class.getDeclaredMethod(
            "importDatasets",
            AppPackageContent.class,
            String.class,
            Map.class,
            Map.class,
            Map.class,
            Map.class,
            AppPackageInitResultDTO.class
        );
        method.setAccessible(true);
        method.invoke(
            service,
            content,
            AppPackageImportService.STRATEGY_OVERWRITE,
            Map.of("9001", "source-id"),
            Map.of(),
            Map.of(),
            new LinkedHashMap<>(),
            new AppPackageInitResultDTO()
        );

        ArgumentCaptor<AnalysisDatasetDTO> captor = ArgumentCaptor.forClass(AnalysisDatasetDTO.class);
        verify(datasetService).update(eq("existing-id"), captor.capture());
        assertThat(captor.getValue().getPersistEnabled()).isFalse();
    }
}
