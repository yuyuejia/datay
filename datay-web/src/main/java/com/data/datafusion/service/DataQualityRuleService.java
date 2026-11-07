package com.data.datafusion.service;

import cn.hutool.json.JSONUtil;
import com.data.datafusion.domain.DataQualityRule;
import com.data.job.quality.QualityRuleType;
import com.data.datafusion.repository.DataQualityRuleRepository;
import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.service.dto.DataQualityRuleDTO;
import com.data.datafusion.service.mapper.DataQualityRuleMapper;
import jakarta.persistence.criteria.Predicate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据质量规则服务。
 */
@Service
@Transactional
public class DataQualityRuleService {

    private final DataQualityRuleRepository ruleRepository;

    private final DataQualityRuleMapper ruleMapper;

    public DataQualityRuleService(DataQualityRuleRepository ruleRepository, DataQualityRuleMapper ruleMapper) {
        this.ruleRepository = ruleRepository;
        this.ruleMapper = ruleMapper;
    }

    public DataQualityRuleDTO save(DataQualityRuleDTO dto) {
        validate(dto, false);
        DataQualityRule rule = ruleMapper.toEntity(dto);
        rule.setBuiltin(Boolean.FALSE);
        rule.setCreateTime(ZonedDateTime.now());
        rule.setUpdateTime(ZonedDateTime.now());
        if (rule.getCreater() == null || rule.getCreater().isBlank()) {
            rule.setCreater(SecurityUtils.getCurrentUserLogin().orElse(null));
        }
        if (rule.getTenantId() == null || rule.getTenantId().isBlank()) {
            rule.setTenantId(SecurityUtils.getCurrentTenantIdString().orElse(null));
        }
        rule = ruleRepository.save(rule);
        return ruleMapper.toDto(rule);
    }

    public DataQualityRuleDTO update(DataQualityRuleDTO dto) {
        if (dto.getId() == null) {
            throw new IllegalArgumentException("规则 ID 不能为空");
        }
        DataQualityRule existing = ruleRepository
            .findById(dto.getId())
            .orElseThrow(() -> new IllegalArgumentException("质量规则不存在: " + dto.getId()));
        if (Boolean.TRUE.equals(existing.getBuiltin())) {
            throw new IllegalArgumentException("系统预置规则不允许修改，请复制为自定义规则后再编辑");
        }
        validate(dto, true);
        DataQualityRule rule = ruleMapper.toEntity(dto);
        rule.setBuiltin(Boolean.FALSE);
        rule.setCreateTime(existing.getCreateTime());
        rule.setUpdateTime(ZonedDateTime.now());
        rule.setCreater(existing.getCreater());
        if (rule.getTenantId() == null || rule.getTenantId().isBlank()) {
            rule.setTenantId(existing.getTenantId());
        }
        rule = ruleRepository.save(rule);
        return ruleMapper.toDto(rule);
    }

    @Transactional(readOnly = true)
    public Page<DataQualityRuleDTO> findAll(Pageable pageable, String search, String level, String type, Boolean enabled) {
        return ruleRepository.findAll(buildSpecification(search, level, type, enabled), pageable).map(ruleMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<DataQualityRuleDTO> findOne(String id) {
        return ruleRepository.findById(id).map(ruleMapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<DataQualityRule> findEntity(String id) {
        return ruleRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<DataQualityRule> findEnabledRules() {
        return ruleRepository.findAll(buildSpecification(null, null, null, Boolean.TRUE));
    }

    public void delete(String id) {
        DataQualityRule rule = ruleRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("质量规则不存在: " + id));
        if (Boolean.TRUE.equals(rule.getBuiltin())) {
            throw new IllegalArgumentException("系统预置规则不允许删除");
        }
        ruleRepository.delete(rule);
    }

    /**
     * 规则类型目录，供前端动态渲染参数表单。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTypes() {
        List<Map<String, Object>> types = new ArrayList<>();
        for (QualityRuleType type : QualityRuleType.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("code", type.getCode());
            item.put("label", type.getLabel());
            item.put("level", type.getLevel().name());
            item.put("description", type.getDescription());
            types.add(item);
        }
        return types;
    }

    private void validate(DataQualityRuleDTO dto, boolean update) {
        if (dto.getRuleName() == null || dto.getRuleName().isBlank()) {
            throw new IllegalArgumentException("规则名称不能为空");
        }
        if (dto.getRuleCode() == null || dto.getRuleCode().isBlank()) {
            dto.setRuleCode("DQ_" + System.currentTimeMillis());
        } else {
            dto.setRuleCode(dto.getRuleCode().trim());
        }
        QualityRuleType type = QualityRuleType.of(dto.getRuleType());
        dto.setRuleType(type.getCode());
        dto.setRuleLevel(type.getLevel().name());
        dto.setSeverity(normalizeSeverity(dto.getSeverity()));
        if (dto.getEnabled() == null) {
            dto.setEnabled(Boolean.TRUE);
        }
        if (dto.getDefaultConfig() != null && !dto.getDefaultConfig().isBlank() && !JSONUtil.isTypeJSONObject(dto.getDefaultConfig())) {
            throw new IllegalArgumentException("规则默认参数必须是 JSON 对象");
        }
        String code = dto.getRuleCode();
        ruleRepository
            .findByRuleCode(code)
            .filter(existing -> !existing.getId().equals(dto.getId()))
            .ifPresent(existing -> {
                throw new IllegalArgumentException("规则编码已存在: " + code);
            });
    }

    private String normalizeSeverity(String severity) {
        String normalized = severity == null || severity.isBlank() ? "ERROR" : severity.trim().toUpperCase(Locale.ROOT);
        if (!"ERROR".equals(normalized) && !"WARN".equals(normalized)) {
            throw new IllegalArgumentException("规则严重级别仅支持 ERROR 或 WARN");
        }
        return normalized;
    }

    private Specification<DataQualityRule> buildSpecification(String search, String level, String type, Boolean enabled) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(
                    cb.or(
                        cb.like(cb.lower(root.get("ruleName")), pattern),
                        cb.like(cb.lower(root.get("ruleCode")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern)
                    )
                );
            }
            if (level != null && !level.isBlank()) {
                predicates.add(cb.equal(root.get("ruleLevel"), level.trim().toUpperCase(Locale.ROOT)));
            }
            if (type != null && !type.isBlank()) {
                predicates.add(cb.equal(root.get("ruleType"), type.trim().toUpperCase(Locale.ROOT)));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
