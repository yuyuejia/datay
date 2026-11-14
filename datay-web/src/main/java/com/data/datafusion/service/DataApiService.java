package com.data.datafusion.service;

import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.domain.DataApi;
import com.data.datafusion.domain.ETLTaskDirectory;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.data.datafusion.repository.DataApiRepository;
import com.data.datafusion.repository.ETLTaskDirectoryRepository;
import com.data.datafusion.service.dto.DataApiDTO;
import com.data.datafusion.service.mapper.DataApiMapper;
import jakarta.persistence.criteria.Predicate;
import java.time.ZonedDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Service Implementation for managing {@link com.data.datafusion.domain.DataApi}.
 */
@Service
@Transactional
public class DataApiService {

    private static final Logger LOG = LoggerFactory.getLogger(DataApiService.class);

    private final DataApiRepository dataApiRepository;

    private final DataApiMapper dataApiMapper;

    private final AnalysisDatasetRepository analysisDatasetRepository;

    private final ETLTaskDirectoryRepository etlTaskDirectoryRepository;

    public DataApiService(
        DataApiRepository dataApiRepository,
        DataApiMapper dataApiMapper,
        AnalysisDatasetRepository analysisDatasetRepository,
        ETLTaskDirectoryRepository etlTaskDirectoryRepository
    ) {
        this.dataApiRepository = dataApiRepository;
        this.dataApiMapper = dataApiMapper;
        this.analysisDatasetRepository = analysisDatasetRepository;
        this.etlTaskDirectoryRepository = etlTaskDirectoryRepository;
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
        normalizeSourceFields(dataApiDTO);
        normalizeDirectory(dataApiDTO);
        ensureDatasetUnique(dataApiDTO.getDatasetId(), null);

        ZonedDateTime now = ZonedDateTime.now();
        dataApiDTO.setCreateTime(now);
        dataApiDTO.setUpdateTime(now);
        if (dataApiDTO.getStatus() == null) {
            dataApiDTO.setStatus(DataApi.STATUS_ENABLED);
        }
        DataApi dataApi = dataApiMapper.toEntity(dataApiDTO);
        dataApi = dataApiRepository.save(dataApi);
        DataApiDTO result = dataApiMapper.toDto(dataApi);
        fillDatasetInfo(List.of(result));
        return result;
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
        normalizeSourceFields(dataApiDTO);
        ensureDatasetUnique(dataApiDTO.getDatasetId(), id);

        return dataApiRepository
            .findById(id)
            .map(existing -> {
                dataApiDTO.setId(id);
                dataApiDTO.setTenantId(existing.getTenantId());
                dataApiDTO.setCreateTime(existing.getCreateTime());
                dataApiDTO.setUpdateTime(ZonedDateTime.now());
                // directoryId 为 null 表示保持原目录不变；空字符串表示移动到未分类
                if (dataApiDTO.getDirectoryId() == null) {
                    dataApiDTO.setDirectoryId(existing.getDirectoryId());
                }
                normalizeDirectory(dataApiDTO);
                DataApi dataApi = dataApiMapper.toEntity(dataApiDTO);
                dataApi = dataApiRepository.save(dataApi);
                DataApiDTO result = dataApiMapper.toDto(dataApi);
                fillDatasetInfo(List.of(result));
                return result;
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
        return findAll(pageable, search, null, false);
    }

    /**
     * Get all the dataApis with optional keyword and directory filters.
     *
     * @param pageable      the pagination information.
     * @param search        the optional keyword used to filter by name, code or description.
     * @param directoryId   the optional directory ID; includes all descendant directories.
     * @param uncategorized when true and no directory is specified, only services without a directory are returned.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<DataApiDTO> findAll(Pageable pageable, String search, String directoryId, boolean uncategorized) {
        LOG.debug(
            "Request to get all DataApis with search: {}, directoryId: {}, uncategorized: {}",
            search,
            directoryId,
            uncategorized
        );
        Specification<DataApi> specification = buildSearchSpecification(search);
        if (StringUtils.hasText(directoryId)) {
            Set<String> directoryIds = collectDirectorySubtreeIds(directoryId);
            specification = specification.and((root, query, criteriaBuilder) ->
                directoryIds.isEmpty()
                    ? criteriaBuilder.disjunction()
                    : root.get("directoryId").in(directoryIds)
            );
        } else if (uncategorized) {
            specification = specification.and((root, query, criteriaBuilder) -> criteriaBuilder.isNull(root.get("directoryId")));
        }
        Page<DataApiDTO> page = dataApiRepository.findAll(specification, pageable).map(dataApiMapper::toDto);
        fillDatasetInfo(page.getContent());
        return page;
    }

    /**
     * 收集目录及其所有子孙目录 ID。
     */
    private Set<String> collectDirectorySubtreeIds(String directoryId) {
        Set<String> ids = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(directoryId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (!ids.add(current)) {
                continue;
            }
            etlTaskDirectoryRepository.findByParentIdOrderBySortOrderAsc(current).forEach(child -> queue.add(child.getId()));
        }
        return ids;
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
        return dataApiRepository
            .findById(id)
            .map(dataApiMapper::toDto)
            .map(dto -> {
                fillDatasetInfo(List.of(dto));
                return dto;
            });
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
        return dataApiRepository
            .findByCode(code)
            .map(dataApiMapper::toDto)
            .map(dto -> {
                fillDatasetInfo(List.of(dto));
                return dto;
            });
    }

    /**
     * 按数据集 id 查找已发布的数据服务。
     */
    @Transactional(readOnly = true)
    public Optional<DataApiDTO> findByDatasetId(String datasetId) {
        if (datasetId == null || datasetId.isBlank()) {
            return Optional.empty();
        }
        return dataApiRepository
            .findByDatasetId(datasetId)
            .map(dataApiMapper::toDto)
            .map(dto -> {
                fillDatasetInfo(List.of(dto));
                return dto;
            });
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

    /**
     * 移动数据服务到指定目录；{@code directoryId} 为空表示移动到未分类。
     */
    public DataApiDTO moveToDirectory(String id, String directoryId) {
        DataApi dataApi = dataApiRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("数据服务不存在: " + id));
        String normalizedDirectoryId = StringUtils.hasText(directoryId) ? directoryId.trim() : null;
        validateDirectory(normalizedDirectoryId);
        dataApi.setDirectoryId(normalizedDirectoryId);
        dataApi.setUpdateTime(ZonedDateTime.now());
        DataApiDTO result = dataApiMapper.toDto(dataApiRepository.save(dataApi));
        fillDatasetInfo(List.of(result));
        return result;
    }

    private void normalizeDirectory(DataApiDTO dto) {
        dto.setDirectoryId(StringUtils.hasText(dto.getDirectoryId()) ? dto.getDirectoryId().trim() : null);
        validateDirectory(dto.getDirectoryId());
    }

    /**
     * 校验数据服务要归属的目录存在且属于数据服务目录树。
     */
    private void validateDirectory(String directoryId) {
        if (!StringUtils.hasText(directoryId)) {
            return;
        }
        ETLTaskDirectory directory = etlTaskDirectoryRepository
            .findById(directoryId)
            .orElseThrow(() -> new IllegalArgumentException("目录不存在: " + directoryId));
        if (!TaskConstants.TASK_TYPE_DATA_API.equals(directory.getTaskType())) {
            throw new IllegalArgumentException("目录与数据服务类型不匹配: " + directoryId);
        }
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
        } else if (DataApi.SOURCE_TYPE_DATASET.equals(dto.getSourceType())) {
            if (dto.getDatasetId() == null || dto.getDatasetId().trim().isEmpty()) {
                throw new IllegalArgumentException("请选择要发布的分析数据集");
            }
            // 只校验存在性；数据集停用由执行期统一拦截，避免无法编辑或停用已有服务
            analysisDatasetRepository
                .findById(dto.getDatasetId().trim())
                .orElseThrow(() -> new IllegalArgumentException("数据集不存在：" + dto.getDatasetId()));
        } else {
            throw new IllegalArgumentException("数据来源类型必须是 TABLE、SQL、DATASET 或 API");
        }
    }

    /**
     * DATASET 来源只保留 datasetId，避免前端残留其他来源的配置。
     */
    private void normalizeSourceFields(DataApiDTO dto) {
        if (DataApi.SOURCE_TYPE_DATASET.equals(dto.getSourceType())) {
            dto.setDatasetId(dto.getDatasetId() == null ? null : dto.getDatasetId().trim());
            dto.setDataSourceId(null);
            dto.setSchemaName(null);
            dto.setTableName(null);
            dto.setSqlText(null);
            dto.setApiConfig(null);
        } else {
            dto.setDatasetId(null);
        }
    }

    private void ensureDatasetUnique(String datasetId, String excludeId) {
        if (datasetId == null || datasetId.isBlank()) {
            return;
        }
        Optional<DataApi> conflict = excludeId == null
            ? dataApiRepository.findByDatasetId(datasetId)
            : dataApiRepository.findByDatasetIdAndIdNot(datasetId, excludeId);
        if (conflict.isPresent()) {
            throw new IllegalArgumentException("该数据集已发布为数据服务：" + conflict.get().getCode());
        }
    }

    /**
     * 批量填充关联数据集名称与类型，供列表和详情展示。
     */
    private void fillDatasetInfo(List<DataApiDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }
        List<String> datasetIds = dtos
            .stream()
            .filter(dto -> DataApi.SOURCE_TYPE_DATASET.equals(dto.getSourceType()))
            .map(DataApiDTO::getDatasetId)
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .collect(Collectors.toList());
        if (datasetIds.isEmpty()) {
            return;
        }
        Map<String, AnalysisDataset> datasets = new LinkedHashMap<>();
        analysisDatasetRepository
            .findAllById(datasetIds)
            .forEach(dataset -> datasets.put(dataset.getId(), dataset));
        for (DataApiDTO dto : dtos) {
            AnalysisDataset dataset = datasets.get(dto.getDatasetId());
            if (dataset != null) {
                dto.setDatasetName(dataset.getName());
                dto.setDatasetType(dataset.getType());
            }
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
