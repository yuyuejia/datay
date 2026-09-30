package com.data.datafusion.web.rest;

import static com.data.datafusion.domain.ServiceConfigAsserts.*;
import static com.data.datafusion.web.rest.TestUtil.createUpdateProxyForBean;
import static com.data.datafusion.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.ServiceConfig;
import com.data.datafusion.repository.ServiceConfigRepository;
import com.data.datafusion.service.dto.ServiceConfigDTO;
import com.data.datafusion.service.mapper.ServiceConfigMapper;
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
 * Integration tests for the {@link ServiceConfigResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser(authorities = "ROLE_ADMIN")
class ServiceConfigResourceIT {

    private static final String DEFAULT_DF_GROUP = "AAAAAAAAAA";
    private static final String UPDATED_DF_GROUP = "BBBBBBBBBB";

    private static final String DEFAULT_DF_KEY = "AAAAAAAAAA";
    private static final String UPDATED_DF_KEY = "BBBBBBBBBB";

    private static final String DEFAULT_DF_VALUE = "AAAAAAAAAA";
    private static final String UPDATED_DF_VALUE = "BBBBBBBBBB";

    private static final ZonedDateTime DEFAULT_CREATE_TIME = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_CREATE_TIME = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final String DEFAULT_YTENANT_ID = "AAAAAAAAAA";
    private static final String UPDATED_YTENANT_ID = "BBBBBBBBBB";

    private static final String ENTITY_API_URL = "/api/service-configs";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ServiceConfigRepository serviceConfigRepository;

    @Autowired
    private ServiceConfigMapper serviceConfigMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restServiceConfigMockMvc;

    private ServiceConfig serviceConfig;

    private ServiceConfig insertedServiceConfig;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ServiceConfig createEntity() {
        return new ServiceConfig()
            .dfGroup(DEFAULT_DF_GROUP)
            .dfKey(DEFAULT_DF_KEY)
            .dfValue(DEFAULT_DF_VALUE)
            .createTime(DEFAULT_CREATE_TIME)
            .tenantId(DEFAULT_YTENANT_ID);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ServiceConfig createUpdatedEntity() {
        return new ServiceConfig()
            .dfGroup(UPDATED_DF_GROUP)
            .dfKey(UPDATED_DF_KEY)
            .dfValue(UPDATED_DF_VALUE)
            .createTime(UPDATED_CREATE_TIME)
            .tenantId(UPDATED_YTENANT_ID);
    }

    @BeforeEach
    void initTest() {
        serviceConfig = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedServiceConfig != null) {
            serviceConfigRepository.delete(insertedServiceConfig);
            insertedServiceConfig = null;
        }
    }

    @Test
    @Transactional
    void createServiceConfig() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the ServiceConfig
        ServiceConfigDTO serviceConfigDTO = serviceConfigMapper.toDto(serviceConfig);
        var returnedServiceConfigDTO = om.readValue(
            restServiceConfigMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(serviceConfigDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ServiceConfigDTO.class
        );

        // Validate the ServiceConfig in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedServiceConfig = serviceConfigMapper.toEntity(returnedServiceConfigDTO);
        assertServiceConfigUpdatableFieldsEquals(returnedServiceConfig, getPersistedServiceConfig(returnedServiceConfig));

        insertedServiceConfig = returnedServiceConfig;
    }

    @Test
    @Transactional
    void createServiceConfigWithExistingId() throws Exception {
        // Create the ServiceConfig with an existing ID
        serviceConfig.setId(1L);
        ServiceConfigDTO serviceConfigDTO = serviceConfigMapper.toDto(serviceConfig);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restServiceConfigMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(serviceConfigDTO)))
            .andExpect(status().isBadRequest());

        // Validate the ServiceConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void getAllServiceConfigs() throws Exception {
        // Initialize the database
        insertedServiceConfig = serviceConfigRepository.saveAndFlush(serviceConfig);

        // Get all the serviceConfigList
        restServiceConfigMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(serviceConfig.getId().intValue())))
            .andExpect(jsonPath("$.[*].dfGroup").value(hasItem(DEFAULT_DF_GROUP)))
            .andExpect(jsonPath("$.[*].dfKey").value(hasItem(DEFAULT_DF_KEY)))
            .andExpect(jsonPath("$.[*].dfValue").value(hasItem(DEFAULT_DF_VALUE)))
            .andExpect(jsonPath("$.[*].createTime").value(hasItem(sameInstant(DEFAULT_CREATE_TIME))))
            .andExpect(jsonPath("$.[*].ytenantId").value(hasItem(DEFAULT_YTENANT_ID)));
    }

    @Test
    @Transactional
    void getServiceConfig() throws Exception {
        // Initialize the database
        insertedServiceConfig = serviceConfigRepository.saveAndFlush(serviceConfig);

        // Get the serviceConfig
        restServiceConfigMockMvc
            .perform(get(ENTITY_API_URL_ID, serviceConfig.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(serviceConfig.getId().intValue()))
            .andExpect(jsonPath("$.dfGroup").value(DEFAULT_DF_GROUP))
            .andExpect(jsonPath("$.dfKey").value(DEFAULT_DF_KEY))
            .andExpect(jsonPath("$.dfValue").value(DEFAULT_DF_VALUE))
            .andExpect(jsonPath("$.createTime").value(sameInstant(DEFAULT_CREATE_TIME)))
            .andExpect(jsonPath("$.ytenantId").value(DEFAULT_YTENANT_ID));
    }

    @Test
    @Transactional
    void getNonExistingServiceConfig() throws Exception {
        // Get the serviceConfig
        restServiceConfigMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingServiceConfig() throws Exception {
        // Initialize the database
        insertedServiceConfig = serviceConfigRepository.saveAndFlush(serviceConfig);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the serviceConfig
        ServiceConfig updatedServiceConfig = serviceConfigRepository.findById(serviceConfig.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedServiceConfig are not directly saved in db
        em.detach(updatedServiceConfig);
        updatedServiceConfig
            .dfGroup(UPDATED_DF_GROUP)
            .dfKey(UPDATED_DF_KEY)
            .dfValue(UPDATED_DF_VALUE)
            .createTime(UPDATED_CREATE_TIME)
            .tenantId(UPDATED_YTENANT_ID);
        ServiceConfigDTO serviceConfigDTO = serviceConfigMapper.toDto(updatedServiceConfig);

        restServiceConfigMockMvc
            .perform(
                put(ENTITY_API_URL_ID, serviceConfigDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(serviceConfigDTO))
            )
            .andExpect(status().isOk());

        // Validate the ServiceConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedServiceConfigToMatchAllProperties(updatedServiceConfig);
    }

    @Test
    @Transactional
    void putNonExistingServiceConfig() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        serviceConfig.setId(longCount.incrementAndGet());

        // Create the ServiceConfig
        ServiceConfigDTO serviceConfigDTO = serviceConfigMapper.toDto(serviceConfig);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restServiceConfigMockMvc
            .perform(
                put(ENTITY_API_URL_ID, serviceConfigDTO.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(serviceConfigDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ServiceConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchServiceConfig() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        serviceConfig.setId(longCount.incrementAndGet());

        // Create the ServiceConfig
        ServiceConfigDTO serviceConfigDTO = serviceConfigMapper.toDto(serviceConfig);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restServiceConfigMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(serviceConfigDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ServiceConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamServiceConfig() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        serviceConfig.setId(longCount.incrementAndGet());

        // Create the ServiceConfig
        ServiceConfigDTO serviceConfigDTO = serviceConfigMapper.toDto(serviceConfig);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restServiceConfigMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(serviceConfigDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ServiceConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateServiceConfigWithPatch() throws Exception {
        // Initialize the database
        insertedServiceConfig = serviceConfigRepository.saveAndFlush(serviceConfig);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the serviceConfig using partial update
        ServiceConfig partialUpdatedServiceConfig = new ServiceConfig();
        partialUpdatedServiceConfig.setId(serviceConfig.getId());

            partialUpdatedServiceConfig.dfKey(UPDATED_DF_KEY).tenantId(UPDATED_YTENANT_ID);

        restServiceConfigMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedServiceConfig.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedServiceConfig))
            )
            .andExpect(status().isOk());

        // Validate the ServiceConfig in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertServiceConfigUpdatableFieldsEquals(
            createUpdateProxyForBean(partialUpdatedServiceConfig, serviceConfig),
            getPersistedServiceConfig(serviceConfig)
        );
    }

    @Test
    @Transactional
    void fullUpdateServiceConfigWithPatch() throws Exception {
        // Initialize the database
        insertedServiceConfig = serviceConfigRepository.saveAndFlush(serviceConfig);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the serviceConfig using partial update
        ServiceConfig partialUpdatedServiceConfig = new ServiceConfig();
        partialUpdatedServiceConfig.setId(serviceConfig.getId());

        partialUpdatedServiceConfig
            .dfGroup(UPDATED_DF_GROUP)
            .dfKey(UPDATED_DF_KEY)
            .dfValue(UPDATED_DF_VALUE)
            .createTime(UPDATED_CREATE_TIME)
            .tenantId(UPDATED_YTENANT_ID);

        restServiceConfigMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedServiceConfig.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedServiceConfig))
            )
            .andExpect(status().isOk());

        // Validate the ServiceConfig in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertServiceConfigUpdatableFieldsEquals(partialUpdatedServiceConfig, getPersistedServiceConfig(partialUpdatedServiceConfig));
    }

    @Test
    @Transactional
    void patchNonExistingServiceConfig() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        serviceConfig.setId(longCount.incrementAndGet());

        // Create the ServiceConfig
        ServiceConfigDTO serviceConfigDTO = serviceConfigMapper.toDto(serviceConfig);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restServiceConfigMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, serviceConfigDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(serviceConfigDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ServiceConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchServiceConfig() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        serviceConfig.setId(longCount.incrementAndGet());

        // Create the ServiceConfig
        ServiceConfigDTO serviceConfigDTO = serviceConfigMapper.toDto(serviceConfig);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restServiceConfigMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(serviceConfigDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ServiceConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamServiceConfig() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        serviceConfig.setId(longCount.incrementAndGet());

        // Create the ServiceConfig
        ServiceConfigDTO serviceConfigDTO = serviceConfigMapper.toDto(serviceConfig);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restServiceConfigMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(serviceConfigDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ServiceConfig in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteServiceConfig() throws Exception {
        // Initialize the database
        insertedServiceConfig = serviceConfigRepository.saveAndFlush(serviceConfig);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the serviceConfig
        restServiceConfigMockMvc
            .perform(delete(ENTITY_API_URL_ID, serviceConfig.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return serviceConfigRepository.count();
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

    protected ServiceConfig getPersistedServiceConfig(ServiceConfig serviceConfig) {
        return serviceConfigRepository.findById(serviceConfig.getId()).orElseThrow();
    }

    protected void assertPersistedServiceConfigToMatchAllProperties(ServiceConfig expectedServiceConfig) {
        assertServiceConfigAllPropertiesEquals(expectedServiceConfig, getPersistedServiceConfig(expectedServiceConfig));
    }

    protected void assertPersistedServiceConfigToMatchUpdatableProperties(ServiceConfig expectedServiceConfig) {
        assertServiceConfigAllUpdatablePropertiesEquals(expectedServiceConfig, getPersistedServiceConfig(expectedServiceConfig));
    }
}
