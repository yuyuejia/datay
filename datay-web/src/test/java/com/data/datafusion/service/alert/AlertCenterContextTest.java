package com.data.datafusion.service.alert;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.IntegrationTest;
import com.data.datafusion.repository.AlertChannelRepository;
import com.data.datafusion.repository.AlertEventRepository;
import com.data.datafusion.repository.AlertRuleRepository;
import com.data.datafusion.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 验证告警中心表结构、实体映射和 Spring Data 仓库可以被正常加载。
 */
@IntegrationTest
class AlertCenterContextTest {

    @Autowired
    private AlertChannelRepository alertChannelRepository;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private AlertEventRepository alertEventRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void contextLoads() {
        assertThat(alertChannelRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(alertRuleRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(alertEventRepository.count()).isGreaterThanOrEqualTo(0);
        assertThat(notificationRepository.count()).isGreaterThanOrEqualTo(0);
    }
}
