package com.data.datafusion.service;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.domain.RoleDataScope;
import com.data.datafusion.repository.AuthorityRepository;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.repository.RoleDataScopeRepository;
import com.data.datafusion.security.AuthoritiesConstants;
import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.service.dto.RoleDataScopeDTO;
import com.data.datafusion.service.mapper.RoleDataScopeMapper;
import com.data.datafusion.service.metric.MetricFilterCondition;
import com.data.datafusion.service.metric.MetricFilterConfig;
import com.data.datafusion.service.metric.MetricFilterOperator;
import com.data.datafusion.service.metric.ScopedDimension;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
 * 角色数据范围管理：基于 RBAC，维护角色可访问的维度成员范围，并在查询时解析当前用户生效的范围。
 */
@Service
@Transactional
public class RoleDataScopeService {

    private static final Logger LOG = LoggerFactory.getLogger(RoleDataScopeService.class);

    private static final String MODEL_TYPE_DIMENSION = "DIMENSION";

    private final RoleDataScopeRepository roleDataScopeRepository;
    private final AuthorityRepository authorityRepository;
    private final DataModelRepository dataModelRepository;
    private final ModelFieldRepository modelFieldRepository;
    private final RoleDataScopeMapper roleDataScopeMapper;
    private final ObjectMapper objectMapper;

    public RoleDataScopeService(
        RoleDataScopeRepository roleDataScopeRepository,
        AuthorityRepository authorityRepository,
        DataModelRepository dataModelRepository,
        ModelFieldRepository modelFieldRepository,
        RoleDataScopeMapper roleDataScopeMapper,
        ObjectMapper objectMapper
    ) {
        this.roleDataScopeRepository = roleDataScopeRepository;
        this.authorityRepository = authorityRepository;
        this.dataModelRepository = dataModelRepository;
        this.modelFieldRepository = modelFieldRepository;
        this.roleDataScopeMapper = roleDataScopeMapper;
        this.objectMapper = objectMapper;
    }

    public RoleDataScopeDTO save(RoleDataScopeDTO dto) {
        LOG.debug("Request to save RoleDataScope : {}", dto);
        RoleDataScope entity = roleDataScopeMapper.toEntity(dto);
        validate(entity);
        entity = roleDataScopeRepository.save(entity);
        return roleDataScopeMapper.toDto(entity);
    }

    public RoleDataScopeDTO update(RoleDataScopeDTO dto) {
        LOG.debug("Request to update RoleDataScope : {}", dto);
        RoleDataScope existing = roleDataScopeRepository
            .findById(dto.getId())
            .orElseThrow(() -> new IllegalArgumentException("数据范围不存在：" + dto.getId()));
        RoleDataScope entity = roleDataScopeMapper.toEntity(dto);
        // DTO 不携带 tenantId，显式保留原租户，避免更新后数据被租户过滤器隐藏
        entity.setTenantId(existing.getTenantId());
        validate(entity);
        entity = roleDataScopeRepository.save(entity);
        return roleDataScopeMapper.toDto(entity);
    }

