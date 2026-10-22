package com.data.datafusion.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.data.datafusion.domain.Job;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.service.JobService;
import com.data.datafusion.service.dto.JobDTO;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = JobResource.class, properties = "jhipster.clientApp.name=datafusionApp")
@AutoConfigureMockMvc(addFilters = false)
class JobOnlinePriorityResourceTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JobService jobService;

    @MockitoBean
    private JobRepository jobRepository;

    @Test
    void onlineAppliesRequestedPriority() throws Exception {
        Job job = new Job();
        job.setId("job-1");
        job.setPriority(2);
        when(jobService.findOneJob("job-1")).thenReturn(Optional.of(job));
        JobDTO updated = new JobDTO();
        updated.setId("job-1");
        when(jobService.findOne("job-1")).thenReturn(Optional.of(updated));

        mvc.perform(post("/api/jobs/job-1/online").param("priority", "3")).andExpect(status().isOk());

        assertThat(job.getPriority()).isEqualTo(3);
        verify(jobService).online(job, false);
    }

    @Test
    void onlineKeepsExistingPriorityWhenRequestOmitsIt() throws Exception {
        Job job = new Job();
        job.setId("job-1");
        job.setPriority(1);
        when(jobService.findOneJob("job-1")).thenReturn(Optional.of(job));
        JobDTO updated = new JobDTO();
        updated.setId("job-1");
        when(jobService.findOne("job-1")).thenReturn(Optional.of(updated));

        mvc.perform(post("/api/jobs/job-1/online")).andExpect(status().isOk());

        assertThat(job.getPriority()).isEqualTo(1);
    }

    @Test
    void onlineRejectsPriorityOutsideSupportedRange() throws Exception {
        Job job = new Job();
        job.setId("job-1");
        when(jobService.findOneJob("job-1")).thenReturn(Optional.of(job));

        mvc.perform(post("/api/jobs/job-1/online").param("priority", "4")).andExpect(status().isBadRequest());

        verify(jobService, never()).online(any(Job.class), anyBoolean());
    }
}
