package com.data.datafusion.service;

import com.data.datafusion.domain.DataApi;
import com.data.datafusion.repository.DataApiRepository;
import com.data.datafusion.service.dto.DataApiDTO;
import com.data.datafusion.service.mapper.DataApiMapper;
import jakarta.persistence.criteria.Predicate;
import java.time.ZonedDateTime;
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
 * Service Implementation for managing {@link com.data.datafusion.domain.DataApi}.
 */
@Service
@Transactional
public class DataApiService {

    private static final Logger LOG = LoggerFactory.getLogger(DataApiService.class);

    private final DataApiRepository dataApiRepository;

    private final DataApiMapper dataApiMapper;

    public DataApiService(DataApiRepository dataApiRepository, DataApiMapper dataApiMapper) {
        this.dataApiRepository = dataApiRepository;
        this.dataApiMapper = dataApiMapper;
    }

    /**
     * Save a new dataApi.
     *
     * @param dataApiDTO the entity to save.
     * @return the persisted entity.
     */
    public DataApiDTO save(DataApiDTO dataApiDTO) {
        LOG.debug("Request to save DataApi : {}", dataApiDTO);
        validate(dataApiDTO);
        ensureCodeUnique(dataApiDTO.getCode(), null);

        ZonedDateTime now = ZonedDateTime.now();
        dataApiDTO.setCreateTime(now);
        dataApiDTO.setUpdateTime(now);
        if (dataApiDTO.getStatus() == null) {
            dataApiDTO.setStatus(DataApi.STATUS_ENABLED);
        }
        DataApi dataApi = dataApiMapper.toEntity(dataApiDTO);
        dataApi = dataApiRepository.save(dataApi);
        return dataApiMapper.toDto(dataApi);
    }

    /**
     * Update a dataApi.
     *
     * @param id         the id of the entity.
     * @param dataApiDTO the entity to update.
     * @return the updated entity, empty if not found.
     */
    public Optional<DataApiDTO> update(String id, DataApiDTO dataApiDTO) {
        LOG.debug("Request to update DataApi : {}, {}", id, dataApiDTO);
        validate(dataApiDTO);
        ensureCodeUnique(dataApiDTO.getCode(), id);

        return dataApiRepository
            .findById(id)
            .map(existing -> {
                dataApiDTO.setId(id);
                dataApiDTO.setTenantId(existing.getTenantId());
                dataApiDTO.setCreateTime(existing.getCreateTime());
                dataApiDTO.setUpdateTime(ZonedDateTime.now());
                DataApi dataApi = dataApiMapper.toEntity(dataApiDTO);
                dataApi = dataApiRepository.save(dataApi);
                return dataApiMapper.toDto(dataApi);
            });
    }

    /**
     * Get all the dataApis.
     *
     * @param pageable the pagination information.
     * @param search   the optional keyword used to filter by name, code or description.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<DataApiDTO> findAll(Pageable pageable, String search) {
        LOG.debug("Request to get all DataApis with search: {}", search);
        return dataApiRepository.findAll(buildSearchSpecification(search), pageable).map(dataApiMapper::toDto);
    }

    private Specification<DataApi> buildSearchSpecification(String search) {
        if (search == null || search.trim().isEmpty()) {
            return Specification.where(null);
        }
        String keyword = search.trim().toLowerCase();
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (String field : new String[] { "name", "code", "description", "tableName", "schemaName" }) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get(field)), "%" + keyword + "%"));
            }
            return criteriaBuilder.or(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Get one dataApi by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<DataApiDTO> findOne(String id) {
        LOG.debug("Request to get DataApi : {}", id);
        return dataApiRepository.findById(id).map(dataApiMapper::toDto);
    }

    /**
     * Get one dataApi by code.
     *
     * @param code the code of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<DataApiDTO> findByCode(String code) {
        LOG.debug("Request to get DataApi by code : {}", code);
        return dataApiRepository.findByCode(code).map(dataApiMapper::toDto);
    }

    /**
     * Delete the dataApi by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete DataApi : {}", id);
        dataApiRepository.deleteById(id);
    }

    private void validate(DataApiDTO dto) {
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("服务名称不能为空");
        }
        if (dto.getCode() == null || dto.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("服务编码不能为空");
        }
        if (dto.getCode() != null && !dto.getCode().matches("[A-Za-z0-9_\\-]+")) {
            throw new IllegalArgumentException("服务编码只能包含字母、数字、下划线和中划线");
        }
        if (DataApi.SOURCE_TYPE_TABLE.equals(dto.getSourceType())) {
            if (dto.getDataSourceId() == null) {
                throw new IllegalArgumentException("请选择数据源");
            }
            if (dto.getSchemaName() == null || dto.getSchemaName().trim().isEmpty()) {
                throw new IllegalArgumentException("请选择数据表所属的 Schema");
            }
            if (dto.getTableName() == null || dto.getTableName().trim().isEmpty()) {
                throw new IllegalArgumentException("请选择数据表");
            }
        } else if (DataApi.SOURCE_TYPE_SQL.equals(dto.getSourceType())) {
            if (dto.getDataSourceId() == null) {
                throw new IllegalArgumentException("请选择数据源");
            }
            if (dto.getSqlText() == null || dto.getSqlText().trim().isEmpty()) {
                throw new IllegalArgumentException("自定义 SQL 不能为空");
            }
        } else if (DataApi.SOURCE_TYPE_API.equals(dto.getSourceType())) {
            if (dto.getApiConfig() == null || dto.getApiConfig().trim().isEmpty()) {
                throw new IllegalArgumentException("请配置要注册的 API");
            }
        } else {
            throw new IllegalArgumentException("数据来源类型必须是 TABLE、SQL 或 API");
        }
    }

    private void ensureCodeUnique(String code, String excludeId) {
        Optional<DataApi> conflict = excludeId == null
            ? dataApiRepository.findByCode(code)
            : dataApiRepository.findByCodeAndIdNot(code, excludeId);
        if (conflict.isPresent()) {
            throw new IllegalArgumentException("服务编码已存在：" + code);
        }
    }
}
