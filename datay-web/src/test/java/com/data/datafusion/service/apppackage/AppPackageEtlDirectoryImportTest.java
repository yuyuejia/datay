package com.data.datafusion.service.apppackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.ETLTaskDirectory;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.AnalysisDashboardRepository;
import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.DataQualityTaskRepository;
import com.data.datafusion.repository.DataSourceRepository;
import com.data.datafusion.repository.ETLTaskDirectoryRepository;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.repository.JobDependRepository;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.repository.MetricDirectoryRepository;
import com.data.datafusion.repository.MetricRepository;
import com.data.datafusion.repository.ModelDirectoryRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.AnalysisDashboardService;
import com.data.datafusion.service.AnalysisDatasetService;
import com.data.datafusion.service.DataQualityTaskService;
import com.data.datafusion.service.ETLTaskService;
import com.data.datafusion.service.JobService;
import com.data.datafusion.service.MetricService;
import com.data.datafusion.service.apppackage.model.AppPackageContent;
import com.data.datafusion.service.apppackage.model.AppPackageEtlTask;
import com.data.datafusion.service.dto.AppPackageInitRequestDTO;
import com.data.datafusion.service.dto.AppPackageInitResultDTO;
import com.data.datafusion.service.dto.ETLTaskDTO;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AppPackageEtlDirectoryImportTest {

    @Test
    void importCreatesBatchAndStreamDirectoriesFromLegacyDirField() {
        ETLTaskDirectoryRepository directoryRepository = mock(ETLTaskDirectoryRepository.class);
        ETLTaskService etlTaskService = mock(ETLTaskService.class);
        AppPackageImportService service = service(directoryRepository, etlTaskService);

        List<ETLTaskDirectory> savedDirectories = new ArrayList<>();
        AtomicInteger sequence = new AtomicInteger();
        when(directoryRepository.save(any(ETLTaskDirectory.class))).thenAnswer(invocation -> {
            ETLTaskDirectory directory = invocation.getArgument(0);
            directory.setId("dir-" + sequence.incrementAndGet());
            savedDirectories.add(directory);
            return directory;
        });
        when(etlTaskService.save(any(ETLTaskDTO.class))).thenAnswer(invocation -> {
            ETLTaskDTO dto = invocation.getArgument(0);
            ETLTaskDTO saved = new ETLTaskDTO();
            saved.setId("task-" + dto.getTaskCode());
            saved.setJobId("job-" + dto.getTaskCode());
            return saved;
        });

        AppPackageContent content = new AppPackageContent();
        content.setEtlTasks(
            List.of(
                etlTask("etl-batch-1", "batch_task_1", TaskConstants.TASK_TYPE_ETL, "/快速入门/维度同步"),
                etlTask("etl-batch-2", "batch_task_2", TaskConstants.TASK_TYPE_ETL, "/快速入门/事实同步"),
                etlTask("etl-stream-1", "stream_task_1", TaskConstants.ETL_TASK_KIND_STREAM, "/快速入门/CDC演示")
            )
        );

        AppPackageInitResultDTO result = service.importContent(content, new AppPackageInitRequestDTO(), null);

        assertThat(result.counts.get("etlTaskDirectories")).isEqualTo(5);
        assertThat(savedDirectories)
            .extracting(ETLTaskDirectory::getName, ETLTaskDirectory::getTaskType)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("快速入门", TaskConstants.ETL_TASK_KIND_BATCH),
                org.assertj.core.groups.Tuple.tuple("维度同步", TaskConstants.ETL_TASK_KIND_BATCH),
                org.assertj.core.groups.Tuple.tuple("事实同步", TaskConstants.ETL_TASK_KIND_BATCH),
                org.assertj.core.groups.Tuple.tuple("快速入门", TaskConstants.ETL_TASK_KIND_STREAM),
                org.assertj.core.groups.Tuple.tuple("CDC演示", TaskConstants.ETL_TASK_KIND_STREAM)
            );

        ETLTaskDirectory batchRoot = savedDirectories.get(0);
        ETLTaskDirectory batchDimension = savedDirectories.get(1);
        ETLTaskDirectory batchFact = savedDirectories.get(2);
        ETLTaskDirectory streamRoot = savedDirectories.get(3);
        ETLTaskDirectory streamCdc = savedDirectories.get(4);
        assertThat(batchDimension.getParentId()).isEqualTo(batchRoot.getId());
        assertThat(batchFact.getParentId()).isEqualTo(batchRoot.getId());
        assertThat(streamCdc.getParentId()).isEqualTo(streamRoot.getId());

        ArgumentCaptor<ETLTaskDTO> dtoCaptor = ArgumentCaptor.forClass(ETLTaskDTO.class);
        org.mockito.Mockito.verify(etlTaskService, org.mockito.Mockito.times(3)).save(dtoCaptor.capture());
        assertThat(dtoCaptor.getAllValues())
            .extracting(ETLTaskDTO::getTaskCode, ETLTaskDTO::getDirectoryId)
            .containsExactly(
                org.assertj.core.groups.Tuple.tuple("batch_task_1", batchDimension.getId()),
                org.assertj.core.groups.Tuple.tuple("batch_task_2", batchFact.getId()),
                org.assertj.core.groups.Tuple.tuple("stream_task_1", streamCdc.getId())
            );
    }

    private AppPackageEtlTask etlTask(String oldId, String taskCode, String type, String dir) {
        AppPackageEtlTask task = new AppPackageEtlTask();
        task.oldId = oldId;
        task.jobOldId = oldId + "-job";
        task.taskName = taskCode;
        task.taskCode = taskCode;
        task.type = type;
        task.dir = dir;
        return task;
    }

    private AppPackageImportService service(ETLTaskDirectoryRepository directoryRepository, ETLTaskService etlTaskService) {
        return new AppPackageImportService(
            mock(DataSourceRepository.class),
            mock(DataModelRepository.class),
            mock(ModelFieldRepository.class),
            mock(ModelDirectoryRepository.class),
            mock(MetricRepository.class),
            mock(MetricDirectoryRepository.class),
            mock(AnalysisDatasetRepository.class),
            mock(AnalysisDashboardRepository.class),
            mock(ETLTaskRepository.class),
            directoryRepository,
            mock(JobRepository.class),
            mock(JobDependRepository.class),
            mock(MetricService.class),
            mock(AnalysisDatasetService.class),
            mock(AnalysisDashboardService.class),
            etlTaskService,
            mock(JobService.class),
            mock(DataQualityTaskRepository.class),
            mock(DataQualityTaskService.class)
        );
    }
}
