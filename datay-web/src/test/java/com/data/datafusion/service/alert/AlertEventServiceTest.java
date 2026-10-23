package com.data.datafusion.service.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.AlertEvent;
import com.data.datafusion.domain.AlertRule;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.AlertDeliveryRepository;
import com.data.datafusion.repository.AlertEventRepository;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertEventServiceTest {

    @Mock
    private AlertEventRepository alertEventRepository;

    @Mock
    private AlertDeliveryRepository alertDeliveryRepository;

    @Mock
    private AlertRuleService alertRuleService;

    @Mock
    private AlertDispatchService alertDispatchService;

    @InjectMocks
    private AlertEventService alertEventService;

    @Test
    void createsEventAndSchedulesDispatch() {
        AlertRule rule = rule();
        JobInstance instance = instance(TaskConstants.TASK_STATUS_FAILED, null);
        when(alertRuleService.findMatching(instance)).thenReturn(List.of(rule));
        when(alertEventRepository.findFirstByFingerprintAndStatusInOrderByLastSeenDesc(anyString(), anyCollection())).thenReturn(
            Optional.empty()
        );
        when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(invocation -> {
            AlertEvent event = invocation.getArgument(0);
            event.setId("event-1");
            return event;
        });

        alertEventService.onJobInstanceStatus(instance);

        ArgumentCaptor<AlertEvent> captor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(alertEventRepository).save(captor.capture());
        AlertEvent saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo("1");
        assertThat(saved.getJobCode()).isEqualTo("job-1");
        assertThat(saved.getEventType()).isEqualTo(TaskConstants.TASK_STATUS_FAILED);
        assertThat(saved.getStatus()).isEqualTo(AlertEvent.STATUS_FIRING);
        assertThat(saved.getEventCount()).isEqualTo(1);
        assertThat(saved.getFingerprint()).hasSize(64);
        assertThat(saved.getContent()).contains("任务名称：任务一").doesNotContain("项目：");
        assertThat(saved.getContent()).contains("错误日志：");
        verify(alertDispatchService).dispatch("1", "event-1");
    }

    @Test
    void includesFailureLogInContent() {
        AlertRule rule = rule();
        JobInstance instance = instance(TaskConstants.TASK_STATUS_FAILED, null);
        instance.setJobMessage("2026-10-23 10:00:00 - [Component] - [ERROR] 组件执行失败：数据库连接超时");
        when(alertRuleService.findMatching(instance)).thenReturn(List.of(rule));
        when(alertEventRepository.findFirstByFingerprintAndStatusInOrderByLastSeenDesc(anyString(), anyCollection())).thenReturn(
            Optional.empty()
        );
        when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(invocation -> {
            AlertEvent event = invocation.getArgument(0);
            event.setId("event-1");
            return event;
        });

        alertEventService.onJobInstanceStatus(instance);

        ArgumentCaptor<AlertEvent> captor = ArgumentCaptor.forClass(AlertEvent.class);
        verify(alertEventRepository).save(captor.capture());
        assertThat(captor.getValue().getContent()).contains("错误日志：").contains("数据库连接超时");
    }

    @Test
    void suppressesDispatchWithinSilenceWindowButAccumulatesCount() {
        AlertRule rule = rule();
        JobInstance instance = instance(TaskConstants.TASK_STATUS_FAILED, null);
        AlertEvent existing = event(AlertEvent.STATUS_FIRING, ZonedDateTime.now().minusMinutes(5));
        when(alertRuleService.findMatching(instance)).thenReturn(List.of(rule));
        when(alertEventRepository.findFirstByFingerprintAndStatusInOrderByLastSeenDesc(anyString(), anyCollection())).thenReturn(
            Optional.of(existing)
        );
        when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        alertEventService.onJobInstanceStatus(instance);

        assertThat(existing.getEventCount()).isEqualTo(2);
        assertThat(existing.getStatus()).isEqualTo(AlertEvent.STATUS_FIRING);
        verify(alertDispatchService, never()).dispatch(anyString(), anyString());
    }

    @Test
    void ignoresDuplicateStatusEventForSameJobInstance() {
        AlertRule rule = rule();
        JobInstance instance = instance(TaskConstants.TASK_STATUS_FAILED, null);
        instance.setId("instance-id-1");
        AlertEvent existing = event(AlertEvent.STATUS_FIRING, ZonedDateTime.now().minusMinutes(5));
        existing.setJobInstanceId("instance-id-1");
        when(alertRuleService.findMatching(instance)).thenReturn(List.of(rule));
        when(alertEventRepository.findFirstByFingerprintAndStatusInOrderByLastSeenDesc(anyString(), anyCollection())).thenReturn(
            Optional.of(existing)
        );

        alertEventService.onJobInstanceStatus(instance);

        assertThat(existing.getEventCount()).isEqualTo(1);
        verify(alertEventRepository, never()).save(any(AlertEvent.class));
        verify(alertDispatchService, never()).dispatch(anyString(), anyString());
    }

    @Test
    void dispatchesAgainAfterSilenceWindow() {
        AlertRule rule = rule();
        JobInstance instance = instance(TaskConstants.TASK_STATUS_FAILED, null);
        AlertEvent existing = event(AlertEvent.STATUS_ACKNOWLEDGED, ZonedDateTime.now().minusMinutes(60));
        when(alertRuleService.findMatching(instance)).thenReturn(List.of(rule));
        when(alertEventRepository.findFirstByFingerprintAndStatusInOrderByLastSeenDesc(anyString(), anyCollection())).thenReturn(
            Optional.of(existing)
        );
        when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        alertEventService.onJobInstanceStatus(instance);

        assertThat(existing.getEventCount()).isEqualTo(2);
        assertThat(existing.getStatus()).isEqualTo(AlertEvent.STATUS_ACKNOWLEDGED);
        verify(alertDispatchService).dispatch("1", "event-1");
    }

    @Test
    void createsDefaultRuleWhenNoRuleMatches() {
        AlertRule rule = rule();
        JobInstance instance = instance(TaskConstants.TASK_STATUS_FAILED, null);
        when(alertRuleService.findMatching(instance)).thenReturn(List.of(), List.of(rule));
        when(alertEventRepository.findFirstByFingerprintAndStatusInOrderByLastSeenDesc(anyString(), anyCollection())).thenReturn(
            Optional.empty()
        );
        when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(invocation -> {
            AlertEvent event = invocation.getArgument(0);
            event.setId("event-1");
            return event;
        });

        alertEventService.onJobInstanceStatus(instance);

        verify(alertRuleService).ensureDefaultRule("1");
        verify(alertDispatchService).dispatch("1", "event-1");
    }

    @Test
    void ignoresSubJobFailure() {
        JobInstance instance = instance(TaskConstants.TASK_STATUS_FAILED, "parent-instance");

        alertEventService.onJobInstanceStatus(instance);

        verifyNoInteractions(alertRuleService, alertEventRepository, alertDispatchService);
    }

    @Test
    void resolvesOpenEventsWhenJobSucceeds() {
        JobInstance instance = instance(TaskConstants.TASK_STATUS_SUCCESSFUL, null);
        AlertEvent existing = event(AlertEvent.STATUS_FIRING, ZonedDateTime.now().minusMinutes(10));
        when(alertEventRepository.findByJobCodeAndStatusIn(eq("job-1"), anyCollection())).thenReturn(List.of(existing));
        when(alertEventRepository.save(any(AlertEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        alertEventService.onJobInstanceStatus(instance);

        assertThat(existing.getStatus()).isEqualTo(AlertEvent.STATUS_RESOLVED);
        assertThat(existing.getResolveBy()).isEqualTo("system");
        assertThat(existing.getResolveTime()).isNotNull();
        verify(alertEventRepository).save(existing);
    }

    private AlertRule rule() {
        AlertRule rule = new AlertRule();
        rule.setId("rule-1");
        rule.setSourceType(AlertRule.SOURCE_JOB);
        rule.setEventType("FAILED,TIMEOUT");
        rule.setSeverity(AlertRule.SEVERITY_P1);
        rule.setSilenceMinutes(30);
        rule.setRecipients("[\"role:ROLE_TENANT_ADMIN\"]");
        rule.setChannelIds("[]");
        rule.setEnabled(Boolean.TRUE);
        rule.setDefaultRule(Boolean.FALSE);
        return rule;
    }

    private AlertEvent event(String status, ZonedDateTime lastSeen) {
        AlertEvent event = new AlertEvent();
        event.setId("event-1");
        event.setTenantId("1");
        event.setStatus(status);
        event.setEventCount(1);
        event.setFirstSeen(lastSeen);
        event.setLastSeen(lastSeen);
        return event;
    }

    private JobInstance instance(String status, String parentInstanceCode) {
        JobInstance instance = new JobInstance();
        instance.setTenantId("1");
        instance.setJobCode("job-1");
        instance.setJobName("任务一");
        instance.setType(TaskConstants.TASK_TYPE_SQL);
        instance.setProject("project-a");
        instance.setStatus(status);
        instance.setInstanceCode("instance-1");
        instance.setParentInstanceCode(parentInstanceCode);
        return instance;
    }
}
