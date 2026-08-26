package com.data.datafusion.web.rest;

import static com.data.datafusion.domain.DataSyncAsserts.*;
import static com.data.datafusion.web.rest.TestUtil.createUpdateProxyForBean;
import static com.data.datafusion.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.DataSync;
import com.data.datafusion.repository.DataSyncRepository;
import com.data.datafusion.service.dto.DataSyncDTO;
import com.data.datafusion.service.mapper.DataSyncMapper;
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
 * Integration tests for the {@link DataSyncResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class DataSyncResourceIT {

    private static final String DEFAULT_JOB_NAME = "AAAAAAAAAA";
    private static final String UPDATED_JOB_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_JOB_CODE = "AAAAAAAAAA";
    private static final String UPDATED_JOB_CODE = "BBBBBBBBBB";

    private static final String DEFAULT_JOB_DESC = "AAAAAAAAAA";
    private static final String UPDATED_JOB_DESC = "BBBBBBBBBB";

    private static final String DEFAULT_DIR = "AAAAAAAAAA";
    private static final String UPDATED_DIR = "BBBBBBBBBB";

    private static final String DEFAULT_TYPE = "AAAAAAAAAA";
    private static final String UPDATED_TYPE = "BBBBBBBBBB";

    private static final String DEFAULT_SOURCE = "AAAAAAAAAA";
    private static final String UPDATED_SOURCE = "BBBBBBBBBB";

    private static final String DEFAULT_TARGET = "AAAAAAAAAA";
    private static final String UPDATED_TARGET = "BBBBBBBBBB";

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

    private static final String DEFAULT_PROJECT = "AAAAAAAAAA";
    private static final String UPDATED_PROJECT = "BBBBBBBBBB";

    private static final String DEFAULT_TENANT_ID = "AAAAAAAAAA";
    private static final String UPDATED_TENANT_ID = "BBBBBBBBBB";

    private static final Integer DEFAULT_DR = 1;
    private static final Integer UPDATED_DR = 2;

    private static final String ENTITY_API_URL = "/api/data-syncs";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private DataSyncRepository dataSyncRepository;

    @Autowired
    private DataSyncMapper dataSyncMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restDataSyncMockMvc;

    private DataSync dataSync;

    private DataSync insertedDataSync;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static DataSync createEntity() {
        return new DataSync()
            .jobName(DEFAULT_JOB_NAME)
            .jobCode(DEFAULT_JOB_CODE)
            .jobDesc(DEFAULT_JOB_DESC)
            .dir(DEFAULT_DIR)
            .type(DEFAULT_TYPE)
            .source(DEFAULT_SOURCE)
            .target(DEFAULT_TARGET)
            .cron(DEFAULT_CRON)
            .jobContext(DEFAULT_JOB_CONTEXT)
            .status(DEFAULT_STATUS)
            .lastStatus(DEFAULT_LAST_STATUS)
            .updateTime(DEFAULT_UPDATE_TIME)
            .createTime(DEFAULT_CREATE_TIME)
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
    public static DataSync createUpdatedEntity() {
        return new DataSync()
            .jobName(UPDATED_JOB_NAME)
            .jobCode(UPDATED_JOB_CODE)
            .jobDesc(UPDATED_JOB_DESC)
            .dir(UPDATED_DIR)
            .type(UPDATED_TYPE)
            .source(UPDATED_SOURCE)
            .target(UPDATED_TARGET)
            .cron(UPDATED_CRON)
            .jobContext(UPDATED_JOB_CONTEXT)
            .status(UPDATED_STATUS)
            .lastStatus(UPDATED_LAST_STATUS)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .project(UPDATED_PROJECT)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);
    }

    @BeforeEach
    void initTest() {
        dataSync = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedDataSync != null) {
            dataSyncRepository.delete(insertedDataSync);
            insertedDataSync = null;
        }
    }

    @Test
    @Transactional
    void createDataSync() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the DataSync
        DataSyncDTO dataSyncDTO = dataSyncMapper.toDto(dataSync);
        var returnedDataSyncDTO = om.readValue(
            restDataSyncMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dataSyncDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            DataSyncDTO.class
        );

        // Validate the DataSync in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedDataSync = dataSyncMapper.toEntity(returnedDataSyncDTO);
        assertDataSyncUpdatableFieldsEquals(returnedDataSync, getPersistedDataSync(returnedDataSync));

        insertedDataSync = returnedDataSync;
    }

    @Test
    @Transactional
    void createDataSyncWithExistingId() throws Exception {
        // Create the DataSync with an existing ID
        dataSync.setId(1L);
        DataSyncDTO dataSyncDTO = dataSyncMapper.toDto(dataSync);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restDataSyncMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dataSyncDTO)))
            .andExpect(status().isBadRequest());

        // Validate the DataSync in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void getAllDataSyncs() throws Exception {
        // Initialize the database
        insertedDataSync = dataSyncRepository.saveAndFlush(dataSync);

        // Get all the dataSyncList
        restDataSyncMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(dataSync.getId().intValue())))
            .andExpect(jsonPath("$.[*].jobName").value(hasItem(DEFAULT_JOB_NAME)))
            .andExpect(jsonPath("$.[*].jobCode").value(hasItem(DEFAULT_JOB_CODE)))
            .andExpect(jsonPath("$.[*].jobDesc").value(hasItem(DEFAULT_JOB_DESC)))
            .andExpect(jsonPath("$.[*].dir").value(hasItem(DEFAULT_DIR)))
            .andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE)))
            .andExpect(jsonPath("$.[*].source").value(hasItem(DEFAULT_SOURCE)))
            .andExpect(jsonPath("$.[*].target").value(hasItem(DEFAULT_TARGET)))
            .andExpect(jsonPath("$.[*].cron").value(hasItem(DEFAULT_CRON)))
            .andExpect(jsonPath("$.[*].jobContext").value(hasItem(DEFAULT_JOB_CONTEXT)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS)))
            .andExpect(jsonPath("$.[*].lastStatus").value(hasItem(DEFAULT_LAST_STATUS)))
            .andExpect(jsonPath("$.[*].updateTime").value(hasItem(sameInstant(DEFAULT_UPDATE_TIME))))
            .andExpect(jsonPath("$.[*].createTime").value(hasItem(sameInstant(DEFAULT_CREATE_TIME))))
            .andExpect(jsonPath("$.[*].project").value(hasItem(DEFAULT_PROJECT)))
            .andExpect(jsonPath("$.[*].tenantId").value(hasItem(DEFAULT_TENANT_ID)))
            .andExpect(jsonPath("$.[*].dr").value(hasItem(DEFAULT_DR)));
    }

    @Test
    @Transactional
    void getDataSync() throws Exception {
        // Initialize the database
        insertedDataSync = dataSyncRepository.saveAndFlush(dataSync);

        // Get the dataSync
        restDataSyncMockMvc
            .perform(get(ENTITY_API_URL_ID, dataSync.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(dataSync.getId().intValue()))
            .andExpect(jsonPath("$.jobName").value(DEFAULT_JOB_NAME))
            .andExpect(jsonPath("$.jobCode").value(DEFAULT_JOB_CODE))
            .andExpect(jsonPath("$.jobDesc").value(DEFAULT_JOB_DESC))
            .andExpect(jsonPath("$.dir").value(DEFAULT_DIR))
            .andExpect(jsonPath("$.type").value(DEFAULT_TYPE))
            .andExpect(jsonPath("$.source").value(DEFAULT_SOURCE))
            .andExpect(jsonPath("$.target").value(DEFAULT_TARGET))
            .andExpect(jsonPath("$.cron").value(DEFAULT_CRON))
            .andExpect(jsonPath("$.jobContext").value(DEFAULT_JOB_CONTEXT))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS))
            .andExpect(jsonPath("$.lastStatus").value(DEFAULT_LAST_STATUS))
            .andExpect(jsonPath("$.updateTime").value(sameInstant(DEFAULT_UPDATE_TIME)))
            .andExpect(jsonPath("$.createTime").value(sameInstant(DEFAULT_CREATE_TIME)))
            .andExpect(jsonPath("$.project").value(DEFAULT_PROJECT))
            .andExpect(jsonPath("$.tenantId").value(DEFAULT_TENANT_ID))
            .andExpect(jsonPath("$.dr").value(DEFAULT_DR));
    }

    @Test
    @Transactional
    void getNonExistingDataSync() throws Exception {
        // Get the dataSync
        restDataSyncMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingDataSync() throws Exception {
        // Initialize the database
        insertedDataSync = dataSyncRepository.saveAndFlush(dataSync);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the dataSync
        DataSync updatedDataSync = dataSyncRepository.findById(dataSync.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedDataSync are not directly saved in db
        em.detach(updatedDataSync);
        updatedDataSync
            .jobName(UPDATED_JOB_NAME)
            .jobCode(UPDATED_JOB_CODE)
            .jobDesc(UPDATED_JOB_DESC)
            .dir(UPDATED_DIR)
            .type(UPDATED_TYPE)
            .source(UPDATED_SOURCE)
            .target(UPDATED_TARGET)
            .cron(UPDATED_CRON)
            .jobContext(UPDATED_JOB_CONTEXT)
            .status(UPDATED_STATUS)
            .lastStatus(UPDATED_LAST_STATUS)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .project(UPDATED_PROJECT)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);
        DataSyncDTO dataSyncDTO = dataSyncMapper.toDto(updatedDataSync);

        restDataSyncMockMvc
            .perform(
                put(ENTITY_API_URL_ID, dataSyncDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(dataSyncDTO))
            )
            .andExpect(status().isOk());

        // Validate the DataSync in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedDataSyncToMatchAllProperties(updatedDataSync);
    }

    @Test
    @Transactional
    void putNonExistingDataSync() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dataSync.setId(longCount.incrementAndGet());

        // Create the DataSync
        DataSyncDTO dataSyncDTO = dataSyncMapper.toDto(dataSync);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restDataSyncMockMvc
            .perform(
                put(ENTITY_API_URL_ID, dataSyncDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(dataSyncDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DataSync in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchDataSync() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dataSync.setId(longCount.incrementAndGet());

        // Create the DataSync
        DataSyncDTO dataSyncDTO = dataSyncMapper.toDto(dataSync);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDataSyncMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(dataSyncDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DataSync in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamDataSync() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dataSync.setId(longCount.incrementAndGet());

        // Create the DataSync
        DataSyncDTO dataSyncDTO = dataSyncMapper.toDto(dataSync);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDataSyncMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dataSyncDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the DataSync in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateDataSyncWithPatch() throws Exception {
        // Initialize the database
        insertedDataSync = dataSyncRepository.saveAndFlush(dataSync);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the dataSync using partial update
        DataSync partialUpdatedDataSync = new DataSync();
        partialUpdatedDataSync.setId(dataSync.getId());

        partialUpdatedDataSync
            .jobName(UPDATED_JOB_NAME)
            .jobCode(UPDATED_JOB_CODE)
            .jobDesc(UPDATED_JOB_DESC)
            .target(UPDATED_TARGET)
            .status(UPDATED_STATUS)
            .lastStatus(UPDATED_LAST_STATUS)
            .createTime(UPDATED_CREATE_TIME)
            .dr(UPDATED_DR);

        restDataSyncMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedDataSync.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedDataSync))
            )
            .andExpect(status().isOk());

        // Validate the DataSync in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertDataSyncUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedDataSync, dataSync), getPersistedDataSync(dataSync));
    }

    @Test
    @Transactional
    void fullUpdateDataSyncWithPatch() throws Exception {
        // Initialize the database
        insertedDataSync = dataSyncRepository.saveAndFlush(dataSync);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the dataSync using partial update
        DataSync partialUpdatedDataSync = new DataSync();
        partialUpdatedDataSync.setId(dataSync.getId());

        partialUpdatedDataSync
            .jobName(UPDATED_JOB_NAME)
            .jobCode(UPDATED_JOB_CODE)
            .jobDesc(UPDATED_JOB_DESC)
            .dir(UPDATED_DIR)
            .type(UPDATED_TYPE)
            .source(UPDATED_SOURCE)
            .target(UPDATED_TARGET)
            .cron(UPDATED_CRON)
            .jobContext(UPDATED_JOB_CONTEXT)
            .status(UPDATED_STATUS)
            .lastStatus(UPDATED_LAST_STATUS)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .project(UPDATED_PROJECT)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);

        restDataSyncMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedDataSync.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedDataSync))
            )
            .andExpect(status().isOk());

        // Validate the DataSync in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertDataSyncUpdatableFieldsEquals(partialUpdatedDataSync, getPersistedDataSync(partialUpdatedDataSync));
    }

    @Test
    @Transactional
    void patchNonExistingDataSync() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dataSync.setId(longCount.incrementAndGet());

        // Create the DataSync
        DataSyncDTO dataSyncDTO = dataSyncMapper.toDto(dataSync);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restDataSyncMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, dataSyncDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(dataSyncDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DataSync in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchDataSync() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dataSync.setId(longCount.incrementAndGet());

        // Create the DataSync
        DataSyncDTO dataSyncDTO = dataSyncMapper.toDto(dataSync);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDataSyncMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(dataSyncDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DataSync in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamDataSync() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dataSync.setId(longCount.incrementAndGet());

        // Create the DataSync
        DataSyncDTO dataSyncDTO = dataSyncMapper.toDto(dataSync);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDataSyncMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(dataSyncDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the DataSync in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteDataSync() throws Exception {
        // Initialize the database
        insertedDataSync = dataSyncRepository.saveAndFlush(dataSync);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the dataSync
        restDataSyncMockMvc
            .perform(delete(ENTITY_API_URL_ID, dataSync.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return dataSyncRepository.count();
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

    protected DataSync getPersistedDataSync(DataSync dataSync) {
        return dataSyncRepository.findById(dataSync.getId()).orElseThrow();
    }

    protected void assertPersistedDataSyncToMatchAllProperties(DataSync expectedDataSync) {
        assertDataSyncAllPropertiesEquals(expectedDataSync, getPersistedDataSync(expectedDataSync));
    }

    protected void assertPersistedDataSyncToMatchUpdatableProperties(DataSync expectedDataSync) {
        assertDataSyncAllUpdatablePropertiesEquals(expectedDataSync, getPersistedDataSync(expectedDataSync));
    }
}
