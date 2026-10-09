package com.data.datafusion.web.rest;

import static com.data.datafusion.domain.JobDependAsserts.*;
import static com.data.datafusion.web.rest.TestUtil.createUpdateProxyForBean;
import static com.data.datafusion.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.JobDepend;
import com.data.datafusion.repository.JobDependRepository;
import com.data.datafusion.service.dto.JobDependDTO;
import com.data.datafusion.service.mapper.JobDependMapper;
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
 * Integration tests for the {@link JobDependResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class JobDependResourceIT {

    private static final String DEFAULT_PARENT_JOB_CODE = "AAAAAAAAAA";
    private static final String UPDATED_PARENT_JOB_CODE = "BBBBBBBBBB";

    private static final String DEFAULT_CHILD_JOB_CODE = "AAAAAAAAAA";
    private static final String UPDATED_CHILD_JOB_CODE = "BBBBBBBBBB";

    private static final String DEFAULT_JOB_CODE = "AAAAAAAAAA";
    private static final String UPDATED_JOB_CODE = "BBBBBBBBBB";

    private static final Long DEFAULT_LAST_INTERVAL = 1L;
    private static final Long UPDATED_LAST_INTERVAL = 2L;

    private static final ZonedDateTime DEFAULT_CREATE_TIME = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_CREATE_TIME = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final String DEFAULT_TENANT_ID = "AAAAAAAAAA";
    private static final String UPDATED_TENANT_ID = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/job-depends";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private JobDependRepository jobDependRepository;

    @Autowired
    private JobDependMapper jobDependMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restJobDependMockMvc;

    private JobDepend jobDepend;

    private JobDepend insertedJobDepend;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static JobDepend createEntity() {
        return new JobDepend()
            .parentJobCode(DEFAULT_PARENT_JOB_CODE)
            .childJobCode(DEFAULT_CHILD_JOB_CODE)
            .jobCode(DEFAULT_JOB_CODE)
            .lastInterval(DEFAULT_LAST_INTERVAL)
            .createTime(DEFAULT_CREATE_TIME)
            .tenantId(DEFAULT_TENANT_ID);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static JobDepend createUpdatedEntity() {
        return new JobDepend()
            .parentJobCode(UPDATED_PARENT_JOB_CODE)
            .childJobCode(UPDATED_CHILD_JOB_CODE)
            .jobCode(UPDATED_JOB_CODE)
            .lastInterval(UPDATED_LAST_INTERVAL)
            .createTime(UPDATED_CREATE_TIME)
            .tenantId(UPDATED_TENANT_ID);
    }

    @BeforeEach
    void initTest() {
        jobDepend = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedJobDepend != null) {
            jobDependRepository.delete(insertedJobDepend);
            insertedJobDepend = null;
        }
    }

    @Test
    @Transactional
    void createJobDepend() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the JobDepend
        JobDependDTO jobDependDTO = jobDependMapper.toDto(jobDepend);
        var returnedJobDependDTO = om.readValue(
            restJobDependMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(jobDependDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            JobDependDTO.class
        );

        // Validate the JobDepend in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedJobDepend = jobDependMapper.toEntity(returnedJobDependDTO);
        assertJobDependUpdatableFieldsEquals(returnedJobDepend, getPersistedJobDepend(returnedJobDepend));

        insertedJobDepend = returnedJobDepend;
    }

    @Test
    @Transactional
    void createJobDependWithExistingId() throws Exception {
        // Create the JobDepend with an existing ID
        jobDepend.setId("1");
        JobDependDTO jobDependDTO = jobDependMapper.toDto(jobDepend);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restJobDependMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(jobDependDTO)))
            .andExpect(status().isBadRequest());

        // Validate the JobDepend in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void getAllJobDepends() throws Exception {
        // Initialize the database
        insertedJobDepend = jobDependRepository.saveAndFlush(jobDepend);

        // Get all the jobDependList
        restJobDependMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(jobDepend.getId())))
            .andExpect(jsonPath("$.[*].parentJobCode").value(hasItem(DEFAULT_PARENT_JOB_CODE)))
            .andExpect(jsonPath("$.[*].childJobCode").value(hasItem(DEFAULT_CHILD_JOB_CODE)))
            .andExpect(jsonPath("$.[*].jobCode").value(hasItem(DEFAULT_JOB_CODE)))
            .andExpect(jsonPath("$.[*].lastInterval").value(hasItem(DEFAULT_LAST_INTERVAL.intValue())))
            .andExpect(jsonPath("$.[*].createTime").value(hasItem(sameInstant(DEFAULT_CREATE_TIME))))
            .andExpect(jsonPath("$.[*].tenantId").value(hasItem(DEFAULT_TENANT_ID)));
    }

    @Test
    @Transactional
    void getJobDepend() throws Exception {
        // Initialize the database
        insertedJobDepend = jobDependRepository.saveAndFlush(jobDepend);

        // Get the jobDepend
        restJobDependMockMvc
            .perform(get(ENTITY_API_URL_ID, jobDepend.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(jobDepend.getId()))
            .andExpect(jsonPath("$.parentJobCode").value(DEFAULT_PARENT_JOB_CODE))
            .andExpect(jsonPath("$.childJobCode").value(DEFAULT_CHILD_JOB_CODE))
            .andExpect(jsonPath("$.jobCode").value(DEFAULT_JOB_CODE))
            .andExpect(jsonPath("$.lastInterval").value(DEFAULT_LAST_INTERVAL.intValue()))
            .andExpect(jsonPath("$.createTime").value(sameInstant(DEFAULT_CREATE_TIME)))
            .andExpect(jsonPath("$.tenantId").value(DEFAULT_TENANT_ID));
    }

    @Test
    @Transactional
    void getNonExistingJobDepend() throws Exception {
        // Get the jobDepend
        restJobDependMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingJobDepend() throws Exception {
        // Initialize the database
        insertedJobDepend = jobDependRepository.saveAndFlush(jobDepend);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the jobDepend
        JobDepend updatedJobDepend = jobDependRepository.findById(jobDepend.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedJobDepend are not directly saved in db
        em.detach(updatedJobDepend);
        updatedJobDepend
            .parentJobCode(UPDATED_PARENT_JOB_CODE)
            .childJobCode(UPDATED_CHILD_JOB_CODE)
            .jobCode(UPDATED_JOB_CODE)
            .lastInterval(UPDATED_LAST_INTERVAL)
            .createTime(UPDATED_CREATE_TIME)
            .tenantId(UPDATED_TENANT_ID);
        JobDependDTO jobDependDTO = jobDependMapper.toDto(updatedJobDepend);

        restJobDependMockMvc
            .perform(
                put(ENTITY_API_URL_ID, jobDependDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(jobDependDTO))
            )
            .andExpect(status().isOk());

        // Validate the JobDepend in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedJobDependToMatchAllProperties(updatedJobDepend);
    }

    @Test
    @Transactional
    void putNonExistingJobDepend() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        jobDepend.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the JobDepend
        JobDependDTO jobDependDTO = jobDependMapper.toDto(jobDepend);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restJobDependMockMvc
            .perform(
                put(ENTITY_API_URL_ID, jobDependDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(jobDependDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the JobDepend in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchJobDepend() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        jobDepend.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the JobDepend
        JobDependDTO jobDependDTO = jobDependMapper.toDto(jobDepend);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restJobDependMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(jobDependDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the JobDepend in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamJobDepend() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        jobDepend.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the JobDepend
        JobDependDTO jobDependDTO = jobDependMapper.toDto(jobDepend);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restJobDependMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(jobDependDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the JobDepend in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateJobDependWithPatch() throws Exception {
        // Initialize the database
        insertedJobDepend = jobDependRepository.saveAndFlush(jobDepend);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the jobDepend using partial update
        JobDepend partialUpdatedJobDepend = new JobDepend();
        partialUpdatedJobDepend.setId(jobDepend.getId());

        partialUpdatedJobDepend
            .parentJobCode(UPDATED_PARENT_JOB_CODE)
            .jobCode(UPDATED_JOB_CODE)
            .createTime(UPDATED_CREATE_TIME)
            .tenantId(UPDATED_TENANT_ID);

        restJobDependMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedJobDepend.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedJobDepend))
            )
            .andExpect(status().isOk());

        // Validate the JobDepend in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertJobDependUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedJobDepend, jobDepend),
            getPersistedJobDepend(jobDepend)
        );
    }

    @Test
    @Transactional
    void fullUpdateJobDependWithPatch() throws Exception {
        // Initialize the database
        insertedJobDepend = jobDependRepository.saveAndFlush(jobDepend);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the jobDepend using partial update
        JobDepend partialUpdatedJobDepend = new JobDepend();
        partialUpdatedJobDepend.setId(jobDepend.getId());

        partialUpdatedJobDepend
            .parentJobCode(UPDATED_PARENT_JOB_CODE)
            .childJobCode(UPDATED_CHILD_JOB_CODE)
            .jobCode(UPDATED_JOB_CODE)
            .lastInterval(UPDATED_LAST_INTERVAL)
            .createTime(UPDATED_CREATE_TIME)
            .tenantId(UPDATED_TENANT_ID);

        restJobDependMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedJobDepend.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedJobDepend))
            )
            .andExpect(status().isOk());

        // Validate the JobDepend in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertJobDependUpdatableFieldsEquals(partialUpdatedJobDepend, getPersistedJobDepend(partialUpdatedJobDepend));
    }

    @Test
    @Transactional
    void patchNonExistingJobDepend() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        jobDepend.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the JobDepend
        JobDependDTO jobDependDTO = jobDependMapper.toDto(jobDepend);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restJobDependMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, jobDependDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(jobDependDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the JobDepend in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchJobDepend() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        jobDepend.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the JobDepend
        JobDependDTO jobDependDTO = jobDependMapper.toDto(jobDepend);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restJobDependMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(jobDependDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the JobDepend in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamJobDepend() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        jobDepend.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the JobDepend
        JobDependDTO jobDependDTO = jobDependMapper.toDto(jobDepend);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restJobDependMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(jobDependDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the JobDepend in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteJobDepend() throws Exception {
        // Initialize the database
        insertedJobDepend = jobDependRepository.saveAndFlush(jobDepend);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the jobDepend
        restJobDependMockMvc
            .perform(delete(ENTITY_API_URL_ID, jobDepend.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return jobDependRepository.count();
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

    protected JobDepend getPersistedJobDepend(JobDepend jobDepend) {
        return jobDependRepository.findById(jobDepend.getId()).orElseThrow();
    }

    protected void assertPersistedJobDependToMatchAllProperties(JobDepend expectedJobDepend) {
        assertJobDependAllPropertiesEquals(expectedJobDepend, getPersistedJobDepend(expectedJobDepend));
    }

    protected void assertPersistedJobDependToMatchUpdatableProperties(JobDepend expectedJobDepend) {
        assertJobDependAllUpdatablePropertiesEquals(expectedJobDepend, getPersistedJobDepend(expectedJobDepend));
    }
}
