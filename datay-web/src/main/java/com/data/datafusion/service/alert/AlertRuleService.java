package com.data.datafusion.service.alert;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.data.datafusion.domain.AlertRule;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.repository.AlertRuleRepository;
import com.data.datafusion.service.dto.AlertRuleDTO;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 告警规则管理。MVP 只支持任务失败/超时两类事件。
 */
@Service
@Transactional
public class AlertRuleService {

    private static final Logger LOG = LoggerFactory.getLogger(AlertRuleService.class);

    private static final List<String> ALLOWED_EVENTS = List.of(AlertRule.EVENT_FAILED, AlertRule.EVENT_TIMEOUT);

    private static final List<String> ALLOWED_SEVERITIES = List.of(
        AlertRule.SEVERITY_P0,
        AlertRule.SEVERITY_P1,
        AlertRule.SEVERITY_P2
    );

    private final AlertRuleRepository alertRuleRepository;

    public AlertRuleService(AlertRuleRepository alertRuleRepository) {
        this.alertRuleRepository = alertRuleRepository;
    }

    @Transactional(readOnly = true)
    public List<AlertRuleDTO> findAll() {
        return alertRuleRepository.findAllByOrderByCreateTimeDesc().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<AlertRuleDTO> findOne(String id) {
        return alertRuleRepository.findById(id).map(this::toDto);
    }

    public AlertRuleDTO save(AlertRuleDTO dto) {
        validate(dto);
        String code = normalizeCode(dto.code(), null);
        ZonedDateTime now = ZonedDateTime.now();
        AlertRule entity = toEntity(dto);
        entity.setId(null);
        entity.setCode(code);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDefaultRule(Boolean.FALSE);
        if (entity.getEnabled() == null) {
            entity.setEnabled(Boolean.TRUE);
        }
        return toDto(alertRuleRepository.save(entity));
    }

    public Optional<AlertRuleDTO> update(String id, AlertRuleDTO dto) {
        validate(dto);
        return alertRuleRepository
            .findById(id)
            .map(existing -> {
                AlertRule entity = toEntity(dto);
                entity.setId(id);
                entity.setTenantId(existing.getTenantId());
                entity.setCode(normalizeCode(dto.code() == null || dto.code().isBlank() ? existing.getCode() : dto.code(), id));
                entity.setCreateTime(existing.getCreateTime());
                entity.setUpdateTime(ZonedDateTime.now());
                entity.setDefaultRule(existing.getDefaultRule());
                if (entity.getEnabled() == null) {
                    entity.setEnabled(existing.getEnabled());
                }
                return toDto(alertRuleRepository.save(entity));
            });
    }

    public void delete(String id) {
        alertRuleRepository.deleteById(id);
    }

    /**
     * 当前租户没有任何规则时创建默认规则：任务失败/超时 -> 租户管理员站内信。
     */
    public AlertRule ensureDefaultRule(String tenantId) {
        if (alertRuleRepository.count() > 0) {
            return null;
        }
        ZonedDateTime now = ZonedDateTime.now();
        AlertRule rule = new AlertRule();
        rule.setTenantId(tenantId);
        rule.setName("任务失败告警");
        rule.setCode("default_job_failure");
        rule.setSourceType(AlertRule.SOURCE_JOB);
        rule.setEventType(AlertRule.EVENT_FAILED + "," + AlertRule.EVENT_TIMEOUT);
        rule.setSeverity(AlertRule.SEVERITY_P1);
        rule.setSilenceMinutes(30);
        rule.setRecipients("[\"role:ROLE_TENANT_ADMIN\"]");
        rule.setChannelIds("[]");
        rule.setDefaultRule(Boolean.TRUE);
        rule.setEnabled(Boolean.TRUE);
        rule.setCreateTime(now);
        rule.setUpdateTime(now);
        LOG.info("Create default alert rule for tenant {}", tenantId);
        return alertRuleRepository.save(rule);
    }

    @Transactional(readOnly = true)
    public List<AlertRule> findMatching(JobInstance instance) {
        if (instance == null) {
            return List.of();
        }
        List<AlertRule> matching = alertRuleRepository
            .findByEnabledTrue()
            .stream()
            .filter(rule -> matches(rule, instance))
            .collect(Collectors.toList());
        // 自定义规则命中时优先使用自定义规则，默认规则只作为兜底，避免同样的失败产生两条告警。
        List<AlertRule> customRules = matching
            .stream()
            .filter(rule -> !Boolean.TRUE.equals(rule.getDefaultRule()))
            .collect(Collectors.toList());
        return customRules.isEmpty() ? matching : customRules;
    }

    private boolean matches(AlertRule rule, JobInstance instance) {
        if (hasText(rule.getSourceType()) && !AlertRule.SOURCE_JOB.equalsIgnoreCase(rule.getSourceType())) {
            return false;
        }
        List<String> events = splitCsv(rule.getEventType());
        if (!events.isEmpty() && !events.contains(instance.getStatus())) {
            return false;
        }
        if (hasText(rule.getProject()) && !rule.getProject().trim().equals(instance.getProject())) {
            return false;
        }
        if (hasText(rule.getJobType()) && !rule.getJobType().trim().equalsIgnoreCase(instance.getType())) {
            return false;
        }
        return true;
    }

    private void validate(AlertRuleDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("规则不能为空");
        }
        if (!hasText(dto.name())) {
            throw new IllegalArgumentException("规则名称不能为空");
        }
        if (hasText(dto.sourceType()) && !AlertRule.SOURCE_JOB.equalsIgnoreCase(dto.sourceType())) {
            throw new IllegalArgumentException("MVP 仅支持任务来源");
        }
        List<String> events = splitCsv(dto.eventType());
        if (events.isEmpty()) {
            throw new IllegalArgumentException("请选择告警事件");
        }
        for (String event : events) {
            if (!ALLOWED_EVENTS.contains(event)) {
                throw new IllegalArgumentException("不支持的告警事件：" + event);
            }
        }
        String severity = dto.severity() == null ? AlertRule.SEVERITY_P1 : dto.severity().toUpperCase(Locale.ROOT);
        if (!ALLOWED_SEVERITIES.contains(severity)) {
            throw new IllegalArgumentException("告警级别必须是 P0、P1 或 P2");
        }
        validateJsonArray(dto.recipients(), "接收人");
        validateJsonArray(dto.channelIds(), "通知渠道");
    }

