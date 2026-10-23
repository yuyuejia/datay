package com.data.datafusion.service;

import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.domain.RoleDatasetScope;
import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.data.datafusion.repository.AuthorityRepository;
import com.data.datafusion.repository.RoleDatasetScopeRepository;
import com.data.datafusion.security.AuthoritiesConstants;
import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.service.dataset.DatasetScopeCondition;
import com.data.datafusion.service.dataset.DatasetScopeFilterConfig;
import com.data.datafusion.service.dto.RoleDatasetScopeDTO;
import com.data.datafusion.service.mapper.RoleDatasetScopeMapper;
import com.data.datafusion.service.metric.MetricFilterOperator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 角色数据集范围管理：基于 RBAC，维护角色可访问的数据集行范围，并在查询时解析当前用户生效的条件。
 */
@Service
@Transactional
public class RoleDatasetScopeService {

    private static final Logger LOG = LoggerFactory.getLogger(RoleDatasetScopeService.class);

    private final RoleDatasetScopeRepository roleDatasetScopeRepository;
    private final AuthorityRepository authorityRepository;
    private final AnalysisDatasetRepository analysisDatasetRepository;
    private final RoleDatasetScopeMapper roleDatasetScopeMapper;
    private final ObjectMapper objectMapper;

    public RoleDatasetScopeService(
        RoleDatasetScopeRepository roleDatasetScopeRepository,
        AuthorityRepository authorityRepository,
        AnalysisDatasetRepository analysisDatasetRepository,
        RoleDatasetScopeMapper roleDatasetScopeMapper,
        ObjectMapper objectMapper
    ) {
        this.roleDatasetScopeRepository = roleDatasetScopeRepository;
        this.authorityRepository = authorityRepository;
        this.analysisDatasetRepository = analysisDatasetRepository;
        this.roleDatasetScopeMapper = roleDatasetScopeMapper;
        this.objectMapper = objectMapper;
    }

    public RoleDatasetScopeDTO save(RoleDatasetScopeDTO dto) {
        LOG.debug("Request to save RoleDatasetScope : {}", dto);
        RoleDatasetScope entity = roleDatasetScopeMapper.toEntity(dto);
        validate(entity);
        entity = roleDatasetScopeRepository.save(entity);
        return roleDatasetScopeMapper.toDto(entity);
    }

    public RoleDatasetScopeDTO update(RoleDatasetScopeDTO dto) {
        LOG.debug("Request to update RoleDatasetScope : {}", dto);
        RoleDatasetScope existing = roleDatasetScopeRepository
            .findById(dto.getId())
            .orElseThrow(() -> new IllegalArgumentException("数据集数据权限不存在：" + dto.getId()));
        RoleDatasetScope entity = roleDatasetScopeMapper.toEntity(dto);
        // DTO 不携带 tenantId，显式保留原租户，避免更新后数据被租户过滤器隐藏
        entity.setTenantId(existing.getTenantId());
        validate(entity);
        entity = roleDatasetScopeRepository.save(entity);
        return roleDatasetScopeMapper.toDto(entity);
    }

