package com.data.datafusion.service;

import com.data.datafusion.domain.DataSource;
import com.data.datafusion.repository.DataSourceRepository;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.mapper.DataSourceMapper;
import com.data.datafusion.util.DBUtils;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.data.datafusion.domain.DataSource}.
 */
@Service
@Transactional
public class DataSourceService {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceService.class);

    private final DataSourceRepository dataSourceRepository;

    private final DataSourceMapper dataSourceMapper;

    public DataSourceService(DataSourceRepository dataSourceRepository, DataSourceMapper dataSourceMapper) {
        this.dataSourceRepository = dataSourceRepository;
        this.dataSourceMapper = dataSourceMapper;
    }

    /**
     * Save a dataSource.
     *
     * @param dataSourceDTO the entity to save.
     * @return the persisted entity.
     */
    public DataSourceDTO save(DataSourceDTO dataSourceDTO) {
        LOG.debug("Request to save DataSource : {}", dataSourceDTO);
        DataSource dataSource = dataSourceMapper.toEntity(dataSourceDTO);
        dataSource = dataSourceRepository.save(dataSource);
        return dataSourceMapper.toDto(dataSource);
    }

    /**
     * Update a dataSource.
     *
     * @param dataSourceDTO the entity to save.
     * @return the persisted entity.
     */
    public DataSourceDTO update(DataSourceDTO dataSourceDTO) {
        LOG.debug("Request to update DataSource : {}", dataSourceDTO);
        DataSource dataSource = dataSourceMapper.toEntity(dataSourceDTO);
        dataSource = dataSourceRepository.save(dataSource);
        return dataSourceMapper.toDto(dataSource);
    }

    /**
     * Partially update a dataSource.
     *
     * @param dataSourceDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<DataSourceDTO> partialUpdate(DataSourceDTO dataSourceDTO) {
        LOG.debug("Request to partially update DataSource : {}", dataSourceDTO);

        return dataSourceRepository
            .findById(dataSourceDTO.getId())
            .map(existingDataSource -> {
                dataSourceMapper.partialUpdate(existingDataSource, dataSourceDTO);

                return existingDataSource;
            })
            .map(dataSourceRepository::save)
            .map(dataSourceMapper::toDto);
    }

    /**
     * Get all the dataSources.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<DataSourceDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all DataSources");
        return dataSourceRepository.findAll(pageable).map(dataSourceMapper::toDto);
    }

    /**
     * Get all the dataSources with an optional keyword search.
     *
     * @param pageable the pagination information.
     * @param search   the optional keyword used to filter by name, hostname/IP, port, url, schema name or username.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<DataSourceDTO> findAll(Pageable pageable, String search) {
        LOG.debug("Request to get all DataSources with search: {}", search);
        return dataSourceRepository.findAll(buildSearchSpecification(search), pageable).map(dataSourceMapper::toDto);
    }

    private Specification<DataSource> buildSearchSpecification(String search) {
        if (search == null || search.trim().isEmpty()) {
            return Specification.where(null);
        }
        String keyword = search.trim().toLowerCase();
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (String field : new String[] { "name", "hostname", "port", "url", "schemaName", "username" }) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get(field)), "%" + keyword + "%"));
            }
            return criteriaBuilder.or(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Get one dataSource by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<DataSourceDTO> findOne(Long id) {
        LOG.debug("Request to get DataSource : {}", id);
        return dataSourceRepository.findById(id).map(dataSourceMapper::toDto);
    }

    /**
     * Delete the dataSource by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete DataSource : {}", id);
        dataSourceRepository.deleteById(id);
    }

    /**
     * 测试数据源连接
     * @param dataSourceDTO 数据源DTO
     * @return 连接测试结果，true表示连接成功，false表示连接失败
     */
    public boolean testConnection(DataSourceDTO dataSourceDTO) {
        LOG.debug("Request to test DataSource connection: {}", dataSourceDTO);
        return DBUtils.testConnection(dataSourceDTO);
    }
}
