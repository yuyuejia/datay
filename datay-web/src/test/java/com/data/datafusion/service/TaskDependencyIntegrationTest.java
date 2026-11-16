package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.ETLTask;
import com.data.datafusion.domain.Job;
import com.data.datafusion.domain.JobDepend;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.repository.JobDependRepository;
import com.data.datafusion.repository.JobInstanceRepository;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.dto.JobDependDTO;
import com.data.datafusion.service.scheduler.WaitingJobQuartzTask;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 任务依赖能力的集成测试：依赖持久化、替换/清空、循环校验，以及调度触发前的依赖判定。
 */
@IntegrationTest
@AutoConfigureMockMvc(addFilters = false)
@Transactional
class TaskDependencyIntegrationTest {

    private static final Long TENANT_ID = 1L;

    @Autowired
    private JobDependService jobDependService;

    @Autowired
    private JobDependRepository jobDependRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobInstanceRepository jobInstanceRepository;

    @Autowired
    private JobInstanceService jobInstanceService;

    @Autowired
    private ETLTaskRepository etlTaskRepository;

    @Autowired
    private JobService jobService;

    @Autowired
    private MockMvc mvc;

    private Job parentJob;

    private Job childJob;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(TENANT_ID);
        parentJob = saveJob("上游任务");
        childJob = saveJob("下游任务");
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void replacesDependenciesInsteadOfAppendingAndCanClearThem() {
        jobDependService.replaceChildDependencies(childJob.getId(), List.of(dependency(parentJob.getId(), 60_000L)));

        List<JobDepend> dependencies = jobDependRepository.findByChildJobCode(childJob.getId());
        assertThat(dependencies).hasSize(1);
        assertThat(dependencies.get(0).getParentJobCode()).isEqualTo(parentJob.getId());
        assertThat(dependencies.get(0).getChildJobCode()).isEqualTo(childJob.getId());
        assertThat(dependencies.get(0).getJobCode()).isEqualTo(childJob.getId());
        assertThat(dependencies.get(0).getLastInterval()).isEqualTo(60_000L);
        assertThat(dependencies.get(0).getTenantId()).isEqualTo(String.valueOf(TENANT_ID));

        jobDependService.replaceChildDependencies(childJob.getId(), List.of(dependency(parentJob.getId(), 120_000L)));

        dependencies = jobDependRepository.findByChildJobCode(childJob.getId());
        assertThat(dependencies).hasSize(1);
        assertThat(dependencies.get(0).getLastInterval()).isEqualTo(120_000L);

        jobDependService.replaceChildDependencies(childJob.getId(), List.of());

        assertThat(jobDependRepository.findByChildJobCode(childJob.getId())).isEmpty();
    }

