package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.DataCompareTask;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.DataCompareTaskRepository;
import com.data.datafusion.repository.JobInstanceRepository;
import com.data.datafusion.service.dto.DataCompareResultDTO;
import com.data.file.LocalFileStorageStrategy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class DataCompareResultServiceTest {

    @Test
    void shouldParseCsvLineWithQuotes() {
        List<String> values = DataCompareResultService.parseCsvLine("1,\"a,b\",\"say \"\"hi\"\"\",null");
        assertThat(values).containsExactly("1", "a,b", "say \"hi\"", "null");
    }

    @Test
    void shouldParseSummaryNumbers() {
        List<String> headers = DataCompareResultService.parseCsvLine("left_rows,right_rows,same_rows,diff_rows");
        List<String> values = DataCompareResultService.parseCsvLine("10,9,7,3");
        assertThat(headers).containsExactly("left_rows", "right_rows", "same_rows", "diff_rows");
        assertThat(values).containsExactly("10", "9", "7", "3");
    }

    @Test
    void shouldBuildDtoFromJobInstanceAndSummaryFile(@TempDir Path tempDir) throws Exception {
        JobInstanceRepository jobInstanceRepository = mock(JobInstanceRepository.class);
        DataCompareTaskRepository taskRepository = mock(DataCompareTaskRepository.class);
        DataCompareResultService service = new DataCompareResultService(
            jobInstanceRepository,
            taskRepository,
            new LocalFileStorageStrategy(tempDir.toString())
        );

        String summaryPath = "data-compare/task1/summary_inst1.csv";
        Path summaryFile = tempDir.resolve(summaryPath);
        Files.createDirectories(summaryFile.getParent());
        Files.writeString(
            summaryFile,
            "left_rows,right_rows,same_rows,value_diff_rows,only_left_rows,only_right_rows,diff_rows\n" +
            "4,4,3,1,0,0,1\n",
            StandardCharsets.UTF_8
        );

        long start = Instant.now().minusSeconds(10).toEpochMilli();
        long end = Instant.now().toEpochMilli();
        JobInstance instance = new JobInstance();
        instance.setId("inst1");
        instance.setInstanceCode("code1");
        instance.setJobCode("job1");
        instance.setJobName("verify");
        instance.setStatus(TaskConstants.TASK_STATUS_SUCCESSFUL);
        instance.setStartTime(String.valueOf(start));
        instance.setEndTime(String.valueOf(end));
        instance.setCreateTime(ZonedDateTime.now());
        instance.setTenantId("0");
        instance.setJobContext(
            "{\"units\":[],\"dataCompare\":{" +
            "\"taskId\":\"task1\",\"taskName\":\"verify\"," +
            "\"outputDir\":\"data-compare/task1\"," +
            "\"resultPath\":\"data-compare/task1/diff_result_inst1.csv\"," +
            "\"summaryPath\":\"" + summaryPath + "\"," +
            "\"resultFormat\":\"CSV\"}}"
        );
        when(jobInstanceRepository.findById("inst1")).thenReturn(Optional.of(instance));

        Optional<DataCompareResultDTO> dto = service.findByJobInstanceId("inst1");

        assertThat(dto).isPresent();
        assertThat(dto.get().getId()).isEqualTo("inst1");
        assertThat(dto.get().getTaskId()).isEqualTo("task1");
        assertThat(dto.get().getStatus()).isEqualTo(TaskConstants.TASK_STATUS_SUCCESSFUL);
        assertThat(dto.get().getLeftRowCount()).isEqualTo(4);
        assertThat(dto.get().getRightRowCount()).isEqualTo(4);
        assertThat(dto.get().getSameRowCount()).isEqualTo(3);
        assertThat(dto.get().getDiffRowCount()).isEqualTo(1);
        assertThat(dto.get().getValueDiffCount()).isEqualTo(1);
        assertThat(dto.get().getStartTime()).isNotNull();
        assertThat(dto.get().getEndTime()).isNotNull();
        assertThat(dto.get().getResultPath()).contains("diff_result_inst1.csv");
        assertThat(dto.get().getDownloadUrl()).contains("/by-instance-id/inst1/download");
    }

    @Test
    void shouldSupportCountOnlyResult(@TempDir Path tempDir) throws Exception {
        JobInstanceRepository jobInstanceRepository = mock(JobInstanceRepository.class);
        DataCompareTaskRepository taskRepository = mock(DataCompareTaskRepository.class);
        DataCompareResultService service = new DataCompareResultService(
            jobInstanceRepository,
            taskRepository,
            new LocalFileStorageStrategy(tempDir.toString())
        );

        String summaryPath = "data-compare/task1/summary_inst_count.csv";
        Path summaryFile = tempDir.resolve(summaryPath);
        Files.createDirectories(summaryFile.getParent());
        Files.writeString(summaryFile, "left_rows,right_rows,diff_rows\n10,8,2\n", StandardCharsets.UTF_8);

        JobInstance instance = new JobInstance();
        instance.setId("inst-count");
        instance.setJobName("count-only");
        instance.setStatus(TaskConstants.TASK_STATUS_SUCCESSFUL);
        instance.setJobContext(
            "{\"units\":[],\"dataCompare\":{\"taskId\":\"task1\",\"compareRecordCount\":true,\"compareDetail\":false," +
            "\"tables\":[{\"index\":0,\"leftTable\":\"t1\",\"rightTable\":\"t1\",\"summaryPath\":\"" + summaryPath + "\",\"resultFormat\":\"CSV\"}]}}"
        );
        when(jobInstanceRepository.findById("inst-count")).thenReturn(Optional.of(instance));

        DataCompareResultDTO dto = service.findByJobInstanceId("inst-count").orElseThrow();

        assertThat(dto.getCompareDetail()).isFalse();
        assertThat(dto.getCompareRecordCount()).isTrue();
        assertThat(dto.getLeftRowCount()).isEqualTo(10);
        assertThat(dto.getRightRowCount()).isEqualTo(8);
        assertThat(dto.getDiffRowCount()).isEqualTo(2);
        assertThat(dto.getSameRowCount()).isNull();
        assertThat(dto.getTables()).hasSize(1);
        assertThat(dto.getTables().get(0).getResultPath()).isNull();
        assertThat(dto.getTables().get(0).getDiffRowCount()).isEqualTo(2);
    }

    @Test
    void shouldUseJobInstanceFailedStatusAndMessage(@TempDir Path tempDir) {
        JobInstanceRepository jobInstanceRepository = mock(JobInstanceRepository.class);
        DataCompareTaskRepository taskRepository = mock(DataCompareTaskRepository.class);
        DataCompareResultService service = new DataCompareResultService(
            jobInstanceRepository,
            taskRepository,
            new LocalFileStorageStrategy(tempDir.toString())
        );

        JobInstance instance = new JobInstance();
        instance.setId("inst2");
        instance.setStatus(TaskConstants.TASK_STATUS_FAILED);
        instance.setJobMessage("compare failed");
        instance.setJobContext(
            "{\"units\":[],\"dataCompare\":{\"taskId\":\"task1\",\"summaryPath\":\"not-exists.csv\",\"resultFormat\":\"CSV\"}}"
        );
        when(jobInstanceRepository.findById("inst2")).thenReturn(Optional.of(instance));

        DataCompareResultDTO dto = service.findByJobInstanceId("inst2").orElseThrow();

        assertThat(dto.getStatus()).isEqualTo(TaskConstants.TASK_STATUS_FAILED);
        assertThat(dto.getJobMessage()).isEqualTo("compare failed");
        assertThat(dto.getLeftRowCount()).isNull();
    }

    @Test
    void shouldListResultsFromJobInstances(@TempDir Path tempDir) {
        JobInstanceRepository jobInstanceRepository = mock(JobInstanceRepository.class);
        DataCompareTaskRepository taskRepository = mock(DataCompareTaskRepository.class);
        DataCompareResultService service = new DataCompareResultService(
            jobInstanceRepository,
            taskRepository,
            new LocalFileStorageStrategy(tempDir.toString())
        );

        DataCompareTask task = new DataCompareTask();
        task.setId("task1");
        task.setJobId("job1");
        when(taskRepository.findById("task1")).thenReturn(Optional.of(task));

        JobInstance instance = new JobInstance();
        instance.setId("inst1");
        instance.setJobCode("job1");
        instance.setStatus(TaskConstants.TASK_STATUS_RUNNING);
        instance.setJobContext("{\"units\":[],\"dataCompare\":{\"taskId\":\"task1\"}}");
        PageRequest pageable = PageRequest.of(0, 20);
        when(jobInstanceRepository.findByJobCodeOrderByCreateTimeDesc(eq("job1"), any())).thenReturn(new PageImpl<>(List.of(instance), pageable, 1));

        Page<DataCompareResultDTO> page = service.findByTaskId("task1", pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getJobInstanceId()).isEqualTo("inst1");
        assertThat(page.getContent().get(0).getStatus()).isEqualTo(TaskConstants.TASK_STATUS_RUNNING);
    }
}
