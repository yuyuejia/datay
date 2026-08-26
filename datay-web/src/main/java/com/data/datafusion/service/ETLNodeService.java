package com.data.datafusion.service;

import com.data.datafusion.domain.ETLNode;
import com.data.datafusion.repository.ETLNodeRepository;
import com.data.datafusion.service.dto.ETLNodeDTO;
import com.data.datafusion.service.mapper.ETLNodeMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.data.datafusion.domain.ETLNode}.
 */
@Service
@Transactional
public class ETLNodeService {

    private static final Logger LOG = LoggerFactory.getLogger(ETLNodeService.class);

    private final ETLNodeRepository eTLNodeRepository;

    private final ETLNodeMapper eTLNodeMapper;

    public ETLNodeService(ETLNodeRepository eTLNodeRepository, ETLNodeMapper eTLNodeMapper) {
        this.eTLNodeRepository = eTLNodeRepository;
        this.eTLNodeMapper = eTLNodeMapper;
    }

    /**
     * Save a eTLNode.
     *
     * @param eTLNodeDTO the entity to save.
     * @return the persisted entity.
     */
    public ETLNodeDTO save(ETLNodeDTO eTLNodeDTO) {
        LOG.debug("Request to save ETLNode : {}", eTLNodeDTO);
        ETLNode eTLNode = eTLNodeMapper.toEntity(eTLNodeDTO);
        eTLNode = eTLNodeRepository.save(eTLNode);
        return eTLNodeMapper.toDto(eTLNode);
    }

    /**
     * Update a eTLNode.
     *
     * @param eTLNodeDTO the entity to save.
     * @return the persisted entity.
     */
    public ETLNodeDTO update(ETLNodeDTO eTLNodeDTO) {
        LOG.debug("Request to update ETLNode : {}", eTLNodeDTO);
        ETLNode eTLNode = eTLNodeMapper.toEntity(eTLNodeDTO);
        eTLNode = eTLNodeRepository.save(eTLNode);
        return eTLNodeMapper.toDto(eTLNode);
    }

    /**
     * Partially update a eTLNode.
     *
     * @param eTLNodeDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<ETLNodeDTO> partialUpdate(ETLNodeDTO eTLNodeDTO) {
        LOG.debug("Request to partially update ETLNode : {}", eTLNodeDTO);

        return eTLNodeRepository
            .findById(eTLNodeDTO.getId())
            .map(existingETLNode -> {
                eTLNodeMapper.partialUpdate(existingETLNode, eTLNodeDTO);

                return existingETLNode;
            })
            .map(eTLNodeRepository::save)
            .map(eTLNodeMapper::toDto);
    }

    /**
     * Get all the eTLNodes.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<ETLNodeDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all ETLNodes");
        return eTLNodeRepository.findAll(pageable).map(eTLNodeMapper::toDto);
    }

    /**
     * Get one eTLNode by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<ETLNodeDTO> findOne(Long id) {
        LOG.debug("Request to get ETLNode : {}", id);
        return eTLNodeRepository.findById(id).map(eTLNodeMapper::toDto);
    }

    /**
     * Delete the eTLNode by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete ETLNode : {}", id);
        eTLNodeRepository.deleteById(id);
    }
}
