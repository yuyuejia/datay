package com.data.datafusion.web.rest;

import static com.data.datafusion.domain.ETLComponentAsserts.*;
import static com.data.datafusion.web.rest.TestUtil.createUpdateProxyForBean;
import static com.data.datafusion.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.ETLComponent;
import com.data.datafusion.repository.ETLComponentRepository;
import com.data.datafusion.service.dto.ETLComponentDTO;
import com.data.datafusion.service.mapper.ETLComponentMapper;
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
 * Integration tests for the {@link ETLComponentResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class ETLComponentResourceIT {

    private static final String DEFAULT_NAME = "AAAAAAAAAA";
    private static final String UPDATED_NAME = "BBBBBBBBBB";

    private static final String DEFAULT_CODE = "AAAAAAAAAA";
    private static final String UPDATED_CODE = "BBBBBBBBBB";

    private static final String DEFAULT_DESC = "AAAAAAAAAA";
    private static final String UPDATED_DESC = "BBBBBBBBBB";

    private static final String DEFAULT_GROUP = "AAAAAAAAAA";
    private static final String UPDATED_GROUP = "BBBBBBBBBB";

    private static final String DEFAULT_TYPE = "AAAAAAAAAA";
    private static final String UPDATED_TYPE = "BBBBBBBBBB";

    private static final String DEFAULT_CONFIG = "AAAAAAAAAA";
    private static final String UPDATED_CONFIG = "BBBBBBBBBB";

    private static final String DEFAULT_STATUS = "AAAAAAAAAA";
    private static final String UPDATED_STATUS = "BBBBBBBBBB";

    private static final ZonedDateTime DEFAULT_UPDATE_TIME = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_UPDATE_TIME = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final ZonedDateTime DEFAULT_CREATE_TIME = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_CREATE_TIME = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final String DEFAULT_CREATER = "AAAAAAAAAA";
    private static final String UPDATED_CREATER = "BBBBBBBBBB";

    private static final String DEFAULT_TENANT_ID = "AAAAAAAAAA";
    private static final String UPDATED_TENANT_ID = "BBBBBBBBBB";

    private static final Integer DEFAULT_DR = 1;
    private static final Integer UPDATED_DR = 2;

    private static final String ENTITY_API_URL = "/api/etl-components";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ETLComponentRepository eTLComponentRepository;

    @Autowired
    private ETLComponentMapper eTLComponentMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restETLComponentMockMvc;

    private ETLComponent eTLComponent;

    private ETLComponent insertedETLComponent;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ETLComponent createEntity() {
        return new ETLComponent()
            .name(DEFAULT_NAME)
            .code(DEFAULT_CODE)
            .desc(DEFAULT_DESC)
            .group(DEFAULT_GROUP)
            .type(DEFAULT_TYPE)
            .config(DEFAULT_CONFIG)
            .status(DEFAULT_STATUS)
            .updateTime(DEFAULT_UPDATE_TIME)
            .createTime(DEFAULT_CREATE_TIME)
            .creater(DEFAULT_CREATER)
            .tenantId(DEFAULT_TENANT_ID)
            .dr(DEFAULT_DR);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ETLComponent createUpdatedEntity() {
        return new ETLComponent()
            .name(UPDATED_NAME)
            .code(UPDATED_CODE)
            .desc(UPDATED_DESC)
            .group(UPDATED_GROUP)
            .type(UPDATED_TYPE)
            .config(UPDATED_CONFIG)
            .status(UPDATED_STATUS)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .creater(UPDATED_CREATER)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);
    }

    @BeforeEach
    void initTest() {
        eTLComponent = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedETLComponent != null) {
            eTLComponentRepository.delete(insertedETLComponent);
            insertedETLComponent = null;
        }
    }

    @Test
    @Transactional
    void createETLComponent() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the ETLComponent
        ETLComponentDTO eTLComponentDTO = eTLComponentMapper.toDto(eTLComponent);
        var returnedETLComponentDTO = om.readValue(
            restETLComponentMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(eTLComponentDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ETLComponentDTO.class
        );

        // Validate the ETLComponent in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedETLComponent = eTLComponentMapper.toEntity(returnedETLComponentDTO);
        assertETLComponentUpdatableFieldsEquals(returnedETLComponent, getPersistedETLComponent(returnedETLComponent));

        insertedETLComponent = returnedETLComponent;
    }

    @Test
    @Transactional
    void createETLComponentWithExistingId() throws Exception {
        // Create the ETLComponent with an existing ID
        eTLComponent.setId(1L);
        ETLComponentDTO eTLComponentDTO = eTLComponentMapper.toDto(eTLComponent);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restETLComponentMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(eTLComponentDTO)))
            .andExpect(status().isBadRequest());

        // Validate the ETLComponent in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void getAllETLComponents() throws Exception {
        // Initialize the database
        insertedETLComponent = eTLComponentRepository.saveAndFlush(eTLComponent);

        // Get all the eTLComponentList
        restETLComponentMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(eTLComponent.getId().intValue())))
            .andExpect(jsonPath("$.[*].name").value(hasItem(DEFAULT_NAME)))
            .andExpect(jsonPath("$.[*].code").value(hasItem(DEFAULT_CODE)))
            .andExpect(jsonPath("$.[*].desc").value(hasItem(DEFAULT_DESC)))
            .andExpect(jsonPath("$.[*].group").value(hasItem(DEFAULT_GROUP)))
            .andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE)))
            .andExpect(jsonPath("$.[*].config").value(hasItem(DEFAULT_CONFIG)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS)))
            .andExpect(jsonPath("$.[*].updateTime").value(hasItem(sameInstant(DEFAULT_UPDATE_TIME))))
            .andExpect(jsonPath("$.[*].createTime").value(hasItem(sameInstant(DEFAULT_CREATE_TIME))))
            .andExpect(jsonPath("$.[*].creater").value(hasItem(DEFAULT_CREATER)))
            .andExpect(jsonPath("$.[*].tenantId").value(hasItem(DEFAULT_TENANT_ID)))
            .andExpect(jsonPath("$.[*].dr").value(hasItem(DEFAULT_DR)));
    }

    @Test
    @Transactional
    void getETLComponent() throws Exception {
        // Initialize the database
        insertedETLComponent = eTLComponentRepository.saveAndFlush(eTLComponent);

        // Get the eTLComponent
        restETLComponentMockMvc
            .perform(get(ENTITY_API_URL_ID, eTLComponent.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(eTLComponent.getId().intValue()))
            .andExpect(jsonPath("$.name").value(DEFAULT_NAME))
            .andExpect(jsonPath("$.code").value(DEFAULT_CODE))
            .andExpect(jsonPath("$.desc").value(DEFAULT_DESC))
            .andExpect(jsonPath("$.group").value(DEFAULT_GROUP))
            .andExpect(jsonPath("$.type").value(DEFAULT_TYPE))
            .andExpect(jsonPath("$.config").value(DEFAULT_CONFIG))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS))
            .andExpect(jsonPath("$.updateTime").value(sameInstant(DEFAULT_UPDATE_TIME)))
            .andExpect(jsonPath("$.createTime").value(sameInstant(DEFAULT_CREATE_TIME)))
            .andExpect(jsonPath("$.creater").value(DEFAULT_CREATER))
            .andExpect(jsonPath("$.tenantId").value(DEFAULT_TENANT_ID))
            .andExpect(jsonPath("$.dr").value(DEFAULT_DR));
    }

    @Test
    @Transactional
    void getNonExistingETLComponent() throws Exception {
        // Get the eTLComponent
        restETLComponentMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingETLComponent() throws Exception {
        // Initialize the database
        insertedETLComponent = eTLComponentRepository.saveAndFlush(eTLComponent);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the eTLComponent
        ETLComponent updatedETLComponent = eTLComponentRepository.findById(eTLComponent.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedETLComponent are not directly saved in db
        em.detach(updatedETLComponent);
        updatedETLComponent
            .name(UPDATED_NAME)
            .code(UPDATED_CODE)
            .desc(UPDATED_DESC)
            .group(UPDATED_GROUP)
            .type(UPDATED_TYPE)
            .config(UPDATED_CONFIG)
            .status(UPDATED_STATUS)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .creater(UPDATED_CREATER)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);
        ETLComponentDTO eTLComponentDTO = eTLComponentMapper.toDto(updatedETLComponent);

        restETLComponentMockMvc
            .perform(
                put(ENTITY_API_URL_ID, eTLComponentDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(eTLComponentDTO))
            )
            .andExpect(status().isOk());

        // Validate the ETLComponent in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedETLComponentToMatchAllProperties(updatedETLComponent);
    }

    @Test
    @Transactional
    void putNonExistingETLComponent() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLComponent.setId(longCount.incrementAndGet());

        // Create the ETLComponent
        ETLComponentDTO eTLComponentDTO = eTLComponentMapper.toDto(eTLComponent);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restETLComponentMockMvc
            .perform(
                put(ENTITY_API_URL_ID, eTLComponentDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(eTLComponentDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ETLComponent in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchETLComponent() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLComponent.setId(longCount.incrementAndGet());

        // Create the ETLComponent
        ETLComponentDTO eTLComponentDTO = eTLComponentMapper.toDto(eTLComponent);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restETLComponentMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(eTLComponentDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ETLComponent in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamETLComponent() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLComponent.setId(longCount.incrementAndGet());

        // Create the ETLComponent
        ETLComponentDTO eTLComponentDTO = eTLComponentMapper.toDto(eTLComponent);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restETLComponentMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(eTLComponentDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ETLComponent in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateETLComponentWithPatch() throws Exception {
        // Initialize the database
        insertedETLComponent = eTLComponentRepository.saveAndFlush(eTLComponent);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the eTLComponent using partial update
        ETLComponent partialUpdatedETLComponent = new ETLComponent();
        partialUpdatedETLComponent.setId(eTLComponent.getId());

        partialUpdatedETLComponent
            .name(UPDATED_NAME)
            .desc(UPDATED_DESC)
            .status(UPDATED_STATUS)
            .creater(UPDATED_CREATER)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);

        restETLComponentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedETLComponent.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedETLComponent))
            )
            .andExpect(status().isOk());

        // Validate the ETLComponent in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertETLComponentUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedETLComponent, eTLComponent),
            getPersistedETLComponent(eTLComponent)
        );
    }

    @Test
    @Transactional
    void fullUpdateETLComponentWithPatch() throws Exception {
        // Initialize the database
        insertedETLComponent = eTLComponentRepository.saveAndFlush(eTLComponent);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the eTLComponent using partial update
        ETLComponent partialUpdatedETLComponent = new ETLComponent();
        partialUpdatedETLComponent.setId(eTLComponent.getId());

        partialUpdatedETLComponent
            .name(UPDATED_NAME)
            .code(UPDATED_CODE)
            .desc(UPDATED_DESC)
            .group(UPDATED_GROUP)
            .type(UPDATED_TYPE)
            .config(UPDATED_CONFIG)
            .status(UPDATED_STATUS)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .creater(UPDATED_CREATER)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);

        restETLComponentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedETLComponent.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedETLComponent))
            )
            .andExpect(status().isOk());

        // Validate the ETLComponent in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertETLComponentUpdatableFieldsEquals(partialUpdatedETLComponent, getPersistedETLComponent(partialUpdatedETLComponent));
    }

    @Test
    @Transactional
    void patchNonExistingETLComponent() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLComponent.setId(longCount.incrementAndGet());

        // Create the ETLComponent
        ETLComponentDTO eTLComponentDTO = eTLComponentMapper.toDto(eTLComponent);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restETLComponentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, eTLComponentDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(eTLComponentDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ETLComponent in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchETLComponent() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLComponent.setId(longCount.incrementAndGet());

        // Create the ETLComponent
        ETLComponentDTO eTLComponentDTO = eTLComponentMapper.toDto(eTLComponent);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restETLComponentMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(eTLComponentDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ETLComponent in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamETLComponent() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLComponent.setId(longCount.incrementAndGet());

        // Create the ETLComponent
        ETLComponentDTO eTLComponentDTO = eTLComponentMapper.toDto(eTLComponent);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restETLComponentMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(eTLComponentDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ETLComponent in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteETLComponent() throws Exception {
        // Initialize the database
        insertedETLComponent = eTLComponentRepository.saveAndFlush(eTLComponent);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the eTLComponent
        restETLComponentMockMvc
            .perform(delete(ENTITY_API_URL_ID, eTLComponent.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return eTLComponentRepository.count();
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

    protected ETLComponent getPersistedETLComponent(ETLComponent eTLComponent) {
        return eTLComponentRepository.findById(eTLComponent.getId()).orElseThrow();
    }

    protected void assertPersistedETLComponentToMatchAllProperties(ETLComponent expectedETLComponent) {
        assertETLComponentAllPropertiesEquals(expectedETLComponent, getPersistedETLComponent(expectedETLComponent));
    }

    protected void assertPersistedETLComponentToMatchUpdatableProperties(ETLComponent expectedETLComponent) {
        assertETLComponentAllUpdatablePropertiesEquals(expectedETLComponent, getPersistedETLComponent(expectedETLComponent));
    }
}
