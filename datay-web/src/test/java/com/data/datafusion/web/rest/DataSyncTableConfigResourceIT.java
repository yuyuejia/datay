package com.data.datafusion.web.rest;

import static com.data.datafusion.domain.DataSyncTableConfigAsserts.*;
import static com.data.datafusion.web.rest.TestUtil.createUpdateProxyForBean;
import static com.data.datafusion.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.DataSyncTableConfig;
import com.data.datafusion.repository.DataSyncTableConfigRepository;
import com.data.datafusion.service.dto.DataSyncTableConfigDTO;
import com.data.datafusion.service.mapper.DataSyncTableConfigMapper;
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
 * Integration tests for the {@link DataSyncTableConfigResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class DataSyncTableConfigResourceIT {

    private static final String DEFAULT_SYNC_TASK = "AAAAAAAAAA";
    private static final String UPDATED_SYNC_TASK = "BBBBBBBBBB";

    private static final String DEFAULT_SRC_DATASOURCE = "AAAAAAAAAA";
    private static final String UPDATED_SRC_DATASOURCE = "BBBBBBBBBB";

    private static final String DEFAULT_SRC_SCHEMA_NAME = "AAAAAAAAAA";
    private static final String UPDATED_SRC_SCHEMA_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_SRC_TABLE_NAME = "AAAAAAAAAA";
    private static final String UPDATED_SRC_TABLE_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_SRC_COL_PKS = "AAAAAAAAAA";
    private static final String UPDATED_SRC_COL_PKS = "BBBBBBBBBB";

    private static final String DEFAULT_DES_DATASOURCE = "AAAAAAAAAA";
    private static final String UPDATED_DES_DATASOURCE = "BBBBBBBBBB";

    private static final String DEFAULT_DES_SCHEMA_NAME = "AAAAAAAAAA";
    private static final String UPDATED_DES_SCHEMA_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DES_TABLE_NAME = "AAAAAAAAAA";
    private static final String UPDATED_DES_TABLE_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DES_COL_PKS = "AAAAAAAAAA";
    private static final String UPDATED_DES_COL_PKS = "BBBBBBBBBB";

    private static final String DEFAULT_JOB_DESC = "AAAAAAAAAA";
    private static final String UPDATED_JOB_DESC = "BBBBBBBBBB";

    private static final String DEFAULT_COLUME_CONFIG = "AAAAAAAAAA";
    private static final String UPDATED_COLUME_CONFIG = "BBBBBBBBBB";

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

    private static final String ENTITY_API_URL = "/api/data-sync-table-configs";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private DataSyncTableConfigRepository dataSyncTableConfigRepository;

    @Autowired
    private DataSyncTableConfigMapper dataSyncTableConfigMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restDataSyncTableConfigMockMvc;

    private DataSyncTableConfig dataSyncTableConfig;

    private DataSyncTableConfig insertedDataSyncTableConfig;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static DataSyncTableConfig createEntity() {
        return new DataSyncTableConfig()
            .syncTask(DEFAULT_SYNC_TASK)
            .srcDatasource(DEFAULT_SRC_DATASOURCE)
            .srcSchemaName(DEFAULT_SRC_SCHEMA_NAME)
            .srcTableName(DEFAULT_SRC_TABLE_NAME)
            .srcColPks(DEFAULT_SRC_COL_PKS)
            .desDatasource(DEFAULT_DES_DATASOURCE)
            .desSchemaName(DEFAULT_DES_SCHEMA_NAME)
            .desTableName(DEFAULT_DES_TABLE_NAME)
            .desColPks(DEFAULT_DES_COL_PKS)
            .jobDesc(DEFAULT_JOB_DESC)
            .columeConfig(DEFAULT_COLUME_CONFIG)
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
    public static DataSyncTableConfig createUpdatedEntity() {
        return new DataSyncTableConfig()
            .syncTask(UPDATED_SYNC_TASK)
            .srcDatasource(UPDATED_SRC_DATASOURCE)
            .srcSchemaName(UPDATED_SRC_SCHEMA_NAME)
            .srcTableName(UPDATED_SRC_TABLE_NAME)
            .srcColPks(UPDATED_SRC_COL_PKS)
            .desDatasource(UPDATED_DES_DATASOURCE)
            .desSchemaName(UPDATED_DES_SCHEMA_NAME)
            .desTableName(UPDATED_DES_TABLE_NAME)
            .desColPks(UPDATED_DES_COL_PKS)
            .jobDesc(UPDATED_JOB_DESC)
            .columeConfig(UPDATED_COLUME_CONFIG)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .project(UPDATED_PROJECT)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);
    }

    @BeforeEach
    void initTest() {
        dataSyncTableConfig = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedDataSyncTableConfig != null) {
            dataSyncTableConfigRepository.delete(insertedDataSyncTableConfig);
            insertedDataSyncTableConfig = null;
        }
    }

    @Test
    @Transactional
    void createDataSyncTableConfig() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the DataSyncTableConfig
        DataSyncTableConfigDTO dataSyncTableConfigDTO = dataSyncTableConfigMapper.toDto(dataSyncTableConfig);
        var returnedDataSyncTableConfigDTO = om.readValue(
            restDataSyncTableConfigMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dataSyncTableConfigDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            DataSyncTableConfigDTO.class
        );

        // Validate the DataSyncTableConfig in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedDataSyncTableConfig = dataSyncTableConfigMapper.toEntity(returnedDataSyncTableConfigDTO);
        assertDataSyncTableConfigUpdatableFieldsEquals(
            returnedDataSyncTableConfig,
            getPersistedDataSyncTableConfig(returnedDataSyncTableConfig)
        );

        insertedDataSyncTableConfig = returnedDataSyncTableConfig;
    }

    @Test
    @Transactional
    void createDataSyncTableConfigWithExistingId() throws Exception {
        // Create the DataSyncTableConfig with an existing ID
        dataSyncTableConfig.setId("1");
        DataSyncTableConfigDTO dataSyncTableConfigDTO = dataSyncTableConfigMapper.toDto(dataSyncTableConfig);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restDataSyncTableConfigMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dataSyncTableConfigDTO)))
            .andExpect(status().isBadRequest());

        // Validate the DataSyncTableConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void getAllDataSyncTableConfigs() throws Exception {
        // Initialize the database
        insertedDataSyncTableConfig = dataSyncTableConfigRepository.saveAndFlush(dataSyncTableConfig);

        // Get all the dataSyncTableConfigList
        restDataSyncTableConfigMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(dataSyncTableConfig.getId())))
            .andExpect(jsonPath("$.[*].syncTask").value(hasItem(DEFAULT_SYNC_TASK)))
            .andExpect(jsonPath("$.[*].srcDatasource").value(hasItem(DEFAULT_SRC_DATASOURCE)))
            .andExpect(jsonPath("$.[*].srcSchemaName").value(hasItem(DEFAULT_SRC_SCHEMA_NAME)))
            .andExpect(jsonPath("$.[*].srcTableName").value(hasItem(DEFAULT_SRC_TABLE_NAME)))
            .andExpect(jsonPath("$.[*].srcColPks").value(hasItem(DEFAULT_SRC_COL_PKS)))
            .andExpect(jsonPath("$.[*].desDatasource").value(hasItem(DEFAULT_DES_DATASOURCE)))
            .andExpect(jsonPath("$.[*].desSchemaName").value(hasItem(DEFAULT_DES_SCHEMA_NAME)))
            .andExpect(jsonPath("$.[*].desTableName").value(hasItem(DEFAULT_DES_TABLE_NAME)))
            .andExpect(jsonPath("$.[*].desColPks").value(hasItem(DEFAULT_DES_COL_PKS)))
            .andExpect(jsonPath("$.[*].jobDesc").value(hasItem(DEFAULT_JOB_DESC)))
            .andExpect(jsonPath("$.[*].columeConfig").value(hasItem(DEFAULT_COLUME_CONFIG)))
            .andExpect(jsonPath("$.[*].updateTime").value(hasItem(sameInstant(DEFAULT_UPDATE_TIME))))
            .andExpect(jsonPath("$.[*].createTime").value(hasItem(sameInstant(DEFAULT_CREATE_TIME))))
            .andExpect(jsonPath("$.[*].project").value(hasItem(DEFAULT_PROJECT)))
            .andExpect(jsonPath("$.[*].tenantId").value(hasItem(DEFAULT_TENANT_ID)))
            .andExpect(jsonPath("$.[*].dr").value(hasItem(DEFAULT_DR)));
    }

    @Test
    @Transactional
    void getDataSyncTableConfig() throws Exception {
        // Initialize the database
        insertedDataSyncTableConfig = dataSyncTableConfigRepository.saveAndFlush(dataSyncTableConfig);

        // Get the dataSyncTableConfig
        restDataSyncTableConfigMockMvc
            .perform(get(ENTITY_API_URL_ID, dataSyncTableConfig.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(dataSyncTableConfig.getId()))
            .andExpect(jsonPath("$.syncTask").value(DEFAULT_SYNC_TASK))
            .andExpect(jsonPath("$.srcDatasource").value(DEFAULT_SRC_DATASOURCE))
            .andExpect(jsonPath("$.srcSchemaName").value(DEFAULT_SRC_SCHEMA_NAME))
            .andExpect(jsonPath("$.srcTableName").value(DEFAULT_SRC_TABLE_NAME))
            .andExpect(jsonPath("$.srcColPks").value(DEFAULT_SRC_COL_PKS))
            .andExpect(jsonPath("$.desDatasource").value(DEFAULT_DES_DATASOURCE))
            .andExpect(jsonPath("$.desSchemaName").value(DEFAULT_DES_SCHEMA_NAME))
            .andExpect(jsonPath("$.desTableName").value(DEFAULT_DES_TABLE_NAME))
            .andExpect(jsonPath("$.desColPks").value(DEFAULT_DES_COL_PKS))
            .andExpect(jsonPath("$.jobDesc").value(DEFAULT_JOB_DESC))
            .andExpect(jsonPath("$.columeConfig").value(DEFAULT_COLUME_CONFIG))
            .andExpect(jsonPath("$.updateTime").value(sameInstant(DEFAULT_UPDATE_TIME)))
            .andExpect(jsonPath("$.createTime").value(sameInstant(DEFAULT_CREATE_TIME)))
            .andExpect(jsonPath("$.project").value(DEFAULT_PROJECT))
            .andExpect(jsonPath("$.tenantId").value(DEFAULT_TENANT_ID))
            .andExpect(jsonPath("$.dr").value(DEFAULT_DR));
    }

    @Test
    @Transactional
    void getNonExistingDataSyncTableConfig() throws Exception {
        // Get the dataSyncTableConfig
        restDataSyncTableConfigMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingDataSyncTableConfig() throws Exception {
        // Initialize the database
        insertedDataSyncTableConfig = dataSyncTableConfigRepository.saveAndFlush(dataSyncTableConfig);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the dataSyncTableConfig
        DataSyncTableConfig updatedDataSyncTableConfig = dataSyncTableConfigRepository.findById(dataSyncTableConfig.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedDataSyncTableConfig are not directly saved in db
        em.detach(updatedDataSyncTableConfig);
        updatedDataSyncTableConfig
            .syncTask(UPDATED_SYNC_TASK)
            .srcDatasource(UPDATED_SRC_DATASOURCE)
            .srcSchemaName(UPDATED_SRC_SCHEMA_NAME)
            .srcTableName(UPDATED_SRC_TABLE_NAME)
            .srcColPks(UPDATED_SRC_COL_PKS)
            .desDatasource(UPDATED_DES_DATASOURCE)
            .desSchemaName(UPDATED_DES_SCHEMA_NAME)
            .desTableName(UPDATED_DES_TABLE_NAME)
            .desColPks(UPDATED_DES_COL_PKS)
            .jobDesc(UPDATED_JOB_DESC)
            .columeConfig(UPDATED_COLUME_CONFIG)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .project(UPDATED_PROJECT)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);
        DataSyncTableConfigDTO dataSyncTableConfigDTO = dataSyncTableConfigMapper.toDto(updatedDataSyncTableConfig);

        restDataSyncTableConfigMockMvc
            .perform(
                put(ENTITY_API_URL_ID, dataSyncTableConfigDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(dataSyncTableConfigDTO))
            )
            .andExpect(status().isOk());

        // Validate the DataSyncTableConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedDataSyncTableConfigToMatchAllProperties(updatedDataSyncTableConfig);
    }

    @Test
    @Transactional
    void putNonExistingDataSyncTableConfig() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dataSyncTableConfig.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the DataSyncTableConfig
        DataSyncTableConfigDTO dataSyncTableConfigDTO = dataSyncTableConfigMapper.toDto(dataSyncTableConfig);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restDataSyncTableConfigMockMvc
            .perform(
                put(ENTITY_API_URL_ID, dataSyncTableConfigDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(dataSyncTableConfigDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DataSyncTableConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchDataSyncTableConfig() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dataSyncTableConfig.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the DataSyncTableConfig
        DataSyncTableConfigDTO dataSyncTableConfigDTO = dataSyncTableConfigMapper.toDto(dataSyncTableConfig);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDataSyncTableConfigMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(dataSyncTableConfigDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DataSyncTableConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamDataSyncTableConfig() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dataSyncTableConfig.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the DataSyncTableConfig
        DataSyncTableConfigDTO dataSyncTableConfigDTO = dataSyncTableConfigMapper.toDto(dataSyncTableConfig);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDataSyncTableConfigMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dataSyncTableConfigDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the DataSyncTableConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateDataSyncTableConfigWithPatch() throws Exception {
        // Initialize the database
        insertedDataSyncTableConfig = dataSyncTableConfigRepository.saveAndFlush(dataSyncTableConfig);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the dataSyncTableConfig using partial update
        DataSyncTableConfig partialUpdatedDataSyncTableConfig = new DataSyncTableConfig();
        partialUpdatedDataSyncTableConfig.setId(dataSyncTableConfig.getId());

        partialUpdatedDataSyncTableConfig
            .syncTask(UPDATED_SYNC_TASK)
            .srcTableName(UPDATED_SRC_TABLE_NAME)
            .srcColPks(UPDATED_SRC_COL_PKS)
            .columeConfig(UPDATED_COLUME_CONFIG)
            .tenantId(UPDATED_TENANT_ID);

        restDataSyncTableConfigMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedDataSyncTableConfig.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedDataSyncTableConfig))
            )
            .andExpect(status().isOk());

        // Validate the DataSyncTableConfig in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertDataSyncTableConfigUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedDataSyncTableConfig, dataSyncTableConfig),
            getPersistedDataSyncTableConfig(dataSyncTableConfig)
        );
    }

    @Test
    @Transactional
    void fullUpdateDataSyncTableConfigWithPatch() throws Exception {
        // Initialize the database
        insertedDataSyncTableConfig = dataSyncTableConfigRepository.saveAndFlush(dataSyncTableConfig);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the dataSyncTableConfig using partial update
        DataSyncTableConfig partialUpdatedDataSyncTableConfig = new DataSyncTableConfig();
        partialUpdatedDataSyncTableConfig.setId(dataSyncTableConfig.getId());

        partialUpdatedDataSyncTableConfig
            .syncTask(UPDATED_SYNC_TASK)
            .srcDatasource(UPDATED_SRC_DATASOURCE)
            .srcSchemaName(UPDATED_SRC_SCHEMA_NAME)
            .srcTableName(UPDATED_SRC_TABLE_NAME)
            .srcColPks(UPDATED_SRC_COL_PKS)
            .desDatasource(UPDATED_DES_DATASOURCE)
            .desSchemaName(UPDATED_DES_SCHEMA_NAME)
            .desTableName(UPDATED_DES_TABLE_NAME)
            .desColPks(UPDATED_DES_COL_PKS)
            .jobDesc(UPDATED_JOB_DESC)
            .columeConfig(UPDATED_COLUME_CONFIG)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .project(UPDATED_PROJECT)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);

        restDataSyncTableConfigMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedDataSyncTableConfig.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedDataSyncTableConfig))
            )
            .andExpect(status().isOk());

        // Validate the DataSyncTableConfig in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertDataSyncTableConfigUpdatableFieldsEquals(
            partialUpdatedDataSyncTableConfig,
            getPersistedDataSyncTableConfig(partialUpdatedDataSyncTableConfig)
        );
    }

    @Test
    @Transactional
    void patchNonExistingDataSyncTableConfig() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dataSyncTableConfig.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the DataSyncTableConfig
        DataSyncTableConfigDTO dataSyncTableConfigDTO = dataSyncTableConfigMapper.toDto(dataSyncTableConfig);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restDataSyncTableConfigMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, dataSyncTableConfigDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(dataSyncTableConfigDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DataSyncTableConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchDataSyncTableConfig() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dataSyncTableConfig.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the DataSyncTableConfig
        DataSyncTableConfigDTO dataSyncTableConfigDTO = dataSyncTableConfigMapper.toDto(dataSyncTableConfig);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDataSyncTableConfigMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(dataSyncTableConfigDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DataSyncTableConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamDataSyncTableConfig() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dataSyncTableConfig.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the DataSyncTableConfig
        DataSyncTableConfigDTO dataSyncTableConfigDTO = dataSyncTableConfigMapper.toDto(dataSyncTableConfig);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDataSyncTableConfigMockMvc
            .perform(
                patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(dataSyncTableConfigDTO))
            )
            .andExpect(status().isMethodNotAllowed());

        // Validate the DataSyncTableConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteDataSyncTableConfig() throws Exception {
        // Initialize the database
        insertedDataSyncTableConfig = dataSyncTableConfigRepository.saveAndFlush(dataSyncTableConfig);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the dataSyncTableConfig
        restDataSyncTableConfigMockMvc
            .perform(delete(ENTITY_API_URL_ID, dataSyncTableConfig.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return dataSyncTableConfigRepository.count();
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

    protected DataSyncTableConfig getPersistedDataSyncTableConfig(DataSyncTableConfig dataSyncTableConfig) {
        return dataSyncTableConfigRepository.findById(dataSyncTableConfig.getId()).orElseThrow();
    }

    protected void assertPersistedDataSyncTableConfigToMatchAllProperties(DataSyncTableConfig expectedDataSyncTableConfig) {
        assertDataSyncTableConfigAllPropertiesEquals(
            expectedDataSyncTableConfig,
            getPersistedDataSyncTableConfig(expectedDataSyncTableConfig)
        );
    }

    protected void assertPersistedDataSyncTableConfigToMatchUpdatableProperties(DataSyncTableConfig expectedDataSyncTableConfig) {
        assertDataSyncTableConfigAllUpdatablePropertiesEquals(
            expectedDataSyncTableConfig,
            getPersistedDataSyncTableConfig(expectedDataSyncTableConfig)
        );
    }
}
