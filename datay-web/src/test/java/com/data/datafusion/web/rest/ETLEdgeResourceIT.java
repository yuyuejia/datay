package com.data.datafusion.web.rest;

import static com.data.datafusion.domain.ETLEdgeAsserts.*;
import static com.data.datafusion.web.rest.TestUtil.createUpdateProxyForBean;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.ETLEdge;
import com.data.datafusion.repository.ETLEdgeRepository;
import com.data.datafusion.service.dto.ETLEdgeDTO;
import com.data.datafusion.service.mapper.ETLEdgeMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
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
 * Integration tests for the {@link ETLEdgeResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class ETLEdgeResourceIT {

    private static final String DEFAULT_TASK_ID = "AAAAAAAAAA";
    private static final String UPDATED_TASK_ID = "BBBBBBBBBB";

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_CODE = "AAAAAAAAAA";
    private static final String UPDATED_CODE = "BBBBBBBBBB";

    private static final String DEFAULT_SOURCE = "AAAAAAAAAA";
    private static final String UPDATED_SOURCE = "BBBBBBBBBB";

    private static final String DEFAULT_TARGET = "AAAAAAAAAA";
    private static final String UPDATED_TARGET = "BBBBBBBBBB";

    private static final String DEFAULT_CONFIG = "AAAAAAAAAA";
    private static final String UPDATED_CONFIG = "BBBBBBBBBB";

    private static final String DEFAULT_STATUS = "AAAAAAAAAA";
    private static final String UPDATED_STATUS = "BBBBBBBBBB";

    private static final String DEFAULT_TENANT_ID = "AAAAAAAAAA";
    private static final String UPDATED_TENANT_ID = "BBBBBBBBBB";

    private static final Integer DEFAULT_DR = 1;
    private static final Integer UPDATED_DR = 2;

    private static final String ENTITY_API_URL = "/api/etl-edges";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ETLEdgeRepository eTLEdgeRepository;

    @Autowired
    private ETLEdgeMapper eTLEdgeMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restETLEdgeMockMvc;

    private ETLEdge eTLEdge;

    private ETLEdge insertedETLEdge;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ETLEdge createEntity() {
        return new ETLEdge()
            .taskId(DEFAULT_TASK_ID)
            .name(DEFAULT_NAME)
            .code(DEFAULT_CODE)
            .source(DEFAULT_SOURCE)
            .target(DEFAULT_TARGET)
            .config(DEFAULT_CONFIG)
            .status(DEFAULT_STATUS)
            .tenantId(DEFAULT_TENANT_ID)
            .dr(DEFAULT_DR);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ETLEdge createUpdatedEntity() {
        return new ETLEdge()
            .taskId(UPDATED_TASK_ID)
            .name(UPDATED_NAME)
            .code(UPDATED_CODE)
            .source(UPDATED_SOURCE)
            .target(UPDATED_TARGET)
            .config(UPDATED_CONFIG)
            .status(UPDATED_STATUS)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);
    }

    @BeforeEach
    void initTest() {
        eTLEdge = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedETLEdge != null) {
            eTLEdgeRepository.delete(insertedETLEdge);
            insertedETLEdge = null;
        }
    }

    @Test
    @Transactional
    void createETLEdge() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the ETLEdge
        ETLEdgeDTO eTLEdgeDTO = eTLEdgeMapper.toDto(eTLEdge);
        var returnedETLEdgeDTO = om.readValue(
            restETLEdgeMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(eTLEdgeDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ETLEdgeDTO.class
        );

        // Validate the ETLEdge in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedETLEdge = eTLEdgeMapper.toEntity(returnedETLEdgeDTO);
        assertETLEdgeUpdatableFieldsEquals(returnedETLEdge, getPersistedETLEdge(returnedETLEdge));

        insertedETLEdge = returnedETLEdge;
    }

    @Test
    @Transactional
    void createETLEdgeWithExistingId() throws Exception {
        // Create the ETLEdge with an existing ID
        eTLEdge.setId("1");
        ETLEdgeDTO eTLEdgeDTO = eTLEdgeMapper.toDto(eTLEdge);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restETLEdgeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(eTLEdgeDTO)))
            .andExpect(status().isBadRequest());

        // Validate the ETLEdge in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void getAllETLEdges() throws Exception {
        // Initialize the database
        insertedETLEdge = eTLEdgeRepository.saveAndFlush(eTLEdge);

        // Get all the eTLEdgeList
        restETLEdgeMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(eTLEdge.getId())))
            .andExpect(jsonPath("$.[*].taskId").value(hasItem(DEFAULT_TASK_ID)))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].code").value(hasItem(DEFAULT_CODE)))
            .andExpect(jsonPath("$.[*].source").value(hasItem(DEFAULT_SOURCE)))
            .andExpect(jsonPath("$.[*].target").value(hasItem(DEFAULT_TARGET)))
            .andExpect(jsonPath("$.[*].config").value(hasItem(DEFAULT_CONFIG)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS)))
            .andExpect(jsonPath("$.[*].tenantId").value(hasItem(DEFAULT_TENANT_ID)))
            .andExpect(jsonPath("$.[*].dr").value(hasItem(DEFAULT_DR)));
    }

    @Test
    @Transactional
    void getETLEdge() throws Exception {
        // Initialize the database
        insertedETLEdge = eTLEdgeRepository.saveAndFlush(eTLEdge);

        // Get the eTLEdge
        restETLEdgeMockMvc
            .perform(get(ENTITY_API_URL_ID, eTLEdge.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(eTLEdge.getId()))
            .andExpect(jsonPath("$.taskId").value(DEFAULT_TASK_ID))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.code").value(DEFAULT_CODE))
            .andExpect(jsonPath("$.source").value(DEFAULT_SOURCE))
            .andExpect(jsonPath("$.target").value(DEFAULT_TARGET))
            .andExpect(jsonPath("$.config").value(DEFAULT_CONFIG))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS))
            .andExpect(jsonPath("$.tenantId").value(DEFAULT_TENANT_ID))
            .andExpect(jsonPath("$.dr").value(DEFAULT_DR));
    }

    @Test
    @Transactional
    void getNonExistingETLEdge() throws Exception {
        // Get the eTLEdge
        restETLEdgeMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingETLEdge() throws Exception {
        // Initialize the database
        insertedETLEdge = eTLEdgeRepository.saveAndFlush(eTLEdge);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the eTLEdge
        ETLEdge updatedETLEdge = eTLEdgeRepository.findById(eTLEdge.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedETLEdge are not directly saved in db
        em.detach(updatedETLEdge);
        updatedETLEdge
            .taskId(UPDATED_TASK_ID)
            .name(UPDATED_NAME)
            .code(UPDATED_CODE)
            .source(UPDATED_SOURCE)
            .target(UPDATED_TARGET)
            .config(UPDATED_CONFIG)
            .status(UPDATED_STATUS)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);
        ETLEdgeDTO eTLEdgeDTO = eTLEdgeMapper.toDto(updatedETLEdge);

        restETLEdgeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, eTLEdgeDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(eTLEdgeDTO))
            )
            .andExpect(status().isOk());

        // Validate the ETLEdge in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedETLEdgeToMatchAllProperties(updatedETLEdge);
    }

    @Test
    @Transactional
    void putNonExistingETLEdge() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLEdge.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the ETLEdge
        ETLEdgeDTO eTLEdgeDTO = eTLEdgeMapper.toDto(eTLEdge);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restETLEdgeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, eTLEdgeDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(eTLEdgeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ETLEdge in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchETLEdge() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLEdge.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the ETLEdge
        ETLEdgeDTO eTLEdgeDTO = eTLEdgeMapper.toDto(eTLEdge);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restETLEdgeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(eTLEdgeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ETLEdge in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamETLEdge() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLEdge.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the ETLEdge
        ETLEdgeDTO eTLEdgeDTO = eTLEdgeMapper.toDto(eTLEdge);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restETLEdgeMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(eTLEdgeDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ETLEdge in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateETLEdgeWithPatch() throws Exception {
        // Initialize the database
        insertedETLEdge = eTLEdgeRepository.saveAndFlush(eTLEdge);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the eTLEdge using partial update
        ETLEdge partialUpdatedETLEdge = new ETLEdge();
        partialUpdatedETLEdge.setId(eTLEdge.getId());

        partialUpdatedETLEdge.code(UPDATED_CODE).source(UPDATED_SOURCE).target(UPDATED_TARGET).status(UPDATED_STATUS).dr(UPDATED_DR);

        restETLEdgeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedETLEdge.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedETLEdge))
            )
            .andExpect(status().isOk());

        // Validate the ETLEdge in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertETLEdgeUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedETLEdge, eTLEdge), getPersistedETLEdge(eTLEdge));
    }

    @Test
    @Transactional
    void fullUpdateETLEdgeWithPatch() throws Exception {
        // Initialize the database
        insertedETLEdge = eTLEdgeRepository.saveAndFlush(eTLEdge);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the eTLEdge using partial update
        ETLEdge partialUpdatedETLEdge = new ETLEdge();
        partialUpdatedETLEdge.setId(eTLEdge.getId());

        partialUpdatedETLEdge
            .taskId(UPDATED_TASK_ID)
            .name(UPDATED_NAME)
            .code(UPDATED_CODE)
            .source(UPDATED_SOURCE)
            .target(UPDATED_TARGET)
            .config(UPDATED_CONFIG)
            .status(UPDATED_STATUS)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);

        restETLEdgeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedETLEdge.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedETLEdge))
            )
            .andExpect(status().isOk());

        // Validate the ETLEdge in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertETLEdgeUpdatableFieldsEquals(partialUpdatedETLEdge, getPersistedETLEdge(partialUpdatedETLEdge));
    }

    @Test
    @Transactional
    void patchNonExistingETLEdge() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLEdge.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the ETLEdge
        ETLEdgeDTO eTLEdgeDTO = eTLEdgeMapper.toDto(eTLEdge);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restETLEdgeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, eTLEdgeDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(eTLEdgeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ETLEdge in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchETLEdge() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLEdge.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the ETLEdge
        ETLEdgeDTO eTLEdgeDTO = eTLEdgeMapper.toDto(eTLEdge);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restETLEdgeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(eTLEdgeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ETLEdge in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamETLEdge() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLEdge.setId(String.valueOf(longCount.incrementAndGet()));

        // Create the ETLEdge
        ETLEdgeDTO eTLEdgeDTO = eTLEdgeMapper.toDto(eTLEdge);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restETLEdgeMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(eTLEdgeDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ETLEdge in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteETLEdge() throws Exception {
        // Initialize the database
        insertedETLEdge = eTLEdgeRepository.saveAndFlush(eTLEdge);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the eTLEdge
        restETLEdgeMockMvc
            .perform(delete(ENTITY_API_URL_ID, eTLEdge.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return eTLEdgeRepository.count();
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

    protected ETLEdge getPersistedETLEdge(ETLEdge eTLEdge) {
        return eTLEdgeRepository.findById(eTLEdge.getId()).orElseThrow();
    }

    protected void assertPersistedETLEdgeToMatchAllProperties(ETLEdge expectedETLEdge) {
        assertETLEdgeAllPropertiesEquals(expectedETLEdge, getPersistedETLEdge(expectedETLEdge));
    }

    protected void assertPersistedETLEdgeToMatchUpdatableProperties(ETLEdge expectedETLEdge) {
        assertETLEdgeAllUpdatablePropertiesEquals(expectedETLEdge, getPersistedETLEdge(expectedETLEdge));
    }
}