    @Transactional(readOnly = true)
    public List<RoleDatasetScopeDTO> findAll(String roleName) {
        LOG.debug("Request to get RoleDatasetScopes, roleName : {}", roleName);
        List<RoleDatasetScope> scopes = (roleName == null || roleName.isBlank())
            ? roleDatasetScopeRepository.findAll()
            : roleDatasetScopeRepository.findByRoleName(roleName);
        return scopes.stream().map(roleDatasetScopeMapper::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<RoleDatasetScopeDTO> findOne(String id) {
        LOG.debug("Request to get RoleDatasetScope : {}", id);
        return roleDatasetScopeRepository.findById(id).map(roleDatasetScopeMapper::toDto);
    }

    public void delete(String id) {
        LOG.debug("Request to delete RoleDatasetScope : {}", id);
        roleDatasetScopeRepository.deleteById(id);
    }

    /**
     * 解析当前登录用户在指定数据集上生效的行级过滤条件。
     * <p>
     * 规则：{@code ROLE_ADMIN} 或未登录、无任何规则时返回空（不限制）。多个规则的条件以 OR 组合。
     */
    @Transactional(readOnly = true)
    public List<DatasetScopeCondition> resolveConditions(String datasetId) {
        if (datasetId == null || datasetId.isBlank()) {
            return List.of();
        }
        Set<String> authorities = SecurityUtils.getCurrentUserAuthorities();
        if (authorities.isEmpty() || authorities.contains(AuthoritiesConstants.ADMIN)) {
            return List.of();
        }
        List<RoleDatasetScope> scopes = roleDatasetScopeRepository.findByRoleNameInAndDatasetIdAndEnabledTrue(authorities, datasetId);
        if (scopes.isEmpty()) {
            return List.of();
        }
        List<DatasetScopeCondition> result = new ArrayList<>();
        for (RoleDatasetScope scope : scopes) {
            DatasetScopeFilterConfig config = parseFilterConfig(scope.getFilterConfig());
            if (config == null || config.isEmpty()) {
                continue;
            }
            result.addAll(config.getConditions());
        }
        return result;
    }

    /**
     * 数据集被删除时同步清理其权限规则。
     */
    public void deleteByDatasetId(String datasetId) {
        if (datasetId != null && !datasetId.isBlank()) {
            roleDatasetScopeRepository.deleteByDatasetId(datasetId);
        }
    }

    private void validate(RoleDatasetScope entity) {
        if (entity.getRoleName() == null || entity.getRoleName().isBlank()) {
            throw new IllegalArgumentException("角色不能为空");
        }
        entity.setRoleName(entity.getRoleName().trim());
        if (!authorityRepository.existsById(entity.getRoleName())) {
            throw new IllegalArgumentException("角色不存在：" + entity.getRoleName());
        }
        if (entity.getDatasetId() == null || entity.getDatasetId().isBlank()) {
            throw new IllegalArgumentException("数据集不能为空");
        }
        AnalysisDataset dataset = analysisDatasetRepository
            .findById(entity.getDatasetId())
            .orElseThrow(() -> new IllegalArgumentException("数据集不存在：" + entity.getDatasetId()));
        Set<String> fieldNames = fieldNames(dataset);
        if (fieldNames.isEmpty()) {
            throw new IllegalArgumentException("数据集暂无字段信息，请先预览数据集生成字段后再配置数据权限");
        }
        DatasetScopeFilterConfig config = parseFilterConfig(entity.getFilterConfig());
        if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException("请至少配置一条数据集行级过滤条件");
        }
        for (DatasetScopeCondition condition : config.getConditions()) {
            if (condition.getFieldName() == null || condition.getFieldName().isBlank()) {
                throw new IllegalArgumentException("数据集字段不能为空");
            }
            String fieldName = condition.getFieldName().trim();
            condition.setFieldName(fieldName);
            if (!fieldNames.contains(fieldName)) {
                throw new IllegalArgumentException("数据集字段不存在：" + fieldName);
            }
            MetricFilterOperator operator = MetricFilterOperator.fromCode(condition.getOperator());
            if (operator == null) {
                throw new IllegalArgumentException("不支持的条件运算符：" + condition.getOperator());
            }
            condition.setOperator(operator.name());
            if (operator.isRange()) {
                if (isBlank(condition.getValue()) || isBlank(condition.getValueEnd())) {
                    throw new IllegalArgumentException("BETWEEN 条件需要起止值");
                }
            } else if (!operator.isUnary() && isBlank(condition.getValue())) {
                throw new IllegalArgumentException("条件缺少取值：" + fieldName);
            }
        }
        try {
            entity.setFilterConfig(objectMapper.writeValueAsString(config));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("条件序列化失败：" + e.getOriginalMessage());
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(Boolean.TRUE);
        }
    }

    private Set<String> fieldNames(AnalysisDataset dataset) {
        String fields = dataset.getFields();
        if (fields == null || fields.isBlank()) {
            return Set.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(fields, new TypeReference<Map<String, Object>>() {});
            Object rawColumns = parsed == null ? null : parsed.get("columns");
            if (!(rawColumns instanceof List<?> columns)) {
                return Set.of();
            }
            Set<String> names = new LinkedHashSet<>();
            for (Object rawColumn : columns) {
                String name = null;
                if (rawColumn instanceof Map<?, ?> column) {
                    Object rawName = column.get("name");
                    name = rawName == null ? null : String.valueOf(rawName);
                } else if (rawColumn != null) {
                    name = String.valueOf(rawColumn);
                }
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }
            return names;
        } catch (JsonProcessingException | IllegalArgumentException e) {
            LOG.warn("解析数据集字段失败: {}", e.getMessage());
            return Set.of();
        }
    }

    private DatasetScopeFilterConfig parseFilterConfig(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, DatasetScopeFilterConfig.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("数据集数据权限条件格式错误：" + e.getOriginalMessage());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
