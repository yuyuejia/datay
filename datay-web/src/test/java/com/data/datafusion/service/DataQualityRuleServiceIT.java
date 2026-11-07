package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.service.dto.DataQualityRuleDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
@WithMockUser
class DataQualityRuleServiceIT {

    @Autowired
    private DataQualityRuleService ruleService;

    @Test
    void shouldLoadBuiltinRulesAndTypes() {
        Page<DataQualityRuleDTO> page = ruleService.findAll(PageRequest.of(0, 50), null, null, null, null);
        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent()).anyMatch(rule -> Boolean.TRUE.equals(rule.getBuiltin()));
        assertThat(page.getContent()).anyMatch(rule -> "ROW_COUNT_MIN".equals(rule.getRuleType()));
        assertThat(page.getContent()).anyMatch(rule -> "NOT_NULL".equals(rule.getRuleType()));
        assertThat(page.getContent()).anyMatch(rule -> "CUSTOM_SQL".equals(rule.getRuleType()));
        assertThat(ruleService.listTypes()).isNotEmpty();
        assertThat(ruleService.listTypes()).anyMatch(type -> "CUSTOM_SQL".equals(type.get("code")));
    }
}
