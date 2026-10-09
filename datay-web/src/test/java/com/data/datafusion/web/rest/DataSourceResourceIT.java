package com.data.datafusion.web.rest;

import static com.data.datafusion.domain.DataSourceAsserts.*;
import static com.data.datafusion.web.rest.TestUtil.createUpdateProxyForBean;
import static com.data.datafusion.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.DataSource;
import com.data.datafusion.repository.DataSourceRepository;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.mapper.DataSourceMapper;
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
 * Integration tests for the {@link DataSourceResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class DataSourceResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final String DEFAULT_TYPE = "AAAAAAAAAA";
    private static final String UPDATED_TYPE = "BBBBBBBBBB";

    private static final String DEFAULT_URL = "AAAAAAAAAA";
    private static final String UPDATED_URL = "BBBBBBBBBB";

    private static final String DEFAULT_HOSTNAME = "AAAAAAAAAA";
    private static final String UPDATED_HOSTNAME = "BBBBBBBBBB";

    private static final String DEFAULT_PORT = "AAAAAAAAAA";
    private static final String UPDATED_PORT = "BBBBBBBBBB";

    private static final String DEFAULT_SCHEMA_NAME = "AAAAAAAAAA";
    private static final String UPDATED_SCHEMA_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_USERNAME = "AAAAAAAAAA";
    private static final String UPDATED_USERNAME = "BBBBBBBBBB";

    private static final String DEFAULT_PASSWORD = "AAAAAAAAAA";
    private static final String UPDATED_PASSWORD = "BBBBBBBBBB";

    private static final ZonedDateTime DEFAULT_UPDATE_TIME = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_UPDATE_TIME = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final ZonedDateTime DEFAULT_CREATE_TIME = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_CREATE_TIME = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final String DEFAULT_TENANT_ID = "AAAAAAAAAA";
    private static final String UPDATED_TENANT_ID = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/data-sources";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private DataSourceRepository dataSourceRepository;

    @Autowired
    private DataSourceMapper dataSourceMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restDataSourceMockMvc;

    private DataSource dataSource;

    private DataSource insertedDataSource;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static DataSource createEntity() {
        return new DataSource()
            .name(DEFAULT_NAME)
            .description(DEFAULT_DESCRIPTION)
            .type(DEFAULT_TYPE)
            .url(DEFAULT_URL)
            .hostname(DEFAULT_HOSTNAME)
            .port(DEFAULT_PORT)
            .schemaName(DEFAULT_SCHEMA_NAME)
            .username(DEFAULT_USERNAME)
            .password(DEFAULT_PASSWORD)
            .updateTime(DEFAULT_UPDATE_TIME)
            .createTime(DEFAULT_CREATE_TIME)
            .tenantId(DEFAULT_TENANT_ID);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static DataSource createUpdatedEntity() {
        return new DataSource()
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .type(UPDATED_TYPE)
            .url(UPDATED_URL)
            .hostname(UPDATED_HOSTNAME)
            .port(UPDATED_PORT)
            .schemaName(UPDATED_SCHEMA_NAME)
            .username(UPDATED_USERNAME)
            .password(UPDATED_PASSWORD)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .tenantId(UPDATED_TENANT_ID);
    }

    @BeforeEach
    void initTest() {
        dataSource = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedDataSource != null) {
            dataSourceRepository.delete(insertedDataSource);
            insertedDataSource = null;
        }
    }

    @Test
    @Transactional
    void createDataSource() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the DataSource
        DataSourceDTO dataSourceDTO = dataSourceMapper.toDto(dataSource);
        var returnedDataSourceDTO = om.readValue(
            restDataSourceMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dataSourceDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            DataSourceDTO.class
        );

        // Validate the DataSource in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedDataSource = dataSourceMapper.toEntity(returnedDataSourceDTO);
        assertDataSourceUpdatableFieldsEquals(returnedDataSource, getPersistedDataSource(returnedDataSource));

        insertedDataSource = returnedDataSource;
    }

    @Test
    @Transactional
    void createDataSourceWithExistingId() throws Exception {
        // Create the DataSource with an existing ID
        dataSource.setId("1");
        DataSourceDTO dataSourceDTO = dataSourceMapper.toDto(dataSource);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restDataSourceMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dataSourceDTO)))
            .andExpect(status().isBadRequest());

        // Validate the DataSource in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void getAllDataSources() throws Exception {
        // Initialize the database
        insertedDataSource = dataSourceRepository.saveAndFlush(dataSource);

        // Get all the dataSourceList
        restDataSourceMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(dataSource.getId())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE)))
            .andExpect(jsonPath("$.[*].url").value(hasItem(DEFAULT_URL)))
            .andExpect(jsonPath("$.[*].hostname").value(hasItem(DEFAULT_HOSTNAME)))
            .andExpect(jsonPath("$.[*].port").value(hasItem(DEFAULT_PORT)))
            .andExpect(jsonPath("$.[*].schemaName").value(hasItem(DEFAULT_SCHEMA_NAME)))
            .andExpect(jsonPath("$.[*].username").value(hasItem(DEFAULT_USERNAME)))
            .andExpect(jsonPath("$.[*].password").value(hasItem(DEFAULT_PASSWORD)))
            .andExpect(jsonPath("$.[*].updateTime").value(hasItem(sameInstant(DEFAULT_UPDATE_TIME))))
            .andExpect(jsonPath("$.[*].createTime").value(hasItem(sameInstant(DEFAULT_CREATE_TIME))))
            .andExpect(jsonPath("$.[*].tenantId").value(hasItem(DEFAULT_TENANT_ID)));
    }

    @Test
    @Transactional
    void getDataSource() throws Exception {
        // Initialize the database
        insertedDataSource = dataSourceRepository.saveAndFlush(dataSource);

        // Get the dataSource
        restDataSourceMockMvc
            .perform(get(ENTITY_API_URL_ID, dataSource.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(dataSource.getId()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.type").value(DEFAULT_TYPE))
            .andExpect(jsonPath("$.url").value(DEFAULT_URL))
            .andExpect(jsonPath("$.hostname").value(DEFAULT_HOSTNAME))
            .andExpect(jsonPath("$.port").value(DEFAULT_PORT))
            .andExpect(jsonPath("$.schemaName").value(DEFAULT_SCHEMA_NAME))
            .andExpect(jsonPath("$.username").value(DEFAULT_USERNAME))
            .andExpect(jsonPath("$.password").value(DEFAULT_PASSWORD))
            .andExpect(jsonPath("$.updateTime").value(sameInstant(DEFAULT_UPDATE_TIME)))
            .andExpect(jsonPath("$.createTime").value(sameInstant(DEFAULT_CREATE_TIME)))
            .andExpect(jsonPath("$.tenantId").value(DEFAULT_TENANT_ID));
    }

    @Test
    @Transactional
    void getNonExistingDataSource() throws Exception {
        // Get the dataSource
        restDataSourceMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingDataSource() throws Exception {
        // Initialize the database
        insertedDataSource = dataSourceRepository.saveAndFlush(dataSource);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the dataSource
        DataSource updatedDataSource = dataSourceRepository.findById(dataSource.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedDataSource are not directly saved in db
        em.detach(updatedDataSource);
        updatedDataSource
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .type(UPDATED_TYPE)
            .url(UPDATED_URL)
            .hostname(UPDATED_HOSTNAME)
            .port(UPDATED_PORT)
            .schemaName(UPDATED_SCHEMA_NAME)
            .username(UPDATED_USERNAME)
            .password(UPDATED_PASSWORD)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .tenantId(UPDATED_TENANT_ID);
        DataSourceDTO dataSourceDTO = dataSourceMapper.toDto(updatedDataSource);

        restDataSourceMockMvc
            .perform(
                put(ENTITY_API_URL_ID, dataSourceDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(dataSourceDTO))
            )
            .andExpect(status().isOk());

        // Validate the DataSource in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedDataSourceToMatchAllProperties(updatedDataSource);
    }

    @Test
    @Transactional
    void putNonExistingDataSource() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dataSource.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the DataSource
        DataSourceDTO dataSourceDTO = dataSourceMapper.toDto(dataSource);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restDataSourceMockMvc
            .perform(
                put(ENTITY_API_URL_ID, dataSourceDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(dataSourceDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DataSource in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchDataSource() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dataSource.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the DataSource
        DataSourceDTO dataSourceDTO = dataSourceMapper.toDto(dataSource);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDataSourceMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(dataSourceDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DataSource in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamDataSource() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dataSource.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the DataSource
        DataSourceDTO dataSourceDTO = dataSourceMapper.toDto(dataSource);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDataSourceMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dataSourceDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the DataSource in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateDataSourceWithPatch() throws Exception {
        // Initialize the database
        insertedDataSource = dataSourceRepository.saveAndFlush(dataSource);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the dataSource using partial update
        DataSource partialUpdatedDataSource = new DataSource();
        partialUpdatedDataSource.setId(dataSource.getId());

        partialUpdatedDataSource
            .name(UPDATED_NAME)
            .type(UPDATED_TYPE)
            .hostname(UPDATED_HOSTNAME)
            .schemaName(UPDATED_SCHEMA_NAME)
            .password(UPDATED_PASSWORD)
            .updateTime(UPDATED_UPDATE_TIME)
            .tenantId(UPDATED_TENANT_ID);

        restDataSourceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedDataSource.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedDataSource))
            )
            .andExpect(status().isOk());

        // Validate the DataSource in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertDataSourceUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedDataSource, dataSource),
            getPersistedDataSource(dataSource)
        );
    }

    @Test
    @Transactional
    void fullUpdateDataSourceWithPatch() throws Exception {
        // Initialize the database
        insertedDataSource = dataSourceRepository.saveAndFlush(dataSource);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the dataSource using partial update
        DataSource partialUpdatedDataSource = new DataSource();
        partialUpdatedDataSource.setId(dataSource.getId());

        partialUpdatedDataSource
            .name(UPDATED_NAME)
            .description(UPDATED_DESCRIPTION)
            .type(UPDATED_TYPE)
            .url(UPDATED_URL)
            .hostname(UPDATED_HOSTNAME)
            .port(UPDATED_PORT)
            .schemaName(UPDATED_SCHEMA_NAME)
            .username(UPDATED_USERNAME)
            .password(UPDATED_PASSWORD)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .tenantId(UPDATED_TENANT_ID);

        restDataSourceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedDataSource.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedDataSource))
            )
            .andExpect(status().isOk());

        // Validate the DataSource in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertDataSourceUpdatableFieldsEquals(partialUpdatedDataSource, getPersistedDataSource(partialUpdatedDataSource));
    }

    @Test
    @Transactional
    void patchNonExistingDataSource() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dataSource.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the DataSource
        DataSourceDTO dataSourceDTO = dataSourceMapper.toDto(dataSource);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restDataSourceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, dataSourceDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(dataSourceDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DataSource in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchDataSource() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dataSource.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the DataSource
        DataSourceDTO dataSourceDTO = dataSourceMapper.toDto(dataSource);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDataSourceMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(dataSourceDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DataSource in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamDataSource() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dataSource.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the DataSource
        DataSourceDTO dataSourceDTO = dataSourceMapper.toDto(dataSource);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDataSourceMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(dataSourceDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the DataSource in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteDataSource() throws Exception {
        // Initialize the database
        insertedDataSource = dataSourceRepository.saveAndFlush(dataSource);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the dataSource
        restDataSourceMockMvc
            .perform(delete(ENTITY_API_URL_ID, dataSource.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return dataSourceRepository.count();
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

    protected DataSource getPersistedDataSource(DataSource dataSource) {
        return dataSourceRepository.findById(dataSource.getId()).orElseThrow();
    }

    protected void assertPersistedDataSourceToMatchAllProperties(DataSource expectedDataSource) {
        assertDataSourceAllPropertiesEquals(expectedDataSource, getPersistedDataSource(expectedDataSource));
    }

    protected void assertPersistedDataSourceToMatchUpdatableProperties(DataSource expectedDataSource) {
        assertDataSourceAllUpdatablePropertiesEquals(expectedDataSource, getPersistedDataSource(expectedDataSource));
    }
}
