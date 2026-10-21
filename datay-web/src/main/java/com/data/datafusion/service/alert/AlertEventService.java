package com.data.datafusion.service.alert;

import com.data.datafusion.domain.AlertDelivery;
import com.data.datafusion.domain.AlertEvent;
import com.data.datafusion.domain.AlertRule;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.AlertDeliveryRepository;
import com.data.datafusion.repository.AlertEventRepository;
import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.dto.AlertDeliveryDTO;
import com.data.datafusion.service.dto.AlertEventDTO;
import jakarta.persistence.criteria.Predicate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 告警事件服务：任务终态接入、规则匹配、去重、认领与恢复。
 */
@Service
public class AlertEventService {

    private static final Logger LOG = LoggerFactory.getLogger(AlertEventService.class);

    private static final List<String> OPEN_STATUSES = List.of(AlertEvent.STATUS_FIRING, AlertEvent.STATUS_ACKNOWLEDGED);

    private final AlertEventRepository alertEventRepository;

    private final AlertDeliveryRepository alertDeliveryRepository;

    private final AlertRuleService alertRuleService;

    private final AlertDispatchService alertDispatchService;

    public AlertEventService(
        AlertEventRepository alertEventRepository,
        AlertDeliveryRepository alertDeliveryRepository,
        AlertRuleService alertRuleService,
        AlertDispatchService alertDispatchService
    ) {
        this.alertEventRepository = alertEventRepository;
        this.alertDeliveryRepository = alertDeliveryRepository;
        this.alertRuleService = alertRuleService;
        this.alertDispatchService = alertDispatchService;
    }

    /**
     * 任务终态事件入口。异步执行，任何异常都不能影响任务事件循环。
     */
    @Async("alertTaskExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onJobInstanceStatus(JobInstance instance) {
        if (instance == null || instance.getTenantId() == null || instance.getTenantId().isBlank()) {
            return;
        }
        setTenantContext(instance.getTenantId());
        try {
            String status = instance.getStatus();
            if (TaskConstants.TASK_STATUS_SUCCESSFUL.equals(status) || "SUCCESS".equals(status)) {
                resolveByJobCode(instance.getJobCode(), "system");
                return;
            }
            if (!TaskConstants.TASK_STATUS_FAILED.equals(status) && !TaskConstants.TASK_STATUS_TIMEOUT.equals(status)) {
                return;
            }
            // 仅顶层任务告警，DAG 子任务由父任务统一告警，避免重复。
            if (instance.getParentInstanceCode() != null && !instance.getParentInstanceCode().isBlank()) {
                return;
            }

            List<AlertRule> rules = alertRuleService.findMatching(instance);
            if (rules.isEmpty()) {
                alertRuleService.ensureDefaultRule(instance.getTenantId());
                rules = alertRuleService.findMatching(instance);
            }
            if (rules.isEmpty()) {
                LOG.debug("No alert rule matched job failure: tenant={}, job={}", instance.getTenantId(), instance.getJobCode());
                return;
            }
            for (AlertRule rule : rules) {
                upsertEvent(instance, rule);
            }
        } catch (Exception e) {
            LOG.error("Failed to handle job alert, tenant={}, instance={}", instance.getTenantId(), instance.getInstanceCode(), e);
        } finally {
            TenantContext.clear();
        }
    }

