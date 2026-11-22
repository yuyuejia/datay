package com.data.datafusion.service;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.DimensionRole;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.DimensionRoleRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.dto.DimensionRoleDTO;
import com.data.datafusion.service.mapper.DimensionRoleMapper;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 维度角色管理：角色维护在维度模型上，事实表字段只保存角色编码。
 */
@Service
@Transactional
public class DimensionRoleService {

    private static final Logger LOG = LoggerFactory.getLogger(DimensionRoleService.class);

    private static final Pattern ROLE_CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final int ROLE_CODE_MAX_LENGTH = 64;

    private final DimensionRoleRepository dimensionRoleRepository;
    private final DataModelRepository dataModelRepository;
    private final ModelFieldRepository modelFieldRepository;
    private final DimensionRoleMapper dimensionRoleMapper;

    public DimensionRoleService(
        DimensionRoleRepository dimensionRoleRepository,
        DataModelRepository dataModelRepository,
        ModelFieldRepository modelFieldRepository,
        DimensionRoleMapper dimensionRoleMapper
    ) {
        this.dimensionRoleRepository = dimensionRoleRepository;
        this.dataModelRepository = dataModelRepository;
        this.modelFieldRepository = modelFieldRepository;
        this.dimensionRoleMapper = dimensionRoleMapper;
    }

    @Transactional(readOnly = true)
    public List<DimensionRoleDTO> findByDimensionModelId(String dimensionModelId) {
        return dimensionRoleMapper.toDto(dimensionRoleRepository.findByDimensionModelIdOrderBySortOrderAscCodeAsc(dimensionModelId));
    }

    @Transactional(readOnly = true)
    public List<DimensionRole> findEntitiesByDimensionModelId(String dimensionModelId) {
        return dimensionRoleRepository.findByDimensionModelIdOrderBySortOrderAscCodeAsc(dimensionModelId);
    }

    /**
     * 创建单个角色。
     */
    public DimensionRoleDTO create(String dimensionModelId, DimensionRoleDTO dto) {
        DataModel dimensionModel = requireDimensionModel(dimensionModelId);
        String code = normalizeCode(dto == null ? null : dto.getCode());
        if (dimensionRoleRepository.existsByDimensionModelIdAndCodeIgnoreCase(dimensionModel.getId(), code)) {
            throw new IllegalArgumentException("维度角色编码已存在：" + code);
        }
        DimensionRole entity = new DimensionRole();
        entity.setDimensionModelId(dimensionModel.getId());
        entity.setCode(code);
        entity.setName(normalizeName(dto == null ? null : dto.getName(), code));
        entity.setDescription(trimToNull(dto == null ? null : dto.getDescription()));
        entity.setSortOrder(resolveSortOrder(dimensionModel.getId(), dto == null ? null : dto.getSortOrder()));
        entity.setCreateTime(ZonedDateTime.now());
        entity.setUpdateTime(ZonedDateTime.now());
        DimensionRole saved = dimensionRoleRepository.save(entity);
        LOG.debug("Created dimension role {} for model {}", saved.getCode(), dimensionModelId);
        return dimensionRoleMapper.toDto(saved);
    }

    /**
     * 批量创建角色（快速生成场景）：已存在的编码直接复用，不覆盖名称。
     */
    public List<DimensionRoleDTO> batchCreate(String dimensionModelId, List<DimensionRoleDTO> roles) {
        DataModel dimensionModel = requireDimensionModel(dimensionModelId);
        if (roles != null) {
            Map<String, DimensionRoleDTO> unique = new LinkedHashMap<>();
            for (DimensionRoleDTO role : roles) {
                String code = normalizeCode(role == null ? null : role.getCode());
                unique.putIfAbsent(code, role);
            }
            for (Map.Entry<String, DimensionRoleDTO> entry : unique.entrySet()) {
                String code = entry.getKey();
                DimensionRoleDTO dto = entry.getValue();
                if (dimensionRoleRepository.existsByDimensionModelIdAndCodeIgnoreCase(dimensionModel.getId(), code)) {
                    continue;
                }
                DimensionRole entity = new DimensionRole();
                entity.setDimensionModelId(dimensionModel.getId());
                entity.setCode(code);
                entity.setName(normalizeName(dto == null ? null : dto.getName(), code));
                entity.setDescription(trimToNull(dto == null ? null : dto.getDescription()));
                entity.setSortOrder(resolveSortOrder(dimensionModel.getId(), dto == null ? null : dto.getSortOrder()));
                entity.setCreateTime(ZonedDateTime.now());
                entity.setUpdateTime(ZonedDateTime.now());
                dimensionRoleRepository.save(entity);
            }
        }
        return findByDimensionModelId(dimensionModelId);
    }

    /**
     * 更新角色名称、描述和排序；角色编码创建后不可修改。
     */
    public DimensionRoleDTO update(String dimensionModelId, String roleId, DimensionRoleDTO dto) {
        DimensionRole entity = dimensionRoleRepository
            .findById(roleId)
            .orElseThrow(() -> new IllegalArgumentException("维度角色不存在：" + roleId));
        if (!dimensionModelId.equals(entity.getDimensionModelId())) {
            throw new IllegalArgumentException("维度角色不属于指定维度模型：" + roleId);
        }
        if (dto != null && dto.getCode() != null && !dto.getCode().isBlank()) {
            String code = normalizeCode(dto.getCode());
            if (!code.equals(entity.getCode())) {
                throw new IllegalArgumentException("维度角色编码创建后不可修改");
            }
        }
        if (dto != null && dto.getName() != null) {
            entity.setName(normalizeName(dto.getName(), entity.getCode()));
        }
        if (dto != null) {
            entity.setDescription(trimToNull(dto.getDescription()));
            if (dto.getSortOrder() != null) {
                entity.setSortOrder(dto.getSortOrder());
            }
        }
        entity.setUpdateTime(ZonedDateTime.now());
        return dimensionRoleMapper.toDto(dimensionRoleRepository.save(entity));
    }

