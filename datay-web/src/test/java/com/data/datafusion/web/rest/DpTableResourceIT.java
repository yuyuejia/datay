package com.data.datafusion.web.rest;

import static com.data.datafusion.domain.DpTableAsserts.*;
import static com.data.datafusion.web.rest.TestUtil.createUpdateProxyForBean;
import static com.data.datafusion.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.DpTable;
import com.data.datafusion.repository.DpTableRepository;
import com.data.datafusion.service.dto.DpTableDTO;
import com.data.datafusion.service.mapper.DpTableMapper;
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
 * Integration tests for the {@link DpTableResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class DpTableResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_SCHEMA_NAME = "AAAAAAAAAA";
    private static final String UPDATED_SCHEMA_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_DESCRIPTION = "AAAAAAAAAA";
    private static final String UPDATED_DESCRIPTION = "BBBBBBBBBB";

    private static final String DEFAULT_SOURCE_DB_TYPE = "AAAAAAAAAA";
    private static final String UPDATED_SOURCE_DB_TYPE = "BBBBBBBBBB";

    private static final String DEFAULT_SOURCE_ID = "AAAAAAAAAA";
    private static final String UPDATED_SOURCE_ID = "BBBBBBBBBB";

    private static final String DEFAULT_SOURCE_SCHEMA = "AAAAAAAAAA";
    private static final String UPDATED_SOURCE_SCHEMA = "BBBBBBBBBB";

    private static final String DEFAULT_SOURCE_TABLE = "AAAAAAAAAA";
    private static final String UPDATED_SOURCE_TABLE = "BBBBBBBBBB";

    private static final String DEFAULT_SQL_CONTENT = "AAAAAAAAAA";
    private static final String UPDATED_SQL_CONTENT = "BBBBBBBBBB";

    private static final String DEFAULT_FILE_TYPE = "AAAAAAAAAA";
    private static final String UPDATED_FILE_TYPE = "BBBBBBBBBB";

    private static final String DEFAULT_FILE_PATH = "AAAAAAAAAA";
    private static final String UPDATED_FILE_PATH = "BBBBBBBBBB";

    private static final ZonedDateTime DEFAULT_UPDATE_TIME = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_UPDATE_TIME = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final ZonedDateTime DEFAULT_CREATE_TIME = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_CREATE_TIME = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final String DEFAULT_TENANT_ID = "AAAAAAAAAA";
    private static final String UPDATED_TENANT_ID = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/dp-tables";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private DpTableRepository dpTableRepository;

    @Autowired
    private DpTableMapper dpTableMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restDpTableMockMvc;

    private DpTable dpTable;

    private DpTable insertedDpTable;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static DpTable createEntity() {
        return new DpTable()
            .name(DEFAULT_NAME)
            .schemaName(DEFAULT_SCHEMA_NAME)
            .description(DEFAULT_DESCRIPTION)
            .sourceDBType(DEFAULT_SOURCE_DB_TYPE)
            .sourceId(DEFAULT_SOURCE_ID)
            .sourceSchema(DEFAULT_SOURCE_SCHEMA)
            .sourceTable(DEFAULT_SOURCE_TABLE)
            .sqlContent(DEFAULT_SQL_CONTENT)
            .fileType(DEFAULT_FILE_TYPE)
            .filePath(DEFAULT_FILE_PATH)
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
    public static DpTable createUpdatedEntity() {
        return new DpTable()
            .name(UPDATED_NAME)
            .schemaName(UPDATED_SCHEMA_NAME)
            .description(UPDATED_DESCRIPTION)
            .sourceDBType(UPDATED_SOURCE_DB_TYPE)
            .sourceId(UPDATED_SOURCE_ID)
            .sourceSchema(UPDATED_SOURCE_SCHEMA)
            .sourceTable(UPDATED_SOURCE_TABLE)
            .sqlContent(UPDATED_SQL_CONTENT)
            .fileType(UPDATED_FILE_TYPE)
            .filePath(UPDATED_FILE_PATH)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .tenantId(UPDATED_TENANT_ID);
    }

    @BeforeEach
    void initTest() {
        dpTable = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedDpTable != null) {
            dpTableRepository.delete(insertedDpTable);
            insertedDpTable = null;
        }
    }

    @Test
    @Transactional
    void createDpTable() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the DpTable
        DpTableDTO dpTableDTO = dpTableMapper.toDto(dpTable);
        var returnedDpTableDTO = om.readValue(
            restDpTableMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dpTableDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            DpTableDTO.class
        );

        // Validate the DpTable in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedDpTable = dpTableMapper.toEntity(returnedDpTableDTO);
        assertDpTableUpdatableFieldsEquals(returnedDpTable, getPersistedDpTable(returnedDpTable));

        insertedDpTable = returnedDpTable;
    }

    @Test
    @Transactional
    void createDpTableWithExistingId() throws Exception {
        // Create the DpTable with an existing ID
        dpTable.setId(1L);
        DpTableDTO dpTableDTO = dpTableMapper.toDto(dpTable);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restDpTableMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dpTableDTO)))
            .andExpect(status().isBadRequest());

        // Validate the DpTable in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void getAllDpTables() throws Exception {
        // Initialize the database
        insertedDpTable = dpTableRepository.saveAndFlush(dpTable);

        // Get all the dpTableList
        restDpTableMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(dpTable.getId().intValue())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].schemaName").value(hasItem(DEFAULT_SCHEMA_NAME)))
            .andExpect(jsonPath("$.[*].description").value(hasItem(DEFAULT_DESCRIPTION)))
            .andExpect(jsonPath("$.[*].sourceDBType").value(hasItem(DEFAULT_SOURCE_DB_TYPE)))
            .andExpect(jsonPath("$.[*].sourceId").value(hasItem(DEFAULT_SOURCE_ID)))
            .andExpect(jsonPath("$.[*].sourceSchema").value(hasItem(DEFAULT_SOURCE_SCHEMA)))
            .andExpect(jsonPath("$.[*].sourceTable").value(hasItem(DEFAULT_SOURCE_TABLE)))
            .andExpect(jsonPath("$.[*].sqlContent").value(hasItem(DEFAULT_SQL_CONTENT)))
            .andExpect(jsonPath("$.[*].fileType").value(hasItem(DEFAULT_FILE_TYPE)))
            .andExpect(jsonPath("$.[*].filePath").value(hasItem(DEFAULT_FILE_PATH)))
            .andExpect(jsonPath("$.[*].updateTime").value(hasItem(sameInstant(DEFAULT_UPDATE_TIME))))
            .andExpect(jsonPath("$.[*].createTime").value(hasItem(sameInstant(DEFAULT_CREATE_TIME))))
            .andExpect(jsonPath("$.[*].tenantId").value(hasItem(DEFAULT_TENANT_ID)));
    }

    @Test
    @Transactional
    void getDpTable() throws Exception {
        // Initialize the database
        insertedDpTable = dpTableRepository.saveAndFlush(dpTable);

        // Get the dpTable
        restDpTableMockMvc
            .perform(get(ENTITY_API_URL_ID, dpTable.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(dpTable.getId().intValue()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.schemaName").value(DEFAULT_SCHEMA_NAME))
            .andExpect(jsonPath("$.description").value(DEFAULT_DESCRIPTION))
            .andExpect(jsonPath("$.sourceDBType").value(DEFAULT_SOURCE_DB_TYPE))
            .andExpect(jsonPath("$.sourceId").value(DEFAULT_SOURCE_ID))
            .andExpect(jsonPath("$.sourceSchema").value(DEFAULT_SOURCE_SCHEMA))
            .andExpect(jsonPath("$.sourceTable").value(DEFAULT_SOURCE_TABLE))
            .andExpect(jsonPath("$.sqlContent").value(DEFAULT_SQL_CONTENT))
            .andExpect(jsonPath("$.fileType").value(DEFAULT_FILE_TYPE))
            .andExpect(jsonPath("$.filePath").value(DEFAULT_FILE_PATH))
            .andExpect(jsonPath("$.updateTime").value(sameInstant(DEFAULT_UPDATE_TIME)))
            .andExpect(jsonPath("$.createTime").value(sameInstant(DEFAULT_CREATE_TIME)))
            .andExpect(jsonPath("$.tenantId").value(DEFAULT_TENANT_ID));
    }

    @Test
    @Transactional
    void getNonExistingDpTable() throws Exception {
        // Get the dpTable
        restDpTableMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingDpTable() throws Exception {
        // Initialize the database
        insertedDpTable = dpTableRepository.saveAndFlush(dpTable);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the dpTable
        DpTable updatedDpTable = dpTableRepository.findById(dpTable.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedDpTable are not directly saved in db
        em.detach(updatedDpTable);
        updatedDpTable
            .name(UPDATED_NAME)
            .schemaName(UPDATED_SCHEMA_NAME)
            .description(UPDATED_DESCRIPTION)
            .sourceDBType(UPDATED_SOURCE_DB_TYPE)
            .sourceId(UPDATED_SOURCE_ID)
            .sourceSchema(UPDATED_SOURCE_SCHEMA)
            .sourceTable(UPDATED_SOURCE_TABLE)
            .sqlContent(UPDATED_SQL_CONTENT)
            .fileType(UPDATED_FILE_TYPE)
            .filePath(UPDATED_FILE_PATH)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .tenantId(UPDATED_TENANT_ID);
        DpTableDTO dpTableDTO = dpTableMapper.toDto(updatedDpTable);

        restDpTableMockMvc
            .perform(
                put(ENTITY_API_URL_ID, dpTableDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dpTableDTO))
            )
            .andExpect(status().isOk());

        // Validate the DpTable in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedDpTableToMatchAllProperties(updatedDpTable);
    }

    @Test
    @Transactional
    void putNonExistingDpTable() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dpTable.setId(longCount.incrementAndGet());

        // Create the DpTable
        DpTableDTO dpTableDTO = dpTableMapper.toDto(dpTable);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restDpTableMockMvc
            .perform(
                put(ENTITY_API_URL_ID, dpTableDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dpTableDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DpTable in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchDpTable() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dpTable.setId(longCount.incrementAndGet());

        // Create the DpTable
        DpTableDTO dpTableDTO = dpTableMapper.toDto(dpTable);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDpTableMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(dpTableDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DpTable in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamDpTable() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dpTable.setId(longCount.incrementAndGet());

        // Create the DpTable
        DpTableDTO dpTableDTO = dpTableMapper.toDto(dpTable);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDpTableMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(dpTableDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the DpTable in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateDpTableWithPatch() throws Exception {
        // Initialize the database
        insertedDpTable = dpTableRepository.saveAndFlush(dpTable);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the dpTable using partial update
        DpTable partialUpdatedDpTable = new DpTable();
        partialUpdatedDpTable.setId(dpTable.getId());

        partialUpdatedDpTable
            .sourceId(UPDATED_SOURCE_ID)
            .sourceSchema(UPDATED_SOURCE_SCHEMA)
            .sourceTable(UPDATED_SOURCE_TABLE)
            .sqlContent(UPDATED_SQL_CONTENT)
            .fileType(UPDATED_FILE_TYPE)
            .tenantId(UPDATED_TENANT_ID);

        restDpTableMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedDpTable.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedDpTable))
            )
            .andExpect(status().isOk());

        // Validate the DpTable in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertDpTableUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedDpTable, dpTable), getPersistedDpTable(dpTable));
    }

    @Test
    @Transactional
    void fullUpdateDpTableWithPatch() throws Exception {
        // Initialize the database
        insertedDpTable = dpTableRepository.saveAndFlush(dpTable);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the dpTable using partial update
        DpTable partialUpdatedDpTable = new DpTable();
        partialUpdatedDpTable.setId(dpTable.getId());

        partialUpdatedDpTable
            .name(UPDATED_NAME)
            .schemaName(UPDATED_SCHEMA_NAME)
            .description(UPDATED_DESCRIPTION)
            .sourceDBType(UPDATED_SOURCE_DB_TYPE)
            .sourceId(UPDATED_SOURCE_ID)
            .sourceSchema(UPDATED_SOURCE_SCHEMA)
            .sourceTable(UPDATED_SOURCE_TABLE)
            .sqlContent(UPDATED_SQL_CONTENT)
            .fileType(UPDATED_FILE_TYPE)
            .filePath(UPDATED_FILE_PATH)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .tenantId(UPDATED_TENANT_ID);

        restDpTableMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedDpTable.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedDpTable))
            )
            .andExpect(status().isOk());

        // Validate the DpTable in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertDpTableUpdatableFieldsEquals(partialUpdatedDpTable, getPersistedDpTable(partialUpdatedDpTable));
    }

    @Test
    @Transactional
    void patchNonExistingDpTable() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dpTable.setId(longCount.incrementAndGet());

        // Create the DpTable
        DpTableDTO dpTableDTO = dpTableMapper.toDto(dpTable);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restDpTableMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, dpTableDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(dpTableDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DpTable in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchDpTable() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dpTable.setId(longCount.incrementAndGet());

        // Create the DpTable
        DpTableDTO dpTableDTO = dpTableMapper.toDto(dpTable);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDpTableMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(dpTableDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the DpTable in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamDpTable() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        dpTable.setId(longCount.incrementAndGet());

        // Create the DpTable
        DpTableDTO dpTableDTO = dpTableMapper.toDto(dpTable);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restDpTableMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(dpTableDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the DpTable in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteDpTable() throws Exception {
        // Initialize the database
        insertedDpTable = dpTableRepository.saveAndFlush(dpTable);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the dpTable
        restDpTableMockMvc
            .perform(delete(ENTITY_API_URL_ID, dpTable.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return dpTableRepository.count();
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

    protected DpTable getPersistedDpTable(DpTable dpTable) {
        return dpTableRepository.findById(dpTable.getId()).orElseThrow();
    }

    protected void assertPersistedDpTableToMatchAllProperties(DpTable expectedDpTable) {
        assertDpTableAllPropertiesEquals(expectedDpTable, getPersistedDpTable(expectedDpTable));
    }

    protected void assertPersistedDpTableToMatchUpdatableProperties(DpTable expectedDpTable) {
        assertDpTableAllUpdatablePropertiesEquals(expectedDpTable, getPersistedDpTable(expectedDpTable));
    }
}
