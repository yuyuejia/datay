package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alibaba.fastjson2.JSONObject;
import com.data.datafusion.domain.Job;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.JobInstanceRepository;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.service.mapper.JobInstanceMapper;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JobInstanceServiceDataQualityContextTest {

    @Test
    void buildJobInstanceRebindsDataQualityPathsToNewInstance() {
        JobRepository jobRepository = mock(JobRepository.class);
        Job job = new Job();
        job.setId("job-1");
        job.setJobName("dq-job");
        job.setType(TaskConstants.TASK_TYPE_DATA_QUALITY);
        job.setJobContext(
            "{\"units\":[],\"dataQuality\":{\"outputDir\":\"data-quality/task-1\"," +
            "\"summaryPath\":\"data-quality/task-1/manual-instance/quality_summary.csv\"," +
            "\"detailDir\":\"data-quality/task-1/manual-instance\"}}"
        );
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

        JobInstanceService service = new JobInstanceService(
            mock(JobInstanceRepository.class),
            jobRepository,
            mock(JobInstanceMapper.class)
        );

        JobInstance instance = service.buildJobInstance("job-1");
        String instanceCode = instance.getInstanceCode();
        JSONObject metadata = JSONObject.parseObject(instance.getJobContext()).getJSONObject("dataQuality");

        assertThat(instanceCode).isNotBlank();
        assertThat(metadata.getString("summaryPath")).isEqualTo("data-quality/task-1/" + instanceCode + "/quality_summary.csv");
        assertThat(metadata.getString("detailDir")).isEqualTo("data-quality/task-1/" + instanceCode);
    }
}
