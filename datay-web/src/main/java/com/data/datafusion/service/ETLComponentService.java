package com.data.datafusion.service;

import com.data.datafusion.domain.ETLComponent;
import com.data.datafusion.repository.ETLComponentRepository;
import com.data.datafusion.service.dto.ETLComponentDTO;
import com.data.datafusion.service.mapper.ETLComponentMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.data.datafusion.domain.ETLComponent}.
 */
@Service
@Transactional
public class ETLComponentService {

    private static final Logger LOG = LoggerFactory.getLogger(ETLComponentService.class);

    private final ETLComponentRepository eTLComponentRepository;

    private final ETLComponentMapper eTLComponentMapper;

    public ETLComponentService(ETLComponentRepository eTLComponentRepository, ETLComponentMapper eTLComponentMapper) {
        this.eTLComponentRepository = eTLComponentRepository;
        this.eTLComponentMapper = eTLComponentMapper;
    }

    /**
     * Save a eTLComponent.
     *
     * @param eTLComponentDTO the entity to save.
     * @return the persisted entity.
     */
    public ETLComponentDTO save(ETLComponentDTO eTLComponentDTO) {
        LOG.debug("Request to save ETLComponent : {}", eTLComponentDTO);
        ETLComponent eTLComponent = eTLComponentMapper.toEntity(eTLComponentDTO);
        eTLComponent = eTLComponentRepository.save(eTLComponent);
        return eTLComponentMapper.toDto(eTLComponent);
    }

    /**
     * Update a eTLComponent.
     *
     * @param eTLComponentDTO the entity to save.
     * @return the persisted entity.
     */
    public ETLComponentDTO update(ETLComponentDTO eTLComponentDTO) {
        LOG.debug("Request to update ETLComponent : {}", eTLComponentDTO);
        ETLComponent eTLComponent = eTLComponentMapper.toEntity(eTLComponentDTO);
        eTLComponent = eTLComponentRepository.save(eTLComponent);
        return eTLComponentMapper.toDto(eTLComponent);
    }

    /**
     * Partially update a eTLComponent.
     *
     * @param eTLComponentDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<ETLComponentDTO> partialUpdate(ETLComponentDTO eTLComponentDTO) {
        LOG.debug("Request to partially update ETLComponent : {}", eTLComponentDTO);

        return eTLComponentRepository
            .findById(eTLComponentDTO.getId())
            .map(existingETLComponent -> {
                eTLComponentMapper.partialUpdate(existingETLComponent, eTLComponentDTO);

                return existingETLComponent;
            })
            .map(eTLComponentRepository::save)
            .map(eTLComponentMapper::toDto);
    }

    /**
     * Get all the eTLComponents.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<ETLComponentDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all ETLComponents");
        return eTLComponentRepository.findAll(pageable).map(eTLComponentMapper::toDto);
    }

    /**
     * Get one eTLComponent by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<ETLComponentDTO> findOne(String id) {
        LOG.debug("Request to get ETLComponent : {}", id);
        return eTLComponentRepository.findById(id).map(eTLComponentMapper::toDto);
    }

    /**
     * Delete the eTLComponent by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete ETLComponent : {}", id);
        eTLComponentRepository.deleteById(id);
    }
}
