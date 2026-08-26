package com.data.datafusion.web.rest;

import static com.data.datafusion.domain.JobAsserts.*;
import static com.data.datafusion.web.rest.TestUtil.createUpdateProxyForBean;
import static com.data.datafusion.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.Job;
import com.data.datafusion.repository.JobRepository;
import com.data.datafusion.service.dto.JobDTO;
import com.data.datafusion.service.mapper.JobMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@link JobResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class JobResourceIT {

    private static final String DEFAULT_JOB_NAME = "AAAAAAAAAA";
    private static final String UPDATED_JOB_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_JOB_GROUP = "AAAAAAAAAA";
    private static final String UPDATED_JOB_GROUP = "BBBBBBBBBB";

    private static final String DEFAULT_TYPE = "AAAAAAAAAA";
    private static final String UPDATED_TYPE = "BBBBBBBBBB";

    private static final String DEFAULT_CRON = "AAAAAAAAAA";
    private static final String UPDATED_CRON = "BBBBBBBBBB";

    private static final String DEFAULT_JOB_CONTEXT = "AAAAAAAAAA";
    private static final String UPDATED_JOB_CONTEXT = "BBBBBBBBBB";

    private static final String DEFAULT_STATUS = "AAAAAAAAAA";
    private static final String UPDATED_STATUS = "BBBBBBBBBB";

    private static final ZonedDateTime DEFAULT_UPDATE_TIME = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_UPDATE_TIME = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final ZonedDateTime DEFAULT_CREATE_TIME = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_CREATE_TIME = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final String DEFAULT_PROJECT = "AAAAAAAAAA";
    private static final String UPDATED_PROJECT = "BBBBBBBBBB";

    private static final String DEFAULT_TENANT_ID = "AAAAAAAAAA";
    private static final String UPDATED_TENANT_ID = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/jobs";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobMapper jobMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restJobMockMvc;

    private Job job;

    private Job insertedJob;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Job createEntity() {
        return new Job()
            .jobName(DEFAULT_JOB_NAME)
            .jobGroup(DEFAULT_JOB_GROUP)
            .type(DEFAULT_TYPE)
            .cron(DEFAULT_CRON)
            .jobContext(DEFAULT_JOB_CONTEXT)
            .status(DEFAULT_STATUS)
            .updateTime(DEFAULT_UPDATE_TIME)
            .createTime(DEFAULT_CREATE_TIME)
            .project(DEFAULT_PROJECT)
            .tenantId(DEFAULT_TENANT_ID);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static Job createUpdatedEntity() {
        return new Job()
            .jobName(UPDATED_JOB_NAME)
            .jobGroup(UPDATED_JOB_GROUP)
            .type(UPDATED_TYPE)
            .cron(UPDATED_CRON)
            .jobContext(UPDATED_JOB_CONTEXT)
            .status(UPDATED_STATUS)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .project(UPDATED_PROJECT)
            .tenantId(UPDATED_TENANT_ID);
    }

    @BeforeEach
    void initTest() {
        job = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedJob != null) {
            jobRepository.delete(insertedJob);
            insertedJob = null;
        }
    }

    @Test
    @Transactional
    void createJob() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the Job
        JobDTO jobDTO = jobMapper.toDto(job);
        var returnedJobDTO = om.readValue(
            restJobMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(jobDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            JobDTO.class
        );

        // Validate the Job in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedJob = jobMapper.toEntity(returnedJobDTO);
        assertJobUpdatableFieldsEquals(returnedJob, getPersistedJob(returnedJob));

        insertedJob = returnedJob;
    }

    @Test
    @Transactional
    void createJobWithExistingId() throws Exception {
        // Create the Job with an existing ID
        job.setId(1L);
        JobDTO jobDTO = jobMapper.toDto(job);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restJobMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(jobDTO)))
            .andExpect(status().isBadRequest());

        // Validate the Job in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void getAllJobs() throws Exception {
        // Initialize the database
        insertedJob = jobRepository.saveAndFlush(job);

        // Get all the jobList
        restJobMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(job.getId().intValue())))
            .andExpect(jsonPath("$.[*].jobName").value(hasItem(DEFAULT_JOB_NAME)))
            .andExpect(jsonPath("$.[*].jobGroup").value(hasItem(DEFAULT_JOB_GROUP)))
            .andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE)))
            .andExpect(jsonPath("$.[*].cron").value(hasItem(DEFAULT_CRON)))
            .andExpect(jsonPath("$.[*].jobContext").value(hasItem(DEFAULT_JOB_CONTEXT)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS)))
            .andExpect(jsonPath("$.[*].updateTime").value(hasItem(sameInstant(DEFAULT_UPDATE_TIME))))
            .andExpect(jsonPath("$.[*].createTime").value(hasItem(sameInstant(DEFAULT_CREATE_TIME))))
            .andExpect(jsonPath("$.[*].project").value(hasItem(DEFAULT_PROJECT)))
            .andExpect(jsonPath("$.[*].tenantId").value(hasItem(DEFAULT_TENANT_ID)));
    }

    @Test
    @Transactional
    void getJob() throws Exception {
        // Initialize the database
        insertedJob = jobRepository.saveAndFlush(job);

        // Get the job
        restJobMockMvc
            .perform(get(ENTITY_API_URL_ID, job.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(job.getId().intValue()))
            .andExpect(jsonPath("$.jobName").value(DEFAULT_JOB_NAME))
            .andExpect(jsonPath("$.jobGroup").value(DEFAULT_JOB_GROUP))
            .andExpect(jsonPath("$.type").value(DEFAULT_TYPE))
            .andExpect(jsonPath("$.cron").value(DEFAULT_CRON))
            .andExpect(jsonPath("$.jobContext").value(DEFAULT_JOB_CONTEXT))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS))
            .andExpect(jsonPath("$.updateTime").value(sameInstant(DEFAULT_UPDATE_TIME)))
            .andExpect(jsonPath("$.createTime").value(sameInstant(DEFAULT_CREATE_TIME)))
            .andExpect(jsonPath("$.project").value(DEFAULT_PROJECT))
            .andExpect(jsonPath("$.tenantId").value(DEFAULT_TENANT_ID));
    }

    @Test
    @Transactional
    void getNonExistingJob() throws Exception {
        // Get the job
        restJobMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingJob() throws Exception {
        // Initialize the database
        insertedJob = jobRepository.saveAndFlush(job);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the job
        Job updatedJob = jobRepository.findById(job.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedJob are not directly saved in db
        em.detach(updatedJob);
        updatedJob
            .jobName(UPDATED_JOB_NAME)
            .jobGroup(UPDATED_JOB_GROUP)
            .type(UPDATED_TYPE)
            .cron(UPDATED_CRON)
            .jobContext(UPDATED_JOB_CONTEXT)
            .status(UPDATED_STATUS)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .project(UPDATED_PROJECT)
            .tenantId(UPDATED_TENANT_ID);
        JobDTO jobDTO = jobMapper.toDto(updatedJob);

        restJobMockMvc
            .perform(put(ENTITY_API_URL_ID, jobDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(jobDTO)))
            .andExpect(status().isOk());

        // Validate the Job in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedJobToMatchAllProperties(updatedJob);
    }

    @Test
    @Transactional
    void putNonExistingJob() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        job.setId(longCount.incrementAndGet());

        // Create the Job
        JobDTO jobDTO = jobMapper.toDto(job);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restJobMockMvc
            .perform(put(ENTITY_API_URL_ID, jobDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(jobDTO)))
            .andExpect(status().isBadRequest());

        // Validate the Job in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchJob() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        job.setId(longCount.incrementAndGet());

        // Create the Job
        JobDTO jobDTO = jobMapper.toDto(job);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restJobMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(jobDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Job in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamJob() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        job.setId(longCount.incrementAndGet());

        // Create the Job
        JobDTO jobDTO = jobMapper.toDto(job);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restJobMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(jobDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Job in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateJobWithPatch() throws Exception {
        // Initialize the database
        insertedJob = jobRepository.saveAndFlush(job);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the job using partial update
        Job partialUpdatedJob = new Job();
        partialUpdatedJob.setId(job.getId());

        partialUpdatedJob.cron(UPDATED_CRON).updateTime(UPDATED_UPDATE_TIME).project(UPDATED_PROJECT).tenantId(UPDATED_TENANT_ID);

        restJobMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedJob.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedJob))
            )
            .andExpect(status().isOk());

        // Validate the Job in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertJobUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedJob, job), getPersistedJob(job));
    }

    @Test
    @Transactional
    void fullUpdateJobWithPatch() throws Exception {
        // Initialize the database
        insertedJob = jobRepository.saveAndFlush(job);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the job using partial update
        Job partialUpdatedJob = new Job();
        partialUpdatedJob.setId(job.getId());

        partialUpdatedJob
            .jobName(UPDATED_JOB_NAME)
            .jobGroup(UPDATED_JOB_GROUP)
            .type(UPDATED_TYPE)
            .cron(UPDATED_CRON)
            .jobContext(UPDATED_JOB_CONTEXT)
            .status(UPDATED_STATUS)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .project(UPDATED_PROJECT)
            .tenantId(UPDATED_TENANT_ID);

        restJobMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedJob.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedJob))
            )
            .andExpect(status().isOk());

        // Validate the Job in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertJobUpdatableFieldsEquals(partialUpdatedJob, getPersistedJob(partialUpdatedJob));
    }

    @Test
    @Transactional
    void patchNonExistingJob() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        job.setId(longCount.incrementAndGet());

        // Create the Job
        JobDTO jobDTO = jobMapper.toDto(job);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restJobMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, jobDTO.getId()).contentType("application/merge-patch+json").content(om.writeValueAsBytes(jobDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Job in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchJob() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        job.setId(longCount.incrementAndGet());

        // Create the Job
        JobDTO jobDTO = jobMapper.toDto(job);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restJobMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(jobDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the Job in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamJob() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        job.setId(longCount.incrementAndGet());

        // Create the Job
        JobDTO jobDTO = jobMapper.toDto(job);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restJobMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(jobDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the Job in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteJob() throws Exception {
        // Initialize the database
        insertedJob = jobRepository.saveAndFlush(job);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the job
        restJobMockMvc.perform(delete(ENTITY_API_URL_ID, job.getId()).accept(MediaType.APPLICATION_JSON)).andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return jobRepository.count();
    }

    protected void assertIncrementedRepositoryCount(long countBefore) {
        assertThat(countBefore + 1).isEqualTo(getRepositoryCount());
    }

    protected void assertDecrementedRepositoryCount(long countBefore) {
        assertThat(countBefore - 1).isEqualTo(getRepositoryCount());
    }

    protected void assertSameRepositoryCount(long countBefore) {
        assertThat(countBefore).isEqualTo(getRepositoryCount());
    }

    protected Job getPersistedJob(Job job) {
        return jobRepository.findById(job.getId()).orElseThrow();
    }

    protected void assertPersistedJobToMatchAllProperties(Job expectedJob) {
        assertJobAllPropertiesEquals(expectedJob, getPersistedJob(expectedJob));
    }

    protected void assertPersistedJobToMatchUpdatableProperties(Job expectedJob) {
        assertJobAllUpdatablePropertiesEquals(expectedJob, getPersistedJob(expectedJob));
    }
}
