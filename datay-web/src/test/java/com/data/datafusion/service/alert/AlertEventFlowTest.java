package com.data.datafusion.service.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.domain.AlertEvent;
import com.data.datafusion.domain.AlertRule;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.AlertChannelRepository;
import com.data.datafusion.repository.AlertDeliveryRepository;
import com.data.datafusion.repository.AlertEventRepository;
import com.data.datafusion.repository.AlertRuleRepository;
import com.data.datafusion.repository.NotificationRepository;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.dto.AlertRuleDTO;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 使用真实 Spring 上下文验证“任务失败 -> 规则匹配 -> 告警事件落库”的链路。
 */
@IntegrationTest
class AlertEventFlowTest {

    private static final Long TENANT_ID = 9_876_543_210L;

    @Autowired
    private AlertEventService alertEventService;

    @Autowired
    private AlertRuleService alertRuleService;

    @Autowired
    private AlertEventRepository alertEventRepository;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private AlertChannelRepository alertChannelRepository;

    @Autowired
    private AlertDeliveryRepository alertDeliveryRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @AfterEach
    void cleanup() {
        TenantContext.setTenantId(TENANT_ID);
        try {
            notificationRepository.deleteAll();
            alertDeliveryRepository.deleteAll();
            alertEventRepository.deleteAll();
            alertRuleRepository.deleteAll();
            alertChannelRepository.deleteAll();
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void createsAlertEventForTopLevelFailedJob() throws InterruptedException {
        TenantContext.setTenantId(TENANT_ID);
        String ruleCode = "flow_test_rule_" + System.nanoTime();
        AlertRuleDTO rule = alertRuleService.save(
            new AlertRuleDTO(
                null,
                "链路测试规则",
                ruleCode,
                "JOB",
                "FAILED,TIMEOUT",
                "P1",
                null,
                null,
                0,
                "[\"user:missing-user\"]",
                "[]",
                false,
                true,
                null,
                null
            )
        );

        alertEventService.onJobInstanceStatus(instance("flow-test-job"));

        AlertEvent event = awaitEvent("flow-test-job");
        assertThat(event).isNotNull();
        assertThat(event.getRuleId()).isEqualTo(rule.id());
        assertThat(event.getSeverity()).isEqualTo("P1");
        assertThat(event.getEventCount()).isEqualTo(1);
        assertThat(event.getFingerprint()).hasSize(64);
    }

    @Test
    void createsDefaultRuleWhenTenantHasNoRules() throws InterruptedException {
        TenantContext.setTenantId(TENANT_ID);

        alertEventService.onJobInstanceStatus(instance("flow-default-job"));

        AlertEvent event = awaitEvent("flow-default-job");
        assertThat(event).isNotNull();
        TenantContext.setTenantId(TENANT_ID);
        List<AlertRule> rules = alertRuleRepository
            .findAll()
            .stream()
            .filter(rule -> String.valueOf(TENANT_ID).equals(rule.getTenantId()))
            .toList();
        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getDefaultRule()).isTrue();
        assertThat(rules.get(0).getCode()).isEqualTo("default_job_failure");
        assertThat(event.getRuleId()).isEqualTo(rules.get(0).getId());
    }

    private AlertEvent awaitEvent(String jobCode) throws InterruptedException {
        AlertEvent event = null;
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline && event == null) {
            TenantContext.setTenantId(TENANT_ID);
            event = alertEventRepository
                .findByJobCodeAndStatusIn(jobCode, List.of(AlertEvent.STATUS_FIRING))
                .stream()
                .findFirst()
                .orElse(null);
            if (event == null) {
                Thread.sleep(100);
            }
        }
        return event;
    }

    private JobInstance instance(String jobCode) {
        JobInstance instance = new JobInstance();
        instance.setTenantId(String.valueOf(TENANT_ID));
        instance.setJobCode(jobCode);
        instance.setJobName("链路测试任务");
        instance.setType(TaskConstants.TASK_TYPE_SQL);
        instance.setStatus(TaskConstants.TASK_STATUS_FAILED);
        instance.setInstanceCode(jobCode + "-instance");
        return instance;
    }
}