    /**
     * 替换维度下的角色清单：用于维度编辑页统一保存。
     */
    public List<DimensionRoleDTO> replace(String dimensionModelId, List<DimensionRoleDTO> roles) {
        DataModel dimensionModel = requireDimensionModel(dimensionModelId);
        List<DimensionRole> existing = dimensionRoleRepository.findByDimensionModelIdOrderBySortOrderAscCodeAsc(
            dimensionModel.getId()
        );
        Map<String, DimensionRole> existingByCode = new LinkedHashMap<>();
        for (DimensionRole role : existing) {
            existingByCode.put(role.getCode(), role);
        }

        Map<String, DimensionRole> retained = new LinkedHashMap<>();
        if (roles != null) {
            int index = 0;
            for (DimensionRoleDTO dto : roles) {
                String code = normalizeCode(dto == null ? null : dto.getCode());
                if (retained.containsKey(code)) {
                    throw new IllegalArgumentException("维度角色编码重复：" + code);
                }
                DimensionRole entity = existingByCode.get(code);
                if (entity == null) {
                    entity = new DimensionRole();
                    entity.setDimensionModelId(dimensionModel.getId());
                    entity.setCode(code);
                    entity.setCreateTime(ZonedDateTime.now());
                }
                entity.setName(normalizeName(dto == null ? null : dto.getName(), code));
                entity.setDescription(trimToNull(dto == null ? null : dto.getDescription()));
                entity.setSortOrder(dto != null && dto.getSortOrder() != null ? dto.getSortOrder() : index);
                entity.setUpdateTime(ZonedDateTime.now());
                retained.put(code, entity);
                index++;
            }
        }

        for (DimensionRole role : existing) {
            if (retained.containsKey(role.getCode())) {
                continue;
            }
            if (isRoleReferenced(dimensionModel.getId(), role.getCode())) {
                throw new IllegalArgumentException("维度角色已被事实表字段使用，不能删除：" + role.getCode());
            }
            dimensionRoleRepository.delete(role);
        }
        dimensionRoleRepository.saveAll(retained.values());
        return findByDimensionModelId(dimensionModelId);
    }

    public void delete(String dimensionModelId, String roleId) {
        DimensionRole entity = dimensionRoleRepository
            .findById(roleId)
            .orElseThrow(() -> new IllegalArgumentException("维度角色不存在：" + roleId));
        if (!dimensionModelId.equals(entity.getDimensionModelId())) {
            throw new IllegalArgumentException("维度角色不属于指定维度模型：" + roleId);
        }
        if (isRoleReferenced(entity.getDimensionModelId(), entity.getCode())) {
            throw new IllegalArgumentException("维度角色已被事实表字段使用，不能删除：" + entity.getCode());
        }
        dimensionRoleRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public void validateRoleCodes(String dimensionModelId, List<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return;
        }
        for (String roleCode : roleCodes) {
            String code = normalizeCode(roleCode);
            if (!dimensionRoleRepository.existsByDimensionModelIdAndCodeIgnoreCase(dimensionModelId, code)) {
                throw new IllegalArgumentException("维度角色未在维度模型中定义：" + code);
            }
        }
    }

    @Transactional(readOnly = true)
    public boolean isRoleReferenced(String dimensionModelId, String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return false;
        }
        return modelFieldRepository.existsByDimensionModelIdAndDimensionRoleIgnoreCase(dimensionModelId, roleCode);
    }

    public void deleteByDimensionModelId(String dimensionModelId) {
        dimensionRoleRepository.deleteByDimensionModelId(dimensionModelId);
    }

    public static String normalizeCode(String code) {
        String normalized = trimToNull(code);
        if (normalized == null) {
            throw new IllegalArgumentException("维度角色编码不能为空");
        }
        normalized = normalized.toUpperCase(Locale.ROOT);
        if (normalized.length() > ROLE_CODE_MAX_LENGTH) {
            throw new IllegalArgumentException("维度角色编码不能超过 " + ROLE_CODE_MAX_LENGTH + " 个字符");
        }
        if (!ROLE_CODE_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("维度角色编码只能包含大写字母、数字和下划线，且必须以字母开头：" + normalized);
        }
        return normalized;
    }

    public static String normalizeName(String name, String defaultName) {
        String normalized = trimToNull(name);
        if (normalized == null) {
            normalized = defaultName;
        }
        if (normalized != null && normalized.length() > 128) {
            throw new IllegalArgumentException("维度角色名称不能超过 128 个字符");
        }
        return normalized;
    }

    private Integer resolveSortOrder(String dimensionModelId, Integer requested) {
        if (requested != null) {
            return requested;
        }
        List<DimensionRole> existing = dimensionRoleRepository.findByDimensionModelIdOrderBySortOrderAscCodeAsc(dimensionModelId);
        int max = -1;
        for (DimensionRole role : existing) {
            if (role.getSortOrder() != null && role.getSortOrder() > max) {
                max = role.getSortOrder();
            }
        }
        return max + 1;
    }

    private DataModel requireDimensionModel(String dimensionModelId) {
        DataModel model = dataModelRepository
            .findById(dimensionModelId)
            .orElseThrow(() -> new IllegalArgumentException("维度模型不存在：" + dimensionModelId));
        if (!"DIMENSION".equalsIgnoreCase(model.getModelType())) {
            throw new IllegalArgumentException("只有维度模型可以维护维度角色：" + model.getName());
        }
        return model;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