    private void upsertEvent(JobInstance instance, AlertRule rule) {
        ZonedDateTime now = ZonedDateTime.now();
        String fingerprint = fingerprint(instance.getTenantId(), rule.getId(), instance.getJobCode(), instance.getStatus());
        Optional<AlertEvent> existing = alertEventRepository.findFirstByFingerprintAndStatusInOrderByLastSeenDesc(
            fingerprint,
            OPEN_STATUSES
        );
        int silenceMinutes = rule.getSilenceMinutes() == null ? 30 : Math.max(0, rule.getSilenceMinutes());

        if (existing.isPresent()) {
            AlertEvent event = existing.get();
            if (isSameInstance(event, instance)) {
                // 同一实例可能因任务状态事件被重复提交而再次进入这里，不能把重复事件累计成多次失败。
                LOG.debug(
                    "Ignore duplicate alert status for job instance: event={}, instance={}",
                    event.getId(),
                    instance.getInstanceCode()
                );
                return;
            }
            ZonedDateTime previousLastSeen = event.getLastSeen();
            boolean shouldNotify =
                previousLastSeen == null || !previousLastSeen.plusMinutes(silenceMinutes).isAfter(now);
            event.setEventCount(event.getEventCount() == null ? 2 : event.getEventCount() + 1);
            event.setLastSeen(now);
            event.setUpdateTime(now);
            event.setTitle(buildTitle(instance, rule));
            event.setContent(buildContent(instance));
            event.setJobInstanceId(instance.getId());
            event.setInstanceCode(instance.getInstanceCode());
            event.setProject(instance.getProject());
            alertEventRepository.save(event);
            if (shouldNotify) {
                scheduleDispatch(event.getTenantId(), event.getId());
            }
            return;
        }

        AlertEvent event = new AlertEvent();
        event.setTenantId(instance.getTenantId());
        event.setRuleId(rule.getId());
        event.setFingerprint(fingerprint);
        event.setSourceType(AlertRule.SOURCE_JOB);
        event.setEventType(instance.getStatus());
        event.setSeverity(rule.getSeverity());
        event.setTitle(buildTitle(instance, rule));
        event.setContent(buildContent(instance));
        event.setJobCode(instance.getJobCode());
        event.setJobName(instance.getJobName());
        event.setJobInstanceId(instance.getId());
        event.setInstanceCode(instance.getInstanceCode());
        event.setProject(instance.getProject());
        event.setStatus(AlertEvent.STATUS_FIRING);
        event.setEventCount(1);
        event.setFirstSeen(now);
        event.setLastSeen(now);
        event.setRecipients(rule.getRecipients());
        event.setChannelIds(rule.getChannelIds());
        event.setCreateTime(now);
        event.setUpdateTime(now);
        event = alertEventRepository.save(event);
        scheduleDispatch(event.getTenantId(), event.getId());
    }

    private String buildTitle(JobInstance instance, AlertRule rule) {
        String action = TaskConstants.TASK_STATUS_TIMEOUT.equals(instance.getStatus()) ? "任务超时" : "任务失败";
        String name = instance.getJobName() == null || instance.getJobName().isBlank() ? instance.getJobCode() : instance.getJobName();
        return "[" + rule.getSeverity() + "] " + action + "：" + name;
    }

    private String buildContent(JobInstance instance) {
        StringBuilder content = new StringBuilder();
        content.append("任务名称：").append(value(instance.getJobName())).append('\n');
        content.append("任务编码：").append(value(instance.getJobCode())).append('\n');
        content.append("任务类型：").append(value(instance.getType())).append('\n');
        content.append("实例编码：").append(value(instance.getInstanceCode())).append('\n');
        content.append("执行节点：").append(value(instance.getExecNode())).append('\n');
        content.append("开始时间：").append(value(instance.getStartTime())).append('\n');
        content.append("结束时间：").append(value(instance.getEndTime())).append('\n');
        content.append("状态：").append(value(instance.getStatus())).append('\n');
        content.append("信息：").append(truncate(value(instance.getJobMessage()), 4000));
        return content.toString();
    }

    private void resolveByJobCode(String jobCode, String operator) {
        if (jobCode == null || jobCode.isBlank()) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now();
        List<AlertEvent> events = alertEventRepository.findByJobCodeAndStatusIn(jobCode, OPEN_STATUSES);
        for (AlertEvent event : events) {
            event.setStatus(AlertEvent.STATUS_RESOLVED);
            event.setResolveBy(operator);
            event.setResolveTime(now);
            event.setUpdateTime(now);
            alertEventRepository.save(event);
            LOG.info("Alert event auto resolved: event={}, job={}", event.getId(), jobCode);
        }
    }

    @Transactional
    public Optional<AlertEventDTO> acknowledge(String id) {
        return alertEventRepository
            .findById(id)
            .map(event -> {
                if (!AlertEvent.STATUS_RESOLVED.equals(event.getStatus())) {
                    event.setStatus(AlertEvent.STATUS_ACKNOWLEDGED);
                    event.setAckBy(SecurityUtils.getCurrentUserLogin().orElse("system"));
                    event.setAckTime(ZonedDateTime.now());
                    event.setUpdateTime(ZonedDateTime.now());
                    alertEventRepository.save(event);
                }
                return toDto(event);
            });
    }

