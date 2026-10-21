package com.data.datafusion.service.alert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.AlertRule;
import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.repository.AlertRuleRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertRuleServiceTest {

    @Mock
    private AlertRuleRepository alertRuleRepository;

    @InjectMocks
    private AlertRuleService alertRuleService;

    @Test
    void customRuleTakesPrecedenceOverDefaultRule() {
        AlertRule defaultRule = rule("default-rule", true, null, null, "FAILED,TIMEOUT");
        AlertRule customRule = rule("custom-rule", false, "project-a", "SQL", "FAILED");
        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(defaultRule, customRule));

        List<AlertRule> result = alertRuleService.findMatching(instance("project-a", "SQL", "FAILED"));

        assertThat(result).extracting(AlertRule::getId).containsExactly("custom-rule");
    }

    @Test
    void fallsBackToDefaultRuleWhenNoCustomRuleMatches() {
        AlertRule defaultRule = rule("default-rule", true, null, null, "FAILED,TIMEOUT");
        AlertRule unrelatedRule = rule("custom-rule", false, "project-b", "SQL", "FAILED");
        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(defaultRule, unrelatedRule));

        List<AlertRule> result = alertRuleService.findMatching(instance("project-a", "SQL", "FAILED"));

        assertThat(result).extracting(AlertRule::getId).containsExactly("default-rule");
    }

    @Test
    void doesNotMatchProjectTypeOrEvent() {
        AlertRule failedOnlySqlRule = rule("custom-rule", false, "project-a", "SQL", "FAILED");
        when(alertRuleRepository.findByEnabledTrue()).thenReturn(List.of(failedOnlySqlRule));

        assertThat(alertRuleService.findMatching(instance("project-a", "SHELL", "FAILED"))).isEmpty();
        assertThat(alertRuleService.findMatching(instance("project-a", "SQL", "TIMEOUT"))).isEmpty();
        assertThat(alertRuleService.findMatching(instance("project-b", "SQL", "FAILED"))).isEmpty();
    }

    private AlertRule rule(String id, boolean defaultRule, String project, String jobType, String eventType) {
        AlertRule rule = new AlertRule();
        rule.setId(id);
        rule.setSourceType(AlertRule.SOURCE_JOB);
        rule.setEventType(eventType);
        rule.setProject(project);
        rule.setJobType(jobType);
        rule.setDefaultRule(defaultRule);
        rule.setEnabled(Boolean.TRUE);
        return rule;
    }

    private JobInstance instance(String project, String type, String status) {
        JobInstance instance = new JobInstance();
        instance.setTenantId("1");
        instance.setJobCode("job-1");
        instance.setJobName("任务一");
        instance.setProject(project);
        instance.setType(type);
        instance.setStatus(status);
        instance.setInstanceCode("instance-1");
        return instance;
    }
}
