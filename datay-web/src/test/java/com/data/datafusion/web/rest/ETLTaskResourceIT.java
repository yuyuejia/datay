package com.data.datafusion.web.rest;

import static com.data.datafusion.domain.ETLTaskAsserts.*;
import static com.data.datafusion.web.rest.TestUtil.createUpdateProxyForBean;
import static com.data.datafusion.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.ETLTask;
import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.service.dto.ETLTaskDTO;
import com.data.datafusion.service.mapper.ETLTaskMapper;
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
 * Integration tests for the {@link ETLTaskResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class ETLTaskResourceIT {

    private static final String DEFAULT_TASK_NAME = "AAAAAAAAAA";
    private static final String UPDATED_TASK_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_TASK_CODE = "AAAAAAAAAA";
    private static final String UPDATED_TASK_CODE = "BBBBBBBBBB";

    private static final Long DEFAULT_JOB_ID = 1L;
    private static final Long UPDATED_JOB_ID = 2L;

    private static final String DEFAULT_TASK_DESC = "AAAAAAAAAA";
    private static final String UPDATED_TASK_DESC = "BBBBBBBBBB";

    private static final String DEFAULT_DIR = "AAAAAAAAAA";
    private static final String UPDATED_DIR = "BBBBBBBBBB";

    private static final String DEFAULT_TYPE = "AAAAAAAAAA";
    private static final String UPDATED_TYPE = "BBBBBBBBBB";

    private static final String DEFAULT_CRON = "AAAAAAAAAA";
    private static final String UPDATED_CRON = "BBBBBBBBBB";

    private static final String DEFAULT_JOB_CONTEXT = "AAAAAAAAAA";
    private static final String UPDATED_JOB_CONTEXT = "BBBBBBBBBB";

    private static final String DEFAULT_STATUS = "AAAAAAAAAA";
    private static final String UPDATED_STATUS = "BBBBBBBBBB";

    private static final String DEFAULT_LAST_STATUS = "AAAAAAAAAA";
    private static final String UPDATED_LAST_STATUS = "BBBBBBBBBB";

    private static final ZonedDateTime DEFAULT_UPDATE_TIME = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_UPDATE_TIME = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final ZonedDateTime DEFAULT_CREATE_TIME = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_CREATE_TIME = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final String DEFAULT_CREATER = "AAAAAAAAAA";
    private static final String UPDATED_CREATER = "BBBBBBBBBB";

    private static final String DEFAULT_PROJECT = "AAAAAAAAAA";
    private static final String UPDATED_PROJECT = "BBBBBBBBBB";

    private static final String DEFAULT_TENANT_ID = "AAAAAAAAAA";
    private static final String UPDATED_TENANT_ID = "BBBBBBBBBB";

    private static final Integer DEFAULT_DR = 1;
    private static final Integer UPDATED_DR = 2;

    private static final String ENTITY_API_URL = "/api/etl-tasks";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ETLTaskRepository eTLTaskRepository;

    @Autowired
    private ETLTaskMapper eTLTaskMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restETLTaskMockMvc;

    private ETLTask eTLTask;

    private ETLTask insertedETLTask;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ETLTask createEntity() {
        return new ETLTask()
            .taskName(DEFAULT_TASK_NAME)
            .taskCode(DEFAULT_TASK_CODE)
            .jobId(DEFAULT_JOB_ID)
            .taskDesc(DEFAULT_TASK_DESC)
            .dir(DEFAULT_DIR)
            .type(DEFAULT_TYPE)
            .cron(DEFAULT_CRON)
            .jobContext(DEFAULT_JOB_CONTEXT)
            .status(DEFAULT_STATUS)
            .lastStatus(DEFAULT_LAST_STATUS)
            .updateTime(DEFAULT_UPDATE_TIME)
            .createTime(DEFAULT_CREATE_TIME)
            .creater(DEFAULT_CREATER)
            .project(DEFAULT_PROJECT)
            .tenantId(DEFAULT_TENANT_ID)
            .dr(DEFAULT_DR);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ETLTask createUpdatedEntity() {
        return new ETLTask()
            .taskName(UPDATED_TASK_NAME)
            .taskCode(UPDATED_TASK_CODE)
            .jobId(UPDATED_JOB_ID)
            .taskDesc(UPDATED_TASK_DESC)
            .dir(UPDATED_DIR)
            .type(UPDATED_TYPE)
            .cron(UPDATED_CRON)
            .jobContext(UPDATED_JOB_CONTEXT)
            .status(UPDATED_STATUS)
            .lastStatus(UPDATED_LAST_STATUS)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .creater(UPDATED_CREATER)
            .project(UPDATED_PROJECT)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);
    }

    @BeforeEach
    void initTest() {
        eTLTask = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedETLTask != null) {
            eTLTaskRepository.delete(insertedETLTask);
            insertedETLTask = null;
        }
    }

    @Test
    @Transactional
    void createETLTask() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the ETLTask
        ETLTaskDTO eTLTaskDTO = eTLTaskMapper.toDto(eTLTask);
        var returnedETLTaskDTO = om.readValue(
            restETLTaskMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(eTLTaskDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ETLTaskDTO.class
        );

        // Validate the ETLTask in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedETLTask = eTLTaskMapper.toEntity(returnedETLTaskDTO);
        assertETLTaskUpdatableFieldsEquals(returnedETLTask, getPersistedETLTask(returnedETLTask));

        insertedETLTask = returnedETLTask;
    }

    @Test
    @Transactional
    void createETLTaskWithExistingId() throws Exception {
        // Create the ETLTask with an existing ID
        eTLTask.setId(1L);
        ETLTaskDTO eTLTaskDTO = eTLTaskMapper.toDto(eTLTask);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restETLTaskMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(eTLTaskDTO)))
            .andExpect(status().isBadRequest());

        // Validate the ETLTask in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void getAllETLTasks() throws Exception {
        // Initialize the database
        insertedETLTask = eTLTaskRepository.saveAndFlush(eTLTask);

        // Get all the eTLTaskList
        restETLTaskMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(eTLTask.getId().intValue())))
            .andExpect(jsonPath("$.[*].taskName").value(hasItem(DEFAULT_TASK_NAME)))
            .andExpect(jsonPath("$.[*].taskCode").value(hasItem(DEFAULT_TASK_CODE)))
            .andExpect(jsonPath("$.[*].jobId").value(hasItem(DEFAULT_JOB_ID.intValue())))
            .andExpect(jsonPath("$.[*].taskDesc").value(hasItem(DEFAULT_TASK_DESC)))
            .andExpect(jsonPath("$.[*].dir").value(hasItem(DEFAULT_DIR)))
            .andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE)))
            .andExpect(jsonPath("$.[*].cron").value(hasItem(DEFAULT_CRON)))
            .andExpect(jsonPath("$.[*].jobContext").value(hasItem(DEFAULT_JOB_CONTEXT)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS)))
            .andExpect(jsonPath("$.[*].lastStatus").value(hasItem(DEFAULT_LAST_STATUS)))
            .andExpect(jsonPath("$.[*].updateTime").value(hasItem(sameInstant(DEFAULT_UPDATE_TIME))))
            .andExpect(jsonPath("$.[*].createTime").value(hasItem(sameInstant(DEFAULT_CREATE_TIME))))
            .andExpect(jsonPath("$.[*].creater").value(hasItem(DEFAULT_CREATER)))
            .andExpect(jsonPath("$.[*].project").value(hasItem(DEFAULT_PROJECT)))
            .andExpect(jsonPath("$.[*].tenantId").value(hasItem(DEFAULT_TENANT_ID)))
            .andExpect(jsonPath("$.[*].dr").value(hasItem(DEFAULT_DR)));
    }

    @Test
    @Transactional
    void getETLTask() throws Exception {
        // Initialize the database
        insertedETLTask = eTLTaskRepository.saveAndFlush(eTLTask);

        // Get the eTLTask
        restETLTaskMockMvc
            .perform(get(ENTITY_API_URL_ID, eTLTask.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(eTLTask.getId().intValue()))
            .andExpect(jsonPath("$.taskName").value(DEFAULT_TASK_NAME))
            .andExpect(jsonPath("$.taskCode").value(DEFAULT_TASK_CODE))
            .andExpect(jsonPath("$.jobId").value(DEFAULT_JOB_ID.intValue()))
            .andExpect(jsonPath("$.taskDesc").value(DEFAULT_TASK_DESC))
            .andExpect(jsonPath("$.dir").value(DEFAULT_DIR))
            .andExpect(jsonPath("$.type").value(DEFAULT_TYPE))
            .andExpect(jsonPath("$.cron").value(DEFAULT_CRON))
            .andExpect(jsonPath("$.jobContext").value(DEFAULT_JOB_CONTEXT))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS))
            .andExpect(jsonPath("$.lastStatus").value(DEFAULT_LAST_STATUS))
            .andExpect(jsonPath("$.updateTime").value(sameInstant(DEFAULT_UPDATE_TIME)))
            .andExpect(jsonPath("$.createTime").value(sameInstant(DEFAULT_CREATE_TIME)))
            .andExpect(jsonPath("$.creater").value(DEFAULT_CREATER))
            .andExpect(jsonPath("$.project").value(DEFAULT_PROJECT))
            .andExpect(jsonPath("$.tenantId").value(DEFAULT_TENANT_ID))
            .andExpect(jsonPath("$.dr").value(DEFAULT_DR));
    }

    @Test
    @Transactional
    void getNonExistingETLTask() throws Exception {
        // Get the eTLTask
        restETLTaskMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingETLTask() throws Exception {
        // Initialize the database
        insertedETLTask = eTLTaskRepository.saveAndFlush(eTLTask);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the eTLTask
        ETLTask updatedETLTask = eTLTaskRepository.findById(eTLTask.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedETLTask are not directly saved in db
        em.detach(updatedETLTask);
        updatedETLTask
            .taskName(UPDATED_TASK_NAME)
            .taskCode(UPDATED_TASK_CODE)
            .jobId(UPDATED_JOB_ID)
            .taskDesc(UPDATED_TASK_DESC)
            .dir(UPDATED_DIR)
            .type(UPDATED_TYPE)
            .cron(UPDATED_CRON)
            .jobContext(UPDATED_JOB_CONTEXT)
            .status(UPDATED_STATUS)
            .lastStatus(UPDATED_LAST_STATUS)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .creater(UPDATED_CREATER)
            .project(UPDATED_PROJECT)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);
        ETLTaskDTO eTLTaskDTO = eTLTaskMapper.toDto(updatedETLTask);

        restETLTaskMockMvc
            .perform(
                put(ENTITY_API_URL_ID, eTLTaskDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(eTLTaskDTO))
            )
            .andExpect(status().isOk());

        // Validate the ETLTask in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedETLTaskToMatchAllProperties(updatedETLTask);
    }

    @Test
    @Transactional
    void putNonExistingETLTask() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLTask.setId(longCount.incrementAndGet());

        // Create the ETLTask
        ETLTaskDTO eTLTaskDTO = eTLTaskMapper.toDto(eTLTask);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restETLTaskMockMvc
            .perform(
                put(ENTITY_API_URL_ID, eTLTaskDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(eTLTaskDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ETLTask in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchETLTask() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLTask.setId(longCount.incrementAndGet());

        // Create the ETLTask
        ETLTaskDTO eTLTaskDTO = eTLTaskMapper.toDto(eTLTask);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restETLTaskMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(eTLTaskDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ETLTask in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamETLTask() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLTask.setId(longCount.incrementAndGet());

        // Create the ETLTask
        ETLTaskDTO eTLTaskDTO = eTLTaskMapper.toDto(eTLTask);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restETLTaskMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(eTLTaskDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ETLTask in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateETLTaskWithPatch() throws Exception {
        // Initialize the database
        insertedETLTask = eTLTaskRepository.saveAndFlush(eTLTask);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the eTLTask using partial update
        ETLTask partialUpdatedETLTask = new ETLTask();
        partialUpdatedETLTask.setId(eTLTask.getId());

        partialUpdatedETLTask
            .type(UPDATED_TYPE)
            .lastStatus(UPDATED_LAST_STATUS)
            .createTime(UPDATED_CREATE_TIME)
            .project(UPDATED_PROJECT)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);

        restETLTaskMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedETLTask.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedETLTask))
            )
            .andExpect(status().isOk());

        // Validate the ETLTask in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertETLTaskUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedETLTask, eTLTask), getPersistedETLTask(eTLTask));
    }

    @Test
    @Transactional
    void fullUpdateETLTaskWithPatch() throws Exception {
        // Initialize the database
        insertedETLTask = eTLTaskRepository.saveAndFlush(eTLTask);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the eTLTask using partial update
        ETLTask partialUpdatedETLTask = new ETLTask();
        partialUpdatedETLTask.setId(eTLTask.getId());

        partialUpdatedETLTask
            .taskName(UPDATED_TASK_NAME)
            .taskCode(UPDATED_TASK_CODE)
            .jobId(UPDATED_JOB_ID)
            .taskDesc(UPDATED_TASK_DESC)
            .dir(UPDATED_DIR)
            .type(UPDATED_TYPE)
            .cron(UPDATED_CRON)
            .jobContext(UPDATED_JOB_CONTEXT)
            .status(UPDATED_STATUS)
            .lastStatus(UPDATED_LAST_STATUS)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .creater(UPDATED_CREATER)
            .project(UPDATED_PROJECT)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);

        restETLTaskMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedETLTask.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedETLTask))
            )
            .andExpect(status().isOk());

        // Validate the ETLTask in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertETLTaskUpdatableFieldsEquals(partialUpdatedETLTask, getPersistedETLTask(partialUpdatedETLTask));
    }

    @Test
    @Transactional
    void patchNonExistingETLTask() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLTask.setId(longCount.incrementAndGet());

        // Create the ETLTask
        ETLTaskDTO eTLTaskDTO = eTLTaskMapper.toDto(eTLTask);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restETLTaskMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, eTLTaskDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(eTLTaskDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ETLTask in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchETLTask() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLTask.setId(longCount.incrementAndGet());

        // Create the ETLTask
        ETLTaskDTO eTLTaskDTO = eTLTaskMapper.toDto(eTLTask);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restETLTaskMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(eTLTaskDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ETLTask in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamETLTask() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLTask.setId(longCount.incrementAndGet());

        // Create the ETLTask
        ETLTaskDTO eTLTaskDTO = eTLTaskMapper.toDto(eTLTask);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restETLTaskMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(eTLTaskDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ETLTask in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteETLTask() throws Exception {
        // Initialize the database
        insertedETLTask = eTLTaskRepository.saveAndFlush(eTLTask);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the eTLTask
        restETLTaskMockMvc
            .perform(delete(ENTITY_API_URL_ID, eTLTask.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return eTLTaskRepository.count();
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

    protected ETLTask getPersistedETLTask(ETLTask eTLTask) {
        return eTLTaskRepository.findById(eTLTask.getId()).orElseThrow();
    }

    protected void assertPersistedETLTaskToMatchAllProperties(ETLTask expectedETLTask) {
        assertETLTaskAllPropertiesEquals(expectedETLTask, getPersistedETLTask(expectedETLTask));
    }

    protected void assertPersistedETLTaskToMatchUpdatableProperties(ETLTask expectedETLTask) {
        assertETLTaskAllUpdatablePropertiesEquals(expectedETLTask, getPersistedETLTask(expectedETLTask));
    }
}