    @Transactional(readOnly = true)
    public List<RoleDataScopeDTO> findAll(String roleName) {
        LOG.debug("Request to get RoleDataScopes, roleName : {}", roleName);
        List<RoleDataScope> scopes = (roleName == null || roleName.isBlank())
            ? roleDataScopeRepository.findAll()
            : roleDataScopeRepository.findByRoleName(roleName);
        return scopes.stream().map(roleDataScopeMapper::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<RoleDataScopeDTO> findOne(String id) {
        LOG.debug("Request to get RoleDataScope : {}", id);
        return roleDataScopeRepository.findById(id).map(roleDataScopeMapper::toDto);
    }

    public void delete(String id) {
        LOG.debug("Request to delete RoleDataScope : {}", id);
        roleDataScopeRepository.deleteById(id);
    }

    /**
     * 解析当前登录用户生效的维度成员范围。
     * <p>
     * 规则：{@code ROLE_ADMIN} 或未登录、无任何规则时返回空（不限制）。
     */
    @Transactional(readOnly = true)
    public List<ScopedDimension> resolveEffectiveScopes() {
        Set<String> authorities = SecurityUtils.getCurrentUserAuthorities();
        if (authorities.isEmpty() || authorities.contains(AuthoritiesConstants.ADMIN)) {
            return List.of();
        }
        List<RoleDataScope> scopes = roleDataScopeRepository.findByRoleNameInAndEnabledTrue(authorities);
        if (scopes.isEmpty()) {
            return List.of();
        }

        Map<String, List<MetricFilterCondition>> grouped = new LinkedHashMap<>();
        for (RoleDataScope scope : scopes) {
            String dimensionModelId = scope.getDimensionModelId();
            if (dimensionModelId == null) {
                continue;
            }
            MetricFilterConfig config = parseFilterConfig(scope.getFilterConfig());
            if (config == null || config.isEmpty()) {
                continue;
            }
            grouped.computeIfAbsent(dimensionModelId, key -> new ArrayList<>()).addAll(config.getConditions());
        }

        List<ScopedDimension> result = new ArrayList<>();
        for (Map.Entry<String, List<MetricFilterCondition>> entry : grouped.entrySet()) {
            ScopedDimension scoped = new ScopedDimension();
            scoped.setDimensionModelId(entry.getKey());
            scoped.setConditions(entry.getValue());
            result.add(scoped);
        }
        return result;
    }

    private void validate(RoleDataScope entity) {
        if (entity.getRoleName() == null || entity.getRoleName().isBlank()) {
            throw new IllegalArgumentException("角色不能为空");
        }
        entity.setRoleName(entity.getRoleName().trim());
        if (!authorityRepository.existsById(entity.getRoleName())) {
            throw new IllegalArgumentException("角色不存在：" + entity.getRoleName());
        }
        if (entity.getDimensionModelId() == null) {
            throw new IllegalArgumentException("维度模型不能为空");
        }
        DataModel dimensionModel = dataModelRepository
            .findById(entity.getDimensionModelId())
            .orElseThrow(() -> new IllegalArgumentException("维度模型不存在：" + entity.getDimensionModelId()));
        if (!MODEL_TYPE_DIMENSION.equalsIgnoreCase(dimensionModel.getModelType())) {
            throw new IllegalArgumentException("数据范围只能绑定维度模型：" + dimensionModel.getCode());
        }
        MetricFilterConfig config = parseFilterConfig(entity.getFilterConfig());
        if (config == null || config.isEmpty()) {
            throw new IllegalArgumentException("请至少配置一条维度成员范围条件");
        }
        Set<String> fieldNames = modelFieldRepository
            .findByModelIdOrderBySortOrderAsc(dimensionModel.getId())
            .stream()
            .map(ModelField::getFieldName)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        for (MetricFilterCondition condition : config.getConditions()) {
            condition.setType(MetricFilterCondition.TYPE_DIMENSION);
            condition.setDimensionModelId(dimensionModel.getId());
            condition.setDimensionModelCode(dimensionModel.getCode());
            if (condition.getDimensionFieldName() == null || condition.getDimensionFieldName().isBlank()) {
                throw new IllegalArgumentException("维度字段不能为空");
            }
            if (!fieldNames.contains(condition.getDimensionFieldName())) {
                throw new IllegalArgumentException("维度字段不存在：" + dimensionModel.getCode() + "." + condition.getDimensionFieldName());
            }
            MetricFilterOperator operator = MetricFilterOperator.fromCode(condition.getOperator());
            if (operator == null) {
                throw new IllegalArgumentException("不支持的条件运算符：" + condition.getOperator());
            }
            condition.setOperator(operator.name());
            if (MetricFilterOperator.BETWEEN == operator) {
                if (condition.getValue() == null || condition.getValue().isBlank() || condition.getValueEnd() == null || condition.getValueEnd().isBlank()) {
                    throw new IllegalArgumentException("BETWEEN 条件需要起止值");
                }
            } else if (!operator.isUnary() && (condition.getValue() == null || condition.getValue().isBlank())) {
                throw new IllegalArgumentException("条件缺少取值：" + condition.getDimensionFieldName());
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

    private MetricFilterConfig parseFilterConfig(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MetricFilterConfig.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("数据范围条件格式错误：" + e.getOriginalMessage());
        }
    }
}