    @Test
    void returnsDependenciesForTheOnlineDialog() {
        jobDependService.replaceChildDependencies(childJob.getId(), List.of(dependency(parentJob.getId(), 90_000L)));

        assertThat(jobDependService.findDtosByChildJobCode(childJob.getId()))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.getParentJobCode()).isEqualTo(parentJob.getId());
                assertThat(item.getLastInterval()).isEqualTo(90_000L);
            });
    }

    @Test
    void onlineApiSavesAndClearsDependencies() throws Exception {
        mvc
            .perform(
                post("/api/jobs/{id}/online", childJob.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"dependencies\":[{\"parentJobCode\":\"" + parentJob.getId() + "\",\"lastInterval\":60000}]}"
                    )
            )
            .andExpect(status().isOk());

        assertThat(jobDependRepository.findByChildJobCode(childJob.getId()))
            .singleElement()
            .satisfies(item -> assertThat(item.getParentJobCode()).isEqualTo(parentJob.getId()));

        mvc
            .perform(
                post("/api/jobs/{id}/online", childJob.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"dependencies\":[]}")
            )
            .andExpect(status().isOk());

        assertThat(jobDependRepository.findByChildJobCode(childJob.getId())).isEmpty();
    }

    @Test
    void etlOnlineApiSavesDependenciesAgainstTheGeneratedJob() throws Exception {
        Job etlJob = saveJob("ETL 任务");
        ETLTask etlTask = new ETLTask();
        etlTask.setTaskName("ETL 任务");
        etlTask.setJobId(etlJob.getId());
        etlTask.setTenantId(String.valueOf(TENANT_ID));
        etlTask.setCreateTime(ZonedDateTime.now());
        etlTask.setUpdateTime(ZonedDateTime.now());
        ETLTask savedTask = etlTaskRepository.saveAndFlush(etlTask);

        mvc
            .perform(
                post("/api/etl-tasks/{id}/online", savedTask.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"dependencies\":[{\"parentJobCode\":\"" + parentJob.getId() + "\",\"lastInterval\":60000}]}"
                    )
            )
            .andExpect(status().isOk());

        assertThat(jobDependRepository.findByChildJobCode(etlJob.getId()))
            .singleElement()
            .satisfies(item -> assertThat(item.getParentJobCode()).isEqualTo(parentJob.getId()));
    }

    @Test
    void deletingAJobRemovesItsIncomingAndOutgoingDependencies() {
        Job downstream = saveJob("下游任务2");
        jobDependService.replaceChildDependencies(childJob.getId(), List.of(dependency(parentJob.getId(), 60_000L)));
        jobDependService.replaceChildDependencies(downstream.getId(), List.of(dependency(parentJob.getId(), 60_000L)));

        jobService.delete(parentJob.getId());

        assertThat(jobDependRepository.findByChildJobCode(childJob.getId())).isEmpty();
        assertThat(jobDependRepository.findByChildJobCode(downstream.getId())).isEmpty();
    }

    @Test
    void rejectsSelfDependency() {
        assertThatThrownBy(() ->
            jobDependService.replaceChildDependencies(childJob.getId(), List.of(dependency(childJob.getId(), 60_000L)))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("不能依赖自身");
    }

    @Test
    void rejectsDuplicateParents() {
        assertThatThrownBy(() ->
            jobDependService.replaceChildDependencies(
                childJob.getId(),
                List.of(dependency(parentJob.getId(), 60_000L), dependency(parentJob.getId(), 60_000L))
            )
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("上游任务重复");
    }

    @Test
    void rejectsMissingParent() {
        assertThatThrownBy(() -> jobDependService.replaceChildDependencies(childJob.getId(), List.of(dependency("missing-job", 60_000L))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("上游任务不存在");
    }

    @Test
    void rejectsCircularDependency() {
        jobDependService.replaceChildDependencies(parentJob.getId(), List.of(dependency(childJob.getId(), 60_000L)));

        assertThatThrownBy(() ->
            jobDependService.replaceChildDependencies(childJob.getId(), List.of(dependency(parentJob.getId(), 60_000L)))
        )
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("循环");
    }

    @Test
    void taskWithoutDependenciesCanStartImmediately() {
        assertThat(jobInstanceService.checkDependIsNullOrSuccess(childInstance())).isTrue();
    }

    @Test
    void taskWaitsWhenParentHasNoSuccessfulInstance() {
        jobDependService.replaceChildDependencies(childJob.getId(), List.of(dependency(parentJob.getId(), 60_000L)));

        assertThat(jobInstanceService.checkDependIsNullOrSuccess(childInstance())).isFalse();
    }

    @Test
    void taskStartsWhenParentSucceededWithinWindow() {
        jobDependService.replaceChildDependencies(childJob.getId(), List.of(dependency(parentJob.getId(), 60_000L)));
        saveSuccessfulInstance(parentJob.getId(), ZonedDateTime.now().minusSeconds(10));

        assertThat(jobInstanceService.checkDependIsNullOrSuccess(childInstance())).isTrue();
    }

    @Test
    void taskWaitsWhenParentSucceededOutsideWindow() {
        jobDependService.replaceChildDependencies(childJob.getId(), List.of(dependency(parentJob.getId(), 60_000L)));
        saveSuccessfulInstance(parentJob.getId(), ZonedDateTime.now().minusSeconds(120));

        assertThat(jobInstanceService.checkDependIsNullOrSuccess(childInstance())).isFalse();
    }

    @Test
    void usesLatestSuccessfulParentInstanceWithinWindow() {
        jobDependService.replaceChildDependencies(childJob.getId(), List.of(dependency(parentJob.getId(), 60_000L)));
        saveSuccessfulInstance(parentJob.getId(), ZonedDateTime.now().minusSeconds(120));
        saveSuccessfulInstance(parentJob.getId(), ZonedDateTime.now().minusSeconds(10));

        assertThat(jobInstanceService.checkDependIsNullOrSuccess(childInstance())).isTrue();
    }

    @Test
    void failedParentInstancesDoNotSatisfyDependency() {
        jobDependService.replaceChildDependencies(childJob.getId(), List.of(dependency(parentJob.getId(), 60_000L)));
        saveInstance(parentJob.getId(), TaskConstants.TASK_STATUS_FAILED, ZonedDateTime.now().minusSeconds(10));

        assertThat(jobInstanceService.checkDependIsNullOrSuccess(childInstance())).isFalse();
    }

    @Test
    void allUpstreamTasksMustBeSatisfied() {
        Job secondParent = saveJob("上游任务2");
        jobDependService.replaceChildDependencies(
            childJob.getId(),
            List.of(dependency(parentJob.getId(), 60_000L), dependency(secondParent.getId(), 60_000L))
        );
        saveSuccessfulInstance(parentJob.getId(), ZonedDateTime.now().minusSeconds(10));

        assertThat(jobInstanceService.checkDependIsNullOrSuccess(childInstance())).isFalse();

        saveSuccessfulInstance(secondParent.getId(), ZonedDateTime.now().minusSeconds(5));

        assertThat(jobInstanceService.checkDependIsNullOrSuccess(childInstance())).isTrue();
    }

    @Test
    void waitingTaskResumesWhenParentBecomesSuccessful() throws Exception {
        jobDependService.replaceChildDependencies(childJob.getId(), List.of(dependency(parentJob.getId(), 60_000L)));
        JobInstance waiting = childInstance("waiting-resume");
        jobInstanceService.waitingInstance(waiting);
        assertThat(jobInstanceRepository.findByInstanceCode(waiting.getInstanceCode()).orElseThrow().getStatus())
            .isEqualTo(TaskConstants.TASK_STATUS_WAITING);

        saveSuccessfulInstance(parentJob.getId(), ZonedDateTime.now().minusSeconds(5));
        new WaitingJobQuartzTask().execute(null);
        // WaitingJobQuartzTask 会按实例租户设置并清理 ThreadLocal，这里恢复测试线程的租户上下文。
        TenantContext.setTenantId(TENANT_ID);

        assertThat(jobInstanceRepository.findByInstanceCode(waiting.getInstanceCode()).orElseThrow().getStatus())
            .isEqualTo(TaskConstants.TASK_STATUS_APPENDING);
    }

    @Test
    void newWaitingInstanceTimesOutPreviousWaitingInstance() {
        JobInstance firstWaiting = childInstance("waiting-1");
        jobInstanceService.waitingInstance(firstWaiting);
        assertThat(jobInstanceRepository.findByInstanceCode(firstWaiting.getInstanceCode()).orElseThrow().getStatus())
            .isEqualTo(TaskConstants.TASK_STATUS_WAITING);

        JobInstance secondWaiting = childInstance("waiting-2");
        jobInstanceService.waitingInstance(secondWaiting);

        assertThat(jobInstanceRepository.findByInstanceCode(secondWaiting.getInstanceCode()).orElseThrow().getStatus())
            .isEqualTo(TaskConstants.TASK_STATUS_WAITING);
        assertThat(jobInstanceRepository.findByInstanceCode(firstWaiting.getInstanceCode()).orElseThrow().getStatus())
            .isEqualTo(TaskConstants.TASK_STATUS_TIMEOUT);
    }

    private Job saveJob(String name) {
        Job job = new Job();
        job.setJobName(name);
        job.setType(TaskConstants.TASK_TYPE_SHELL);
        job.setStatus(TaskConstants.TASK_STATUS_OFFLINE);
        job.setTenantId(String.valueOf(TENANT_ID));
        return jobRepository.saveAndFlush(job);
    }

    private JobInstance childInstance() {
        return childInstance("child-instance-" + UUID.randomUUID());
    }

    private JobInstance childInstance(String instanceCode) {
        JobInstance instance = new JobInstance();
        instance.setInstanceCode(instanceCode);
        instance.setJobName(childJob.getJobName());
        instance.setJobCode(childJob.getId());
        instance.setType(TaskConstants.TASK_TYPE_SHELL);
        instance.setStatus(TaskConstants.TASK_STATUS_APPENDING);
        instance.setCreateTime(ZonedDateTime.now());
        instance.setTenantId(String.valueOf(TENANT_ID));
        return instance;
    }

    private void saveSuccessfulInstance(String parentJobCode, ZonedDateTime createTime) {
        saveInstance(parentJobCode, TaskConstants.TASK_STATUS_SUCCESSFUL, createTime);
    }

    private void saveInstance(String parentJobCode, String status, ZonedDateTime createTime) {
        JobInstance instance = new JobInstance();
        instance.setInstanceCode(parentJobCode + "-" + UUID.randomUUID());
        instance.setJobName(parentJobCode);
        instance.setJobCode(parentJobCode);
        instance.setType(TaskConstants.TASK_TYPE_SHELL);
        instance.setStatus(status);
        instance.setCreateTime(createTime);
        instance.setStartTime(String.valueOf(createTime.toInstant().toEpochMilli()));
        instance.setEndTime(String.valueOf(createTime.toInstant().toEpochMilli()));
        instance.setTenantId(String.valueOf(TENANT_ID));
        jobInstanceRepository.saveAndFlush(instance);
    }

    private static JobDependDTO dependency(String parentJobCode, Long lastInterval) {
        JobDependDTO dto = new JobDependDTO();
        dto.setParentJobCode(parentJobCode);
        dto.setLastInterval(lastInterval);
        return dto;
    }
}
