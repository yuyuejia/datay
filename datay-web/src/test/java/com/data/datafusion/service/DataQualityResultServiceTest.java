package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.DataQualityTaskRepository;
import com.data.datafusion.repository.JobInstanceRepository;
import com.data.datafusion.service.dto.DataQualityResultDTO;
import com.data.file.FileStorageStrategy;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DataQualityResultServiceTest {

    private static final String SUMMARY_HEADER =
        "rule_key,rule_name,rule_level,column_name,rule_type,severity,checked_rows,invalid_rows," +
        "invalid_ratio,max_invalid_rows,max_invalid_ratio,status,message";

    @Test
    void summaryPathFollowsCurrentInstanceInsteadOfStaleJobContext() {
        JobInstanceRepository jobInstanceRepository = mock(JobInstanceRepository.class);
        FileStorageStrategy fileStorageStrategy = mock(FileStorageStrategy.class);
        JobInstance instance = new JobInstance();
        instance.setId("instance-new");
        instance.setInstanceCode("instance-new");
        instance.setJobCode("job-1");
        instance.setStatus(TaskConstants.TASK_STATUS_SUCCESSFUL);
        instance.setJobContext(
            "{\"dataQuality\":{\"taskId\":\"task-1\",\"outputDir\":\"data-quality/task-1\"," +
            "\"summaryPath\":\"data-quality/task-1/instance-old/quality_summary.csv\"," +
            "\"resultPath\":\"data-quality/task-1/instance-old/quality_summary.csv\"," +
            "\"detailDir\":\"data-quality/task-1/instance-old\",\"rules\":[]}}"
        );
        when(jobInstanceRepository.findById("instance-new")).thenReturn(Optional.of(instance));

        String expectedPath = "data-quality/task-1/instance-new/quality_summary.csv";
        String summary = SUMMARY_HEADER + "\n" +
            "DQ_NOT_NULL,订单号非空,FIELD,order_id,NOT_NULL,ERROR,120,0,0.0,0,0.0,PASS,通过\n";
        when(fileStorageStrategy.download(expectedPath)).thenReturn(
            new ByteArrayInputStream(summary.getBytes(StandardCharsets.UTF_8))
        );

        DataQualityResultService service = new DataQualityResultService(
            jobInstanceRepository,
            Mockito.mock(DataQualityTaskRepository.class),
            fileStorageStrategy
        );

        DataQualityResultDTO result = service.findByJobInstanceId("instance-new").orElseThrow();

        assertThat(result.getSummaryPath()).isEqualTo(expectedPath);
        assertThat(result.getCheckedRows()).isEqualTo(120L);
        assertThat(result.getPassRules()).isEqualTo(1);
        verify(fileStorageStrategy).download(expectedPath);
    }
}