    private void validateJsonArray(String json, String label) {
        if (!hasText(json)) {
            return;
        }
        try {
            JSONArray array = JSON.parseArray(json);
            if (array == null) {
                throw new IllegalArgumentException(label + "格式不正确");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(label + "格式不正确");
        }
    }

    private String normalizeCode(String requested, String excludeId) {
        String code = requested == null ? null : requested.trim();
        if (!hasText(code)) {
            code = "alert_rule_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        if (!code.matches("[A-Za-z0-9_\\-]+")) {
            throw new IllegalArgumentException("规则编码只能包含字母、数字、下划线和中划线");
        }
        Optional<AlertRule> conflict = excludeId == null
            ? alertRuleRepository.findByCode(code)
            : alertRuleRepository.findByCode(code).filter(rule -> !rule.getId().equals(excludeId));
        if (conflict.isPresent()) {
            throw new IllegalArgumentException("规则编码已存在：" + code);
        }
        return code;
    }

    private AlertRule toEntity(AlertRuleDTO dto) {
        AlertRule entity = new AlertRule();
        entity.setName(dto.name().trim());
        entity.setCode(dto.code() == null ? null : dto.code().trim());
        entity.setSourceType(AlertRule.SOURCE_JOB);
        entity.setEventType(normalizeEvents(dto.eventType()));
        entity.setSeverity(dto.severity() == null ? AlertRule.SEVERITY_P1 : dto.severity().toUpperCase(Locale.ROOT));
        entity.setProject(trimToNull(dto.project()));
        entity.setJobType(trimToNull(dto.jobType()));
        entity.setSilenceMinutes(dto.silenceMinutes() == null || dto.silenceMinutes() < 0 ? 30 : dto.silenceMinutes());
        entity.setRecipients(normalizeArray(dto.recipients(), "[\"role:ROLE_TENANT_ADMIN\"]"));
        entity.setChannelIds(normalizeArray(dto.channelIds(), "[]"));
        entity.setEnabled(dto.enabled());
        return entity;
    }

    private String normalizeEvents(String eventType) {
        List<String> events = splitCsv(eventType);
        if (events.isEmpty()) {
            return AlertRule.EVENT_FAILED + "," + AlertRule.EVENT_TIMEOUT;
        }
        return events.stream().distinct().collect(Collectors.joining(","));
    }

    private String normalizeArray(String json, String fallback) {
        if (!hasText(json)) {
            return fallback;
        }
        JSONArray array = JSON.parseArray(json);
        if (array == null || array.isEmpty()) {
            return fallback;
        }
        JSONArray normalized = new JSONArray();
        for (Object item : array) {
            if (item == null) {
                continue;
            }
            String value = String.valueOf(item).trim();
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        return normalized.isEmpty() ? fallback : normalized.toJSONString();
    }

    private List<String> splitCsv(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        return Arrays
            .stream(value.split(","))
            .map(String::trim)
            .filter(item -> !item.isEmpty())
            .map(item -> item.toUpperCase(Locale.ROOT))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private AlertRuleDTO toDto(AlertRule entity) {
        return new AlertRuleDTO(
            entity.getId(),
            entity.getName(),
            entity.getCode(),
            entity.getSourceType(),
            entity.getEventType(),
            entity.getSeverity(),
            entity.getProject(),
            entity.getJobType(),
            entity.getSilenceMinutes(),
            entity.getRecipients(),
            entity.getChannelIds(),
            entity.getDefaultRule(),
            entity.getEnabled(),
            entity.getCreateTime(),
            entity.getUpdateTime()
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
