package com.data.datafusion.service;

import com.data.datafusion.domain.DataSyncTableConfig;
import com.data.datafusion.repository.DataSyncTableConfigRepository;
import com.data.datafusion.service.dto.DataSyncTableConfigDTO;
import com.data.datafusion.service.mapper.DataSyncTableConfigMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.data.datafusion.domain.DataSyncTableConfig}.
 */
@Service
@Transactional
public class DataSyncTableConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(DataSyncTableConfigService.class);

    private final DataSyncTableConfigRepository dataSyncTableConfigRepository;

    private final DataSyncTableConfigMapper dataSyncTableConfigMapper;

    public DataSyncTableConfigService(
        DataSyncTableConfigRepository dataSyncTableConfigRepository,
        DataSyncTableConfigMapper dataSyncTableConfigMapper
    ) {
        this.dataSyncTableConfigRepository = dataSyncTableConfigRepository;
        this.dataSyncTableConfigMapper = dataSyncTableConfigMapper;
    }

    /**
     * Save a dataSyncTableConfig.
     *
     * @param dataSyncTableConfigDTO the entity to save.
     * @return the persisted entity.
     */
    public DataSyncTableConfigDTO save(DataSyncTableConfigDTO dataSyncTableConfigDTO) {
        LOG.debug("Request to save DataSyncTableConfig : {}", dataSyncTableConfigDTO);
        DataSyncTableConfig dataSyncTableConfig = dataSyncTableConfigMapper.toEntity(dataSyncTableConfigDTO);
        dataSyncTableConfig = dataSyncTableConfigRepository.save(dataSyncTableConfig);
        return dataSyncTableConfigMapper.toDto(dataSyncTableConfig);
    }

    /**
     * Update a dataSyncTableConfig.
     *
     * @param dataSyncTableConfigDTO the entity to save.
     * @return the persisted entity.
     */
    public DataSyncTableConfigDTO update(DataSyncTableConfigDTO dataSyncTableConfigDTO) {
        LOG.debug("Request to update DataSyncTableConfig : {}", dataSyncTableConfigDTO);
        DataSyncTableConfig dataSyncTableConfig = dataSyncTableConfigMapper.toEntity(dataSyncTableConfigDTO);
        dataSyncTableConfig = dataSyncTableConfigRepository.save(dataSyncTableConfig);
        return dataSyncTableConfigMapper.toDto(dataSyncTableConfig);
    }

    /**
     * Partially update a dataSyncTableConfig.
     *
     * @param dataSyncTableConfigDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<DataSyncTableConfigDTO> partialUpdate(DataSyncTableConfigDTO dataSyncTableConfigDTO) {
        LOG.debug("Request to partially update DataSyncTableConfig : {}", dataSyncTableConfigDTO);

        return dataSyncTableConfigRepository
            .findById(dataSyncTableConfigDTO.getId())
            .map(existingDataSyncTableConfig -> {
                dataSyncTableConfigMapper.partialUpdate(existingDataSyncTableConfig, dataSyncTableConfigDTO);

                return existingDataSyncTableConfig;
            })
            .map(dataSyncTableConfigRepository::save)
            .map(dataSyncTableConfigMapper::toDto);
    }

    /**
     * Get all the dataSyncTableConfigs.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<DataSyncTableConfigDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all DataSyncTableConfigs");
        return dataSyncTableConfigRepository.findAll(pageable).map(dataSyncTableConfigMapper::toDto);
    }

    /**
     * Get one dataSyncTableConfig by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<DataSyncTableConfigDTO> findOne(String id) {
        LOG.debug("Request to get DataSyncTableConfig : {}", id);
        return dataSyncTableConfigRepository.findById(id).map(dataSyncTableConfigMapper::toDto);
    }

    /**
     * Delete the dataSyncTableConfig by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete DataSyncTableConfig : {}", id);
        dataSyncTableConfigRepository.deleteById(id);
    }
}