    @Transactional
    public Optional<AlertEventDTO> resolve(String id) {
        return alertEventRepository
            .findById(id)
            .map(event -> {
                event.setStatus(AlertEvent.STATUS_RESOLVED);
                event.setResolveBy(SecurityUtils.getCurrentUserLogin().orElse("system"));
                event.setResolveTime(ZonedDateTime.now());
                event.setUpdateTime(ZonedDateTime.now());
                alertEventRepository.save(event);
                return toDto(event);
            });
    }

    @Transactional(readOnly = true)
    public Page<AlertEventDTO> findAll(Pageable pageable, String status, String severity, String sourceType, String search) {
        Specification<AlertEvent> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (severity != null && !severity.isBlank()) {
                predicates.add(cb.equal(root.get("severity"), severity));
            }
            if (sourceType != null && !sourceType.isBlank()) {
                predicates.add(cb.equal(root.get("sourceType"), sourceType));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                predicates.add(
                    cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("jobCode")), pattern),
                        cb.like(cb.lower(root.get("jobName")), pattern),
                        cb.like(cb.lower(root.get("instanceCode")), pattern)
                    )
                );
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
        return alertEventRepository.findAll(specification, pageable).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<AlertEventDTO> findOne(String id) {
        return alertEventRepository.findById(id).map(this::toDto);
    }

    @Transactional(readOnly = true)
    public List<AlertDeliveryDTO> findDeliveries(String eventId) {
        return alertDeliveryRepository.findByEventIdOrderByCreateTimeDesc(eventId).stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Map<String, Long> summary() {
        Map<String, Long> summary = new LinkedHashMap<>();
        summary.put("firing", alertEventRepository.countByStatusIn(List.of(AlertEvent.STATUS_FIRING)));
        summary.put("acknowledged", alertEventRepository.countByStatusIn(List.of(AlertEvent.STATUS_ACKNOWLEDGED)));
        summary.put("resolved", alertEventRepository.countByStatusIn(List.of(AlertEvent.STATUS_RESOLVED)));
        return summary;
    }

    private void scheduleDispatch(String tenantId, String eventId) {
        Runnable action = () -> {
            try {
                alertDispatchService.dispatch(tenantId, eventId);
            } catch (Exception e) {
                // 告警投递失败不能反向影响任务状态处理。
                LOG.error("Could not schedule alert dispatch, tenant={}, event={}", tenantId, eventId, e);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        action.run();
                    }
                }
            );
        } else {
            action.run();
        }
    }

    private boolean isSameInstance(AlertEvent event, JobInstance instance) {
        return instance.getId() != null && instance.getId().equals(event.getJobInstanceId());
    }

    private String fingerprint(String tenantId, String ruleId, String jobCode, String eventType) {
        String source = value(tenantId) + "|" + value(ruleId) + "|" + value(jobCode) + "|" + value(eventType);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not build alert fingerprint", e);
        }
    }

    private AlertEventDTO toDto(AlertEvent event) {
        return new AlertEventDTO(
            event.getId(),
            event.getRuleId(),
            event.getSourceType(),
            event.getEventType(),
            event.getSeverity(),
            event.getTitle(),
            event.getContent(),
            event.getJobCode(),
            event.getJobName(),
            event.getJobInstanceId(),
            event.getInstanceCode(),
            event.getProject(),
            event.getStatus(),
            event.getEventCount(),
            event.getFirstSeen(),
            event.getLastSeen(),
            event.getAckBy(),
            event.getAckTime(),
            event.getResolveBy(),
            event.getResolveTime(),
            event.getCreateTime(),
            event.getUpdateTime()
        );
    }

    private AlertDeliveryDTO toDto(AlertDelivery delivery) {
        return new AlertDeliveryDTO(
            delivery.getId(),
            delivery.getEventId(),
            delivery.getChannelId(),
            delivery.getChannelType(),
            delivery.getTarget(),
            delivery.getStatus(),
            delivery.getAttempt(),
            delivery.getErrorMessage(),
            delivery.getSentTime(),
            delivery.getCreateTime()
        );
    }

    private void setTenantContext(String tenantId) {
        try {
            TenantContext.setTenantId(Long.valueOf(tenantId));
        } catch (Exception e) {
            LOG.warn("Invalid tenant id for alert event: {}", tenantId);
        }
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
