package com.data.datafusion.service;

import com.data.datafusion.domain.ModelField;
import com.data.datafusion.repository.DimensionRoleRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.dto.ModelFieldDTO;
import com.data.datafusion.service.mapper.ModelFieldMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ModelFieldService {

    private static final Logger LOG = LoggerFactory.getLogger(ModelFieldService.class);

    private final ModelFieldRepository modelFieldRepository;
    private final DimensionRoleRepository dimensionRoleRepository;
    private final ModelFieldMapper modelFieldMapper;

    public ModelFieldService(
        ModelFieldRepository modelFieldRepository,
        DimensionRoleRepository dimensionRoleRepository,
        ModelFieldMapper modelFieldMapper
    ) {
        this.modelFieldRepository = modelFieldRepository;
        this.dimensionRoleRepository = dimensionRoleRepository;
        this.modelFieldMapper = modelFieldMapper;
    }

    public ModelFieldDTO save(ModelFieldDTO modelFieldDTO) {
        LOG.debug("Request to save ModelField : {}", modelFieldDTO);
        normalizeAndValidateDimensionRoles(List.of(modelFieldDTO));
        ModelField modelField = modelFieldMapper.toEntity(modelFieldDTO);
        modelField = modelFieldRepository.save(modelField);
        return modelFieldMapper.toDto(modelField);
    }

    public ModelFieldDTO update(ModelFieldDTO modelFieldDTO) {
        LOG.debug("Request to update ModelField : {}", modelFieldDTO);
        normalizeAndValidateDimensionRoles(List.of(modelFieldDTO));
        ModelField modelField = modelFieldMapper.toEntity(modelFieldDTO);
        modelField = modelFieldRepository.save(modelField);
        return modelFieldMapper.toDto(modelField);
    }

    public Optional<ModelFieldDTO> partialUpdate(ModelFieldDTO modelFieldDTO) {
        LOG.debug("Request to partially update ModelField : {}", modelFieldDTO);
        return modelFieldRepository
            .findById(modelFieldDTO.getId())
            .map(existing -> {
                modelFieldMapper.partialUpdate(existing, modelFieldDTO);
                return existing;
            })
            .map(modelFieldRepository::save)
            .map(modelFieldMapper::toDto);
    }

    public List<ModelFieldDTO> saveAll(List<ModelFieldDTO> modelFieldDTOs) {
        LOG.debug("Request to save all ModelFields : {}", modelFieldDTOs);
        normalizeAndValidateDimensionRoles(modelFieldDTOs);
        List<ModelField> modelFields = modelFieldMapper.toEntity(modelFieldDTOs);
        modelFields = modelFieldRepository.saveAll(modelFields);
        return modelFieldMapper.toDto(modelFields);
    }

    /**
     * 保存前校验维度角色，供 REST 层在删除旧字段前调用，避免校验失败时字段被清空。
     */
    public void validateDimensionRoles(List<ModelFieldDTO> modelFieldDTOs) {
        normalizeAndValidateDimensionRoles(modelFieldDTOs);
    }

    @Transactional(readOnly = true)
    public List<ModelFieldDTO> findAll() {
        LOG.debug("Request to get all ModelFields");
        return modelFieldRepository.findAll().stream()
            .map(modelFieldMapper::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ModelFieldDTO> findByModelId(String modelId) {
        LOG.debug("Request to get ModelFields by modelId : {}", modelId);
        return modelFieldRepository.findByModelIdOrderBySortOrderAsc(modelId).stream()
            .map(modelFieldMapper::toDto)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ModelFieldDTO> findOne(String id) {
        LOG.debug("Request to get ModelField : {}", id);
        return modelFieldRepository.findById(id).map(modelFieldMapper::toDto);
    }

    public void delete(String id) {
        LOG.debug("Request to delete ModelField : {}", id);
        modelFieldRepository.deleteById(id);
    }

    public void deleteByModelId(String modelId) {
        LOG.debug("Request to delete ModelFields by modelId : {}", modelId);
        modelFieldRepository.deleteByModelId(modelId);
    }

    /**
     * 规范化事实字段的维度角色，并校验：
     * <ul>
     *   <li>角色必须已在对应维度模型中定义；</li>
     *   <li>同一事实模型下同一维度的角色不能重复（空角色视为默认关联）。</li>
     * </ul>
     */
    private void normalizeAndValidateDimensionRoles(List<ModelFieldDTO> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        Map<String, String> seen = new java.util.HashMap<>();
        for (ModelFieldDTO field : fields) {
            if (field == null) {
                continue;
            }
            if (field.getDimensionModelId() == null || field.getDimensionModelId().isBlank()) {
                field.setDimensionRole(null);
                continue;
            }
            String dimensionModelId = field.getDimensionModelId().trim();
            field.setDimensionModelId(dimensionModelId);
            String role = field.getDimensionRole() == null || field.getDimensionRole().isBlank()
                ? null
                : DimensionRoleService.normalizeCode(field.getDimensionRole());
            field.setDimensionRole(role);
            if (role != null && !dimensionRoleRepository.existsByDimensionModelIdAndCodeIgnoreCase(dimensionModelId, role)) {
                throw new IllegalArgumentException("维度角色未在维度模型中定义：" + role);
            }
            String key = dimensionModelId + "|" + (role == null ? "" : role);
            String previous = seen.putIfAbsent(key, field.getFieldName());
            if (previous != null) {
                throw new IllegalArgumentException(
                    "同一事实表对维度 " + dimensionModelId + " 重复关联角色 " + (role == null ? "（默认）" : role)
                );
            }
        }
    }
}