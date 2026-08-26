package com.data.datafusion.service;

import com.data.datafusion.domain.ETLEdge;
import com.data.datafusion.repository.ETLEdgeRepository;
import com.data.datafusion.service.dto.ETLEdgeDTO;
import com.data.datafusion.service.mapper.ETLEdgeMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.data.datafusion.domain.ETLEdge}.
 */
@Service
@Transactional
public class ETLEdgeService {

    private static final Logger LOG = LoggerFactory.getLogger(ETLEdgeService.class);

    private final ETLEdgeRepository eTLEdgeRepository;

    private final ETLEdgeMapper eTLEdgeMapper;

    public ETLEdgeService(ETLEdgeRepository eTLEdgeRepository, ETLEdgeMapper eTLEdgeMapper) {
        this.eTLEdgeRepository = eTLEdgeRepository;
        this.eTLEdgeMapper = eTLEdgeMapper;
    }

    /**
     * Save a eTLEdge.
     *
     * @param eTLEdgeDTO the entity to save.
     * @return the persisted entity.
     */
    public ETLEdgeDTO save(ETLEdgeDTO eTLEdgeDTO) {
        LOG.debug("Request to save ETLEdge : {}", eTLEdgeDTO);
        ETLEdge eTLEdge = eTLEdgeMapper.toEntity(eTLEdgeDTO);
        eTLEdge = eTLEdgeRepository.save(eTLEdge);
        return eTLEdgeMapper.toDto(eTLEdge);
    }

    /**
     * Update a eTLEdge.
     *
     * @param eTLEdgeDTO the entity to save.
     * @return the persisted entity.
     */
    public ETLEdgeDTO update(ETLEdgeDTO eTLEdgeDTO) {
        LOG.debug("Request to update ETLEdge : {}", eTLEdgeDTO);
        ETLEdge eTLEdge = eTLEdgeMapper.toEntity(eTLEdgeDTO);
        eTLEdge = eTLEdgeRepository.save(eTLEdge);
        return eTLEdgeMapper.toDto(eTLEdge);
    }

    /**
     * Partially update a eTLEdge.
     *
     * @param eTLEdgeDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<ETLEdgeDTO> partialUpdate(ETLEdgeDTO eTLEdgeDTO) {
        LOG.debug("Request to partially update ETLEdge : {}", eTLEdgeDTO);

        return eTLEdgeRepository
            .findById(eTLEdgeDTO.getId())
            .map(existingETLEdge -> {
                eTLEdgeMapper.partialUpdate(existingETLEdge, eTLEdgeDTO);

                return existingETLEdge;
            })
            .map(eTLEdgeRepository::save)
            .map(eTLEdgeMapper::toDto);
    }

    /**
     * Get all the eTLEdges.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<ETLEdgeDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all ETLEdges");
        return eTLEdgeRepository.findAll(pageable).map(eTLEdgeMapper::toDto);
    }

    /**
     * Get one eTLEdge by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<ETLEdgeDTO> findOne(Long id) {
        LOG.debug("Request to get ETLEdge : {}", id);
        return eTLEdgeRepository.findById(id).map(eTLEdgeMapper::toDto);
    }

    /**
     * Delete the eTLEdge by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete ETLEdge : {}", id);
        eTLEdgeRepository.deleteById(id);
    }
}
