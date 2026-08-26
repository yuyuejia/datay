package com.data.datafusion.web.rest;

import static com.data.datafusion.domain.JobInstanceAsserts.*;
import static com.data.datafusion.web.rest.TestUtil.createUpdateProxyForBean;
import static com.data.datafusion.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.repository.JobInstanceRepository;
import com.data.datafusion.service.dto.JobInstanceDTO;
import com.data.datafusion.service.mapper.JobInstanceMapper;
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
 * Integration tests for the {@link JobInstanceResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class JobInstanceResourceIT {

    private static final String DEFAULT_INSTANCE_CODE = "AAAAAAAAAA";
    private static final String UPDATED_INSTANCE_CODE = "BBBBBBBBBB";

    private static final String DEFAULT_JOB_NAME = "AAAAAAAAAA";
    private static final String UPDATED_JOB_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_JOB_CODE = "AAAAAAAAAA";
    private static final String UPDATED_JOB_CODE = "BBBBBBBBBB";

    private static final String DEFAULT_TYPE = "AAAAAAAAAA";
    private static final String UPDATED_TYPE = "BBBBBBBBBB";

    private static final String DEFAULT_JOB_CONTEXT = "AAAAAAAAAA";
    private static final String UPDATED_JOB_CONTEXT = "BBBBBBBBBB";

    private static final String DEFAULT_STATUS = "AAAAAAAAAA";
    private static final String UPDATED_STATUS = "BBBBBBBBBB";

    private static final String DEFAULT_JOB_MESSAGE = "AAAAAAAAAA";
    private static final String UPDATED_JOB_MESSAGE = "BBBBBBBBBB";

    private static final String DEFAULT_EXEC_NODE = "AAAAAAAAAA";
    private static final String UPDATED_EXEC_NODE = "BBBBBBBBBB";

    private static final String DEFAULT_START_TIME = "AAAAAAAAAA";
    private static final String UPDATED_START_TIME = "BBBBBBBBBB";

    private static final String DEFAULT_END_TIME = "AAAAAAAAAA";
    private static final String UPDATED_END_TIME = "BBBBBBBBBB";

    private static final ZonedDateTime DEFAULT_CREATE_TIME = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_CREATE_TIME = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final String DEFAULT_PROJECT = "AAAAAAAAAA";
    private static final String UPDATED_PROJECT = "BBBBBBBBBB";

    private static final String DEFAULT_TENANT_ID = "AAAAAAAAAA";
    private static final String UPDATED_TENANT_ID = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/job-instances";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private JobInstanceRepository jobInstanceRepository;

    @Autowired
    private JobInstanceMapper jobInstanceMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restJobInstanceMockMvc;

    private JobInstance jobInstance;

    private JobInstance insertedJobInstance;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static JobInstance createEntity() {
        return new JobInstance()
            .instanceCode(DEFAULT_INSTANCE_CODE)
            .jobName(DEFAULT_JOB_NAME)
            .jobCode(DEFAULT_JOB_CODE)
            .type(DEFAULT_TYPE)
            .jobContext(DEFAULT_JOB_CONTEXT)
            .status(DEFAULT_STATUS)
            .jobMessage(DEFAULT_JOB_MESSAGE)
            .execNode(DEFAULT_EXEC_NODE)
            .startTime(DEFAULT_START_TIME)
            .endTime(DEFAULT_END_TIME)
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
    public static JobInstance createUpdatedEntity() {
        return new JobInstance()
            .instanceCode(UPDATED_INSTANCE_CODE)
            .jobName(UPDATED_JOB_NAME)
            .jobCode(UPDATED_JOB_CODE)
            .type(UPDATED_TYPE)
            .jobContext(UPDATED_JOB_CONTEXT)
            .status(UPDATED_STATUS)
            .jobMessage(UPDATED_JOB_MESSAGE)
            .execNode(UPDATED_EXEC_NODE)
            .startTime(UPDATED_START_TIME)
            .endTime(UPDATED_END_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .project(UPDATED_PROJECT)
            .tenantId(UPDATED_TENANT_ID);
    }

    @BeforeEach
    void initTest() {
        jobInstance = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedJobInstance != null) {
            jobInstanceRepository.delete(insertedJobInstance);
            insertedJobInstance = null;
        }
    }

    @Test
    @Transactional
    void createJobInstance() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the JobInstance
        JobInstanceDTO jobInstanceDTO = jobInstanceMapper.toDto(jobInstance);
        var returnedJobInstanceDTO = om.readValue(
            restJobInstanceMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(jobInstanceDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            JobInstanceDTO.class
        );

        // Validate the JobInstance in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedJobInstance = jobInstanceMapper.toEntity(returnedJobInstanceDTO);
        assertJobInstanceUpdatableFieldsEquals(returnedJobInstance, getPersistedJobInstance(returnedJobInstance));

        insertedJobInstance = returnedJobInstance;
    }

    @Test
    @Transactional
    void createJobInstanceWithExistingId() throws Exception {
        // Create the JobInstance with an existing ID
        jobInstance.setId(1L);
        JobInstanceDTO jobInstanceDTO = jobInstanceMapper.toDto(jobInstance);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restJobInstanceMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(jobInstanceDTO)))
            .andExpect(status().isBadRequest());

        // Validate the JobInstance in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void getAllJobInstances() throws Exception {
        // Initialize the database
        insertedJobInstance = jobInstanceRepository.saveAndFlush(jobInstance);

        // Get all the jobInstanceList
        restJobInstanceMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(jobInstance.getId().intValue())))
            .andExpect(jsonPath("$.[*].instanceCode").value(hasItem(DEFAULT_INSTANCE_CODE)))
            .andExpect(jsonPath("$.[*].jobName").value(hasItem(DEFAULT_JOB_NAME)))
            .andExpect(jsonPath("$.[*].jobCode").value(hasItem(DEFAULT_JOB_CODE)))
            .andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE)))
            .andExpect(jsonPath("$.[*].jobContext").value(hasItem(DEFAULT_JOB_CONTEXT)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS)))
            .andExpect(jsonPath("$.[*].jobMessage").value(hasItem(DEFAULT_JOB_MESSAGE)))
            .andExpect(jsonPath("$.[*].execNode").value(hasItem(DEFAULT_EXEC_NODE)))
            .andExpect(jsonPath("$.[*].startTime").value(hasItem(DEFAULT_START_TIME)))
            .andExpect(jsonPath("$.[*].endTime").value(hasItem(DEFAULT_END_TIME)))
            .andExpect(jsonPath("$.[*].createTime").value(hasItem(sameInstant(DEFAULT_CREATE_TIME))))
            .andExpect(jsonPath("$.[*].project").value(hasItem(DEFAULT_PROJECT)))
            .andExpect(jsonPath("$.[*].tenantId").value(hasItem(DEFAULT_TENANT_ID)));
    }

    @Test
    @Transactional
    void getJobInstance() throws Exception {
        // Initialize the database
        insertedJobInstance = jobInstanceRepository.saveAndFlush(jobInstance);

        // Get the jobInstance
        restJobInstanceMockMvc
            .perform(get(ENTITY_API_URL_ID, jobInstance.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(jobInstance.getId().intValue()))
            .andExpect(jsonPath("$.instanceCode").value(DEFAULT_INSTANCE_CODE))
            .andExpect(jsonPath("$.jobName").value(DEFAULT_JOB_NAME))
            .andExpect(jsonPath("$.jobCode").value(DEFAULT_JOB_CODE))
            .andExpect(jsonPath("$.type").value(DEFAULT_TYPE))
            .andExpect(jsonPath("$.jobContext").value(DEFAULT_JOB_CONTEXT))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS))
            .andExpect(jsonPath("$.jobMessage").value(DEFAULT_JOB_MESSAGE))
            .andExpect(jsonPath("$.execNode").value(DEFAULT_EXEC_NODE))
            .andExpect(jsonPath("$.startTime").value(DEFAULT_START_TIME))
            .andExpect(jsonPath("$.endTime").value(DEFAULT_END_TIME))
            .andExpect(jsonPath("$.createTime").value(sameInstant(DEFAULT_CREATE_TIME)))
            .andExpect(jsonPath("$.project").value(DEFAULT_PROJECT))
            .andExpect(jsonPath("$.tenantId").value(DEFAULT_TENANT_ID));
    }

    @Test
    @Transactional
    void getNonExistingJobInstance() throws Exception {
        // Get the jobInstance
        restJobInstanceMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingJobInstance() throws Exception {
        // Initialize the database
        insertedJobInstance = jobInstanceRepository.saveAndFlush(jobInstance);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the jobInstance
        JobInstance updatedJobInstance = jobInstanceRepository.findById(jobInstance.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedJobInstance are not directly saved in db
        em.detach(updatedJobInstance);
        updatedJobInstance
            .instanceCode(UPDATED_INSTANCE_CODE)
            .jobName(UPDATED_JOB_NAME)
            .jobCode(UPDATED_JOB_CODE)
            .type(UPDATED_TYPE)
            .jobContext(UPDATED_JOB_CONTEXT)
            .status(UPDATED_STATUS)
            .jobMessage(UPDATED_JOB_MESSAGE)
            .execNode(UPDATED_EXEC_NODE)
            .startTime(UPDATED_START_TIME)
            .endTime(UPDATED_END_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .project(UPDATED_PROJECT)
            .tenantId(UPDATED_TENANT_ID);
        JobInstanceDTO jobInstanceDTO = jobInstanceMapper.toDto(updatedJobInstance);

        restJobInstanceMockMvc
            .perform(
                put(ENTITY_API_URL_ID, jobInstanceDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(jobInstanceDTO))
            )
            .andExpect(status().isOk());

        // Validate the JobInstance in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedJobInstanceToMatchAllProperties(updatedJobInstance);
    }

    @Test
    @Transactional
    void putNonExistingJobInstance() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        jobInstance.setId(longCount.incrementAndGet());

        // Create the JobInstance
        JobInstanceDTO jobInstanceDTO = jobInstanceMapper.toDto(jobInstance);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restJobInstanceMockMvc
            .perform(
                put(ENTITY_API_URL_ID, jobInstanceDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(jobInstanceDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the JobInstance in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchJobInstance() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        jobInstance.setId(longCount.incrementAndGet());

        // Create the JobInstance
        JobInstanceDTO jobInstanceDTO = jobInstanceMapper.toDto(jobInstance);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restJobInstanceMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(jobInstanceDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the JobInstance in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamJobInstance() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        jobInstance.setId(longCount.incrementAndGet());

        // Create the JobInstance
        JobInstanceDTO jobInstanceDTO = jobInstanceMapper.toDto(jobInstance);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restJobInstanceMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(jobInstanceDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the JobInstance in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateJobInstanceWithPatch() throws Exception {
        // Initialize the database
        insertedJobInstance = jobInstanceRepository.saveAndFlush(jobInstance);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the jobInstance using partial update
        JobInstance partialUpdatedJobInstance = new JobInstance();
        partialUpdatedJobInstance.setId(jobInstance.getId());

        partialUpdatedJobInstance
            .jobName(UPDATED_JOB_NAME)
            .type(UPDATED_TYPE)
            .jobContext(UPDATED_JOB_CONTEXT)
            .startTime(UPDATED_START_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .project(UPDATED_PROJECT);

        restJobInstanceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedJobInstance.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedJobInstance))
            )
            .andExpect(status().isOk());

        // Validate the JobInstance in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertJobInstanceUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedJobInstance, jobInstance),
            getPersistedJobInstance(jobInstance)
        );
    }

    @Test
    @Transactional
    void fullUpdateJobInstanceWithPatch() throws Exception {
        // Initialize the database
        insertedJobInstance = jobInstanceRepository.saveAndFlush(jobInstance);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the jobInstance using partial update
        JobInstance partialUpdatedJobInstance = new JobInstance();
        partialUpdatedJobInstance.setId(jobInstance.getId());

        partialUpdatedJobInstance
            .instanceCode(UPDATED_INSTANCE_CODE)
            .jobName(UPDATED_JOB_NAME)
            .jobCode(UPDATED_JOB_CODE)
            .type(UPDATED_TYPE)
            .jobContext(UPDATED_JOB_CONTEXT)
            .status(UPDATED_STATUS)
            .jobMessage(UPDATED_JOB_MESSAGE)
            .execNode(UPDATED_EXEC_NODE)
            .startTime(UPDATED_START_TIME)
            .endTime(UPDATED_END_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .project(UPDATED_PROJECT)
            .tenantId(UPDATED_TENANT_ID);

        restJobInstanceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedJobInstance.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedJobInstance))
            )
            .andExpect(status().isOk());

        // Validate the JobInstance in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertJobInstanceUpdatableFieldsEquals(partialUpdatedJobInstance, getPersistedJobInstance(partialUpdatedJobInstance));
    }

    @Test
    @Transactional
    void patchNonExistingJobInstance() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        jobInstance.setId(longCount.incrementAndGet());

        // Create the JobInstance
        JobInstanceDTO jobInstanceDTO = jobInstanceMapper.toDto(jobInstance);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restJobInstanceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, jobInstanceDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(jobInstanceDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the JobInstance in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchJobInstance() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        jobInstance.setId(longCount.incrementAndGet());

        // Create the JobInstance
        JobInstanceDTO jobInstanceDTO = jobInstanceMapper.toDto(jobInstance);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restJobInstanceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(jobInstanceDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the JobInstance in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamJobInstance() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        jobInstance.setId(longCount.incrementAndGet());

        // Create the JobInstance
        JobInstanceDTO jobInstanceDTO = jobInstanceMapper.toDto(jobInstance);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restJobInstanceMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(jobInstanceDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the JobInstance in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteJobInstance() throws Exception {
        // Initialize the database
        insertedJobInstance = jobInstanceRepository.saveAndFlush(jobInstance);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the jobInstance
        restJobInstanceMockMvc
            .perform(delete(ENTITY_API_URL_ID, jobInstance.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return jobInstanceRepository.count();
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

    protected JobInstance getPersistedJobInstance(JobInstance jobInstance) {
        return jobInstanceRepository.findById(jobInstance.getId()).orElseThrow();
    }

    protected void assertPersistedJobInstanceToMatchAllProperties(JobInstance expectedJobInstance) {
        assertJobInstanceAllPropertiesEquals(expectedJobInstance, getPersistedJobInstance(expectedJobInstance));
    }

    protected void assertPersistedJobInstanceToMatchUpdatableProperties(JobInstance expectedJobInstance) {
        assertJobInstanceAllUpdatablePropertiesEquals(expectedJobInstance, getPersistedJobInstance(expectedJobInstance));
    }
}
