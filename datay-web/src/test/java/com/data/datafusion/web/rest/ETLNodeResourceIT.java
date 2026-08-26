package com.data.datafusion.web.rest;

import static com.data.datafusion.domain.ETLNodeAsserts.*;
import static com.data.datafusion.web.rest.TestUtil.createUpdateProxyForBean;
import static com.data.datafusion.web.rest.TestUtil.sameInstant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.ETLNode;
import com.data.datafusion.repository.ETLNodeRepository;
import com.data.datafusion.service.dto.ETLNodeDTO;
import com.data.datafusion.service.mapper.ETLNodeMapper;
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
 * Integration tests for the {@link ETLNodeResource} REST controller.
 */
@IntegrationTest
@AutoConfigureMockMvc
@WithMockUser
class ETLNodeResourceIT {

    private static final String DEFAULT_TASK_ID = "AAAAAAAAAA";
    private static final String UPDATED_TASK_ID = "BBBBBBBBBB";

    private static final String DEFAULT_LABEL = "AAAAAAAAAA";
    private static final String UPDATED_LABEL = "BBBBBBBBBB";

    private static final String DEFAULT_CODE = "AAAAAAAAAA";
    private static final String UPDATED_CODE = "BBBBBBBBBB";

    private static final String DEFAULT_DESC = "AAAAAAAAAA";
    private static final String UPDATED_DESC = "BBBBBBBBBB";

    private static final String DEFAULT_TYPE = "AAAAAAAAAA";
    private static final String UPDATED_TYPE = "BBBBBBBBBB";

    private static final String DEFAULT_CONFIG = "AAAAAAAAAA";
    private static final String UPDATED_CONFIG = "BBBBBBBBBB";

    private static final String DEFAULT_X_AXIS = "AAAAAAAAAA";
    private static final String UPDATED_X_AXIS = "BBBBBBBBBB";

    private static final String DEFAULT_Y_AXIS = "AAAAAAAAAA";
    private static final String UPDATED_Y_AXIS = "BBBBBBBBBB";

    private static final String DEFAULT_STATUS = "AAAAAAAAAA";
    private static final String UPDATED_STATUS = "BBBBBBBBBB";

    private static final ZonedDateTime DEFAULT_UPDATE_TIME = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_UPDATE_TIME = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final ZonedDateTime DEFAULT_CREATE_TIME = ZonedDateTime.ofInstant(Instant.ofEpochMilli(0L), ZoneOffset.UTC);
    private static final ZonedDateTime UPDATED_CREATE_TIME = ZonedDateTime.now(ZoneId.systemDefault()).withNano(0);

    private static final String DEFAULT_TENANT_ID = "AAAAAAAAAA";
    private static final String UPDATED_TENANT_ID = "BBBBBBBBBB";

    private static final Integer DEFAULT_DR = 1;
    private static final Integer UPDATED_DR = 2;

    private static final String ENTITY_API_URL = "/api/etl-nodes";
    private static final String ENTITY_API_URL_ID = ENTITY_API_URL + "/{id}";

    private static Random random = new Random();
    private static AtomicLong longCount = new AtomicLong(random.nextInt() + (2 * Integer.MAX_VALUE));

    @Autowired
    private ObjectMapper om;

    @Autowired
    private ETLNodeRepository eTLNodeRepository;

    @Autowired
    private ETLNodeMapper eTLNodeMapper;

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc restETLNodeMockMvc;

    private ETLNode eTLNode;

    private ETLNode insertedETLNode;

