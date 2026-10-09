package com.data.datafusion.service;

import com.data.datafusion.domain.ServiceConfig;
import com.data.datafusion.repository.ServiceConfigRepository;
import com.data.datafusion.service.dto.ServiceConfigDTO;
import com.data.datafusion.service.mapper.ServiceConfigMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.data.datafusion.domain.ServiceConfig}.
 */
@Service
@Transactional
public class ServiceConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceConfigService.class);

    private final ServiceConfigRepository serviceConfigRepository;

    private final ServiceConfigMapper serviceConfigMapper;

    public ServiceConfigService(ServiceConfigRepository serviceConfigRepository, ServiceConfigMapper serviceConfigMapper) {
        this.serviceConfigRepository = serviceConfigRepository;
        this.serviceConfigMapper = serviceConfigMapper;
    }

    /**
     * Save a serviceConfig.
     *
     * @param serviceConfigDTO the entity to save.
     * @return the persisted entity.
     */
    public ServiceConfigDTO save(ServiceConfigDTO serviceConfigDTO) {
        LOG.debug("Request to save ServiceConfig : {}", serviceConfigDTO);
        ServiceConfig serviceConfig = serviceConfigMapper.toEntity(serviceConfigDTO);
        serviceConfig = serviceConfigRepository.save(serviceConfig);
        return serviceConfigMapper.toDto(serviceConfig);
    }

    /**
     * Update a serviceConfig.
     *
     * @param serviceConfigDTO the entity to save.
     * @return the persisted entity.
     */
    public ServiceConfigDTO update(ServiceConfigDTO serviceConfigDTO) {
        LOG.debug("Request to update ServiceConfig : {}", serviceConfigDTO);
        ServiceConfig serviceConfig = serviceConfigMapper.toEntity(serviceConfigDTO);
        serviceConfig = serviceConfigRepository.save(serviceConfig);
        return serviceConfigMapper.toDto(serviceConfig);
    }

    /**
     * Partially update a serviceConfig.
     *
     * @param serviceConfigDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<ServiceConfigDTO> partialUpdate(ServiceConfigDTO serviceConfigDTO) {
        LOG.debug("Request to partially update ServiceConfig : {}", serviceConfigDTO);

        return serviceConfigRepository
            .findById(serviceConfigDTO.getId())
            .map(existingServiceConfig -> {
                serviceConfigMapper.partialUpdate(existingServiceConfig, serviceConfigDTO);

                return existingServiceConfig;
            })
            .map(serviceConfigRepository::save)
            .map(serviceConfigMapper::toDto);
    }

    /**
     * Get all the serviceConfigs.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<ServiceConfigDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all ServiceConfigs");
        return serviceConfigRepository.findAll(pageable).map(serviceConfigMapper::toDto);
    }

    /**
     * Get one serviceConfig by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<ServiceConfigDTO> findOne(String id) {
        LOG.debug("Request to get ServiceConfig : {}", id);
        return serviceConfigRepository.findById(id).map(serviceConfigMapper::toDto);
    }

    /**
     * Delete the serviceConfig by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete ServiceConfig : {}", id);
        serviceConfigRepository.deleteById(id);
    }
}
