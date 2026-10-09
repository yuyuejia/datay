package com.data.datafusion.service;

import com.data.datafusion.domain.DpTable;
import com.data.datafusion.repository.DpTableRepository;
import com.data.datafusion.service.dto.DpTableDTO;
import com.data.datafusion.service.mapper.DpTableMapper;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.data.datafusion.domain.DpTable}.
 */
@Service
@Transactional
public class DpTableService {

    private static final Logger LOG = LoggerFactory.getLogger(DpTableService.class);

    private final DpTableRepository dpTableRepository;

    private final DpTableMapper dpTableMapper;

    public DpTableService(DpTableRepository dpTableRepository, DpTableMapper dpTableMapper) {
        this.dpTableRepository = dpTableRepository;
        this.dpTableMapper = dpTableMapper;
    }

    /**
     * Save a dpTable.
     *
     * @param dpTableDTO the entity to save.
     * @return the persisted entity.
     */
    public DpTableDTO save(DpTableDTO dpTableDTO) {
        LOG.debug("Request to save DpTable : {}", dpTableDTO);
        DpTable dpTable = dpTableMapper.toEntity(dpTableDTO);
        dpTable = dpTableRepository.save(dpTable);
        return dpTableMapper.toDto(dpTable);
    }

    /**
     * Update a dpTable.
     *
     * @param dpTableDTO the entity to save.
     * @return the persisted entity.
     */
    public DpTableDTO update(DpTableDTO dpTableDTO) {
        LOG.debug("Request to update DpTable : {}", dpTableDTO);
        DpTable dpTable = dpTableMapper.toEntity(dpTableDTO);
        dpTable = dpTableRepository.save(dpTable);
        return dpTableMapper.toDto(dpTable);
    }

    /**
     * Partially update a dpTable.
     *
     * @param dpTableDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<DpTableDTO> partialUpdate(DpTableDTO dpTableDTO) {
        LOG.debug("Request to partially update DpTable : {}", dpTableDTO);

        return dpTableRepository
            .findById(dpTableDTO.getId())
            .map(existingDpTable -> {
                dpTableMapper.partialUpdate(existingDpTable, dpTableDTO);

                return existingDpTable;
            })
            .map(dpTableRepository::save)
            .map(dpTableMapper::toDto);
    }

    /**
     * Get all the dpTables.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<DpTableDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all DpTables");
        return dpTableRepository.findAll(pageable).map(dpTableMapper::toDto);
    }

    /**
     * Get one dpTable by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<DpTableDTO> findOne(String id) {
        LOG.debug("Request to get DpTable : {}", id);
        return dpTableRepository.findById(id).map(dpTableMapper::toDto);
    }

    /**
     * Delete the dpTable by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete DpTable : {}", id);
        dpTableRepository.deleteById(id);
    }
}