    /**
     * Create an entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ETLNode createEntity() {
        return new ETLNode()
            .taskId(DEFAULT_TASK_ID)
            .label(DEFAULT_LABEL)
            .code(DEFAULT_CODE)
            .desc(DEFAULT_DESC)
            .type(DEFAULT_TYPE)
            .config(DEFAULT_CONFIG)
            .xAxis(DEFAULT_X_AXIS)
            .yAxis(DEFAULT_Y_AXIS)
            .status(DEFAULT_STATUS)
            .updateTime(DEFAULT_UPDATE_TIME)
            .createTime(DEFAULT_CREATE_TIME)
            .tenantId(DEFAULT_TENANT_ID)
            .dr(DEFAULT_DR);
    }

    /**
     * Create an updated entity for this test.
     *
     * This is a static method, as tests for other entities might also need it,
     * if they test an entity which requires the current entity.
     */
    public static ETLNode createUpdatedEntity() {
        return new ETLNode()
            .taskId(UPDATED_TASK_ID)
            .label(UPDATED_LABEL)
            .code(UPDATED_CODE)
            .desc(UPDATED_DESC)
            .type(UPDATED_TYPE)
            .config(UPDATED_CONFIG)
            .xAxis(UPDATED_X_AXIS)
            .yAxis(UPDATED_Y_AXIS)
            .status(UPDATED_STATUS)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);
    }

    @BeforeEach
    void initTest() {
        eTLNode = createEntity();
    }

    @AfterEach
    void cleanup() {
        if (insertedETLNode != null) {
            eTLNodeRepository.delete(insertedETLNode);
            insertedETLNode = null;
        }
    }

    @Test
    @Transactional
    void createETLNode() throws Exception {
        long databaseSizeBeforeCreate = getRepositoryCount();
        // Create the ETLNode
        ETLNodeDTO eTLNodeDTO = eTLNodeMapper.toDto(eTLNode);
        var returnedETLNodeDTO = om.readValue(
            restETLNodeMockMvc
                .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(eTLNodeDTO)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            ETLNodeDTO.class
        );

        // Validate the ETLNode in the database
        assertIncrementedRepositoryCount(databaseSizeBeforeCreate);
        var returnedETLNode = eTLNodeMapper.toEntity(returnedETLNodeDTO);
        assertETLNodeUpdatableFieldsEquals(returnedETLNode, getPersistedETLNode(returnedETLNode));

        insertedETLNode = returnedETLNode;
    }

    @Test
    @Transactional
    void createETLNodeWithExistingId() throws Exception {
        // Create the ETLNode with an existing ID
        eTLNode.setId(1L);
        ETLNodeDTO eTLNodeDTO = eTLNodeMapper.toDto(eTLNode);

        long databaseSizeBeforeCreate = getRepositoryCount();

        // An entity with an existing ID cannot be created, so this API call must fail
        restETLNodeMockMvc
            .perform(post(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(eTLNodeDTO)))
            .andExpect(status().isBadRequest());

        // Validate the ETLNode in the database
        assertSameRepositoryCount(databaseSizeBeforeCreate);
    }

    @Test
    @Transactional
    void getAllETLNodes() throws Exception {
        // Initialize the database
        insertedETLNode = eTLNodeRepository.saveAndFlush(eTLNode);

        // Get all the eTLNodeList
        restETLNodeMockMvc
            .perform(get(ENTITY_API_URL + "?sort=id,desc"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.[*].id").value(hasItem(eTLNode.getId().intValue())))
            .andExpect(jsonPath("$.[*].taskId").value(hasItem(DEFAULT_TASK_ID)))
            .andExpect(jsonPath("$.[*].label").value(hasItem(DEFAULT_LABEL)))
            .andExpect(jsonPath("$.[*].code").value(hasItem(DEFAULT_CODE)))
            .andExpect(jsonPath("$.[*].desc").value(hasItem(DEFAULT_DESC)))
            .andExpect(jsonPath("$.[*].type").value(hasItem(DEFAULT_TYPE)))
            .andExpect(jsonPath("$.[*].config").value(hasItem(DEFAULT_CONFIG)))
            .andExpect(jsonPath("$.[*].xAxis").value(hasItem(DEFAULT_X_AXIS)))
            .andExpect(jsonPath("$.[*].yAxis").value(hasItem(DEFAULT_Y_AXIS)))
            .andExpect(jsonPath("$.[*].status").value(hasItem(DEFAULT_STATUS)))
            .andExpect(jsonPath("$.[*].updateTime").value(hasItem(sameInstant(DEFAULT_UPDATE_TIME))))
            .andExpect(jsonPath("$.[*].createTime").value(hasItem(sameInstant(DEFAULT_CREATE_TIME))))
            .andExpect(jsonPath("$.[*].tenantId").value(hasItem(DEFAULT_TENANT_ID)))
            .andExpect(jsonPath("$.[*].dr").value(hasItem(DEFAULT_DR)));
    }

    @Test
    @Transactional
    void getETLNode() throws Exception {
        // Initialize the database
        insertedETLNode = eTLNodeRepository.saveAndFlush(eTLNode);

        // Get the eTLNode
        restETLNodeMockMvc
            .perform(get(ENTITY_API_URL_ID, eTLNode.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(eTLNode.getId().intValue()))
            .andExpect(jsonPath("$.taskId").value(DEFAULT_TASK_ID))
            .andExpect(jsonPath("$.label").value(DEFAULT_LABEL))
            .andExpect(jsonPath("$.code").value(DEFAULT_CODE))
            .andExpect(jsonPath("$.desc").value(DEFAULT_DESC))
            .andExpect(jsonPath("$.type").value(DEFAULT_TYPE))
            .andExpect(jsonPath("$.config").value(DEFAULT_CONFIG))
            .andExpect(jsonPath("$.xAxis").value(DEFAULT_X_AXIS))
            .andExpect(jsonPath("$.yAxis").value(DEFAULT_Y_AXIS))
            .andExpect(jsonPath("$.status").value(DEFAULT_STATUS))
            .andExpect(jsonPath("$.updateTime").value(sameInstant(DEFAULT_UPDATE_TIME)))
            .andExpect(jsonPath("$.createTime").value(sameInstant(DEFAULT_CREATE_TIME)))
            .andExpect(jsonPath("$.tenantId").value(DEFAULT_TENANT_ID))
            .andExpect(jsonPath("$.dr").value(DEFAULT_DR));
    }

    @Test
    @Transactional
    void getNonExistingETLNode() throws Exception {
        // Get the eTLNode
        restETLNodeMockMvc.perform(get(ENTITY_API_URL_ID, Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void putExistingETLNode() throws Exception {
        // Initialize the database
        insertedETLNode = eTLNodeRepository.saveAndFlush(eTLNode);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the eTLNode
        ETLNode updatedETLNode = eTLNodeRepository.findById(eTLNode.getId()).orElseThrow();
        // Disconnect from session so that the updates on updatedETLNode are not directly saved in db
        em.detach(updatedETLNode);
        updatedETLNode
            .taskId(UPDATED_TASK_ID)
            .label(UPDATED_LABEL)
            .code(UPDATED_CODE)
            .desc(UPDATED_DESC)
            .type(UPDATED_TYPE)
            .config(UPDATED_CONFIG)
            .xAxis(UPDATED_X_AXIS)
            .yAxis(UPDATED_Y_AXIS)
            .status(UPDATED_STATUS)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);
        ETLNodeDTO eTLNodeDTO = eTLNodeMapper.toDto(updatedETLNode);

        restETLNodeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, eTLNodeDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(eTLNodeDTO))
            )
            .andExpect(status().isOk());

        // Validate the ETLNode in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertPersistedETLNodeToMatchAllProperties(updatedETLNode);
    }

    @Test
    @Transactional
    void putNonExistingETLNode() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLNode.setId(longCount.incrementAndGet());

        // Create the ETLNode
        ETLNodeDTO eTLNodeDTO = eTLNodeMapper.toDto(eTLNode);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restETLNodeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, eTLNodeDTO.getId()).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(eTLNodeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ETLNode in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithIdMismatchETLNode() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLNode.setId(longCount.incrementAndGet());

        // Create the ETLNode
        ETLNodeDTO eTLNodeDTO = eTLNodeMapper.toDto(eTLNode);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restETLNodeMockMvc
            .perform(
                put(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsBytes(eTLNodeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ETLNode in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void putWithMissingIdPathParamETLNode() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLNode.setId(longCount.incrementAndGet());

        // Create the ETLNode
        ETLNodeDTO eTLNodeDTO = eTLNodeMapper.toDto(eTLNode);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restETLNodeMockMvc
            .perform(put(ENTITY_API_URL).contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(eTLNodeDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ETLNode in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void partialUpdateETLNodeWithPatch() throws Exception {
        // Initialize the database
        insertedETLNode = eTLNodeRepository.saveAndFlush(eTLNode);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the eTLNode using partial update
        ETLNode partialUpdatedETLNode = new ETLNode();
        partialUpdatedETLNode.setId(eTLNode.getId());

        partialUpdatedETLNode
            .taskId(UPDATED_TASK_ID)
            .label(UPDATED_LABEL)
            .status(UPDATED_STATUS)
            .createTime(UPDATED_CREATE_TIME)
            .dr(UPDATED_DR);

        restETLNodeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedETLNode.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedETLNode))
            )
            .andExpect(status().isOk());

        // Validate the ETLNode in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertETLNodeUpdatableFieldsEquals(createUpdateProxyForBean(partialUpdatedETLNode, eTLNode), getPersistedETLNode(eTLNode));
    }

    @Test
    @Transactional
    void fullUpdateETLNodeWithPatch() throws Exception {
        // Initialize the database
        insertedETLNode = eTLNodeRepository.saveAndFlush(eTLNode);

        long databaseSizeBeforeUpdate = getRepositoryCount();

        // Update the eTLNode using partial update
        ETLNode partialUpdatedETLNode = new ETLNode();
        partialUpdatedETLNode.setId(eTLNode.getId());

        partialUpdatedETLNode
            .taskId(UPDATED_TASK_ID)
            .label(UPDATED_LABEL)
            .code(UPDATED_CODE)
            .desc(UPDATED_DESC)
            .type(UPDATED_TYPE)
            .config(UPDATED_CONFIG)
            .xAxis(UPDATED_X_AXIS)
            .yAxis(UPDATED_Y_AXIS)
            .status(UPDATED_STATUS)
            .updateTime(UPDATED_UPDATE_TIME)
            .createTime(UPDATED_CREATE_TIME)
            .tenantId(UPDATED_TENANT_ID)
            .dr(UPDATED_DR);

        restETLNodeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, partialUpdatedETLNode.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(partialUpdatedETLNode))
            )
            .andExpect(status().isOk());

        // Validate the ETLNode in the database

        assertSameRepositoryCount(databaseSizeBeforeUpdate);
        assertETLNodeUpdatableFieldsEquals(partialUpdatedETLNode, getPersistedETLNode(partialUpdatedETLNode));
    }

    @Test
    @Transactional
    void patchNonExistingETLNode() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLNode.setId(longCount.incrementAndGet());

        // Create the ETLNode
        ETLNodeDTO eTLNodeDTO = eTLNodeMapper.toDto(eTLNode);

        // If the entity doesn't have an ID, it will throw BadRequestAlertException
        restETLNodeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, eTLNodeDTO.getId())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(eTLNodeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ETLNode in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithIdMismatchETLNode() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLNode.setId(longCount.incrementAndGet());

        // Create the ETLNode
        ETLNodeDTO eTLNodeDTO = eTLNodeMapper.toDto(eTLNode);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restETLNodeMockMvc
            .perform(
                patch(ENTITY_API_URL_ID, longCount.incrementAndGet())
                    .contentType("application/merge-patch+json")
                    .content(om.writeValueAsBytes(eTLNodeDTO))
            )
            .andExpect(status().isBadRequest());

        // Validate the ETLNode in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void patchWithMissingIdPathParamETLNode() throws Exception {
        long databaseSizeBeforeUpdate = getRepositoryCount();
        eTLNode.setId(longCount.incrementAndGet());

        // Create the ETLNode
        ETLNodeDTO eTLNodeDTO = eTLNodeMapper.toDto(eTLNode);

        // If url ID doesn't match entity ID, it will throw BadRequestAlertException
        restETLNodeMockMvc
            .perform(patch(ENTITY_API_URL).contentType("application/merge-patch+json").content(om.writeValueAsBytes(eTLNodeDTO)))
            .andExpect(status().isMethodNotAllowed());

        // Validate the ETLNode in the database
        assertSameRepositoryCount(databaseSizeBeforeUpdate);
    }

    @Test
    @Transactional
    void deleteETLNode() throws Exception {
        // Initialize the database
        insertedETLNode = eTLNodeRepository.saveAndFlush(eTLNode);

        long databaseSizeBeforeDelete = getRepositoryCount();

        // Delete the eTLNode
        restETLNodeMockMvc
            .perform(delete(ENTITY_API_URL_ID, eTLNode.getId()).accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isNoContent());

        // Validate the database contains one less item
        assertDecrementedRepositoryCount(databaseSizeBeforeDelete);
    }

    protected long getRepositoryCount() {
        return eTLNodeRepository.count();
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

    protected ETLNode getPersistedETLNode(ETLNode eTLNode) {
        return eTLNodeRepository.findById(eTLNode.getId()).orElseThrow();
    }

    protected void assertPersistedETLNodeToMatchAllProperties(ETLNode expectedETLNode) {
        assertETLNodeAllPropertiesEquals(expectedETLNode, getPersistedETLNode(expectedETLNode));
    }

    protected void assertPersistedETLNodeToMatchUpdatableProperties(ETLNode expectedETLNode) {
        assertETLNodeAllUpdatablePropertiesEquals(expectedETLNode, getPersistedETLNode(expectedETLNode));
    }
}
