package com.data.datafusion.ai.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.ServiceConfig;
import com.data.datafusion.repository.ServiceConfigRepository;
import com.data.datafusion.security.TenantContext;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link LlmConfigService} 单元测试：验证服务配置优先、yml 默认值回退与落库。
 */
class LlmConfigServiceTest {

    private ServiceConfigRepository serviceConfigRepository;

    private AiProperties defaults;

    private LlmConfigService service;

    @BeforeEach
    void setUp() {
        serviceConfigRepository = mock(ServiceConfigRepository.class);
        defaults = new AiProperties();
        defaults.setBaseUrl("https://api.openai.com/v1");
        defaults.setApiKey("sk-default");
        defaults.setModel("gpt-4o-mini");
        defaults.setTemperature(0.2);
        service = new LlmConfigService(serviceConfigRepository, defaults);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void stubValue(String key, String value) {
        ServiceConfig sc = new ServiceConfig();
        sc.setDfGroup(LlmConfigService.CONFIG_GROUP);
        sc.setDfKey(key);
        sc.setDfValue(value);
        when(serviceConfigRepository.findByDfGroupAndDfKey(LlmConfigService.CONFIG_GROUP, key)).thenReturn(Optional.of(sc));
    }

    private void stubTenantValue(String key, String tenantId, String value) {
        ServiceConfig sc = new ServiceConfig();
        sc.setDfGroup(LlmConfigService.CONFIG_GROUP);
        sc.setDfKey(key);
        sc.setDfValue(value);
        sc.setTenantId(tenantId);
        when(serviceConfigRepository.findByDfGroupAndDfKeyAndTenantId(LlmConfigService.CONFIG_GROUP, key, tenantId)).thenReturn(
            Optional.of(sc)
        );
    }

    @Test
    void shouldFallBackToPropertiesDefaults() {
        LlmConfig config = service.getConfig();

        assertThat(config.getBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(config.getApiKey()).isEqualTo("sk-default");
        assertThat(config.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(config.isConfigured()).isTrue();
    }

    @Test
    void shouldPreferServiceConfigOverDefaults() {
        stubValue(LlmConfigService.KEY_BASE_URL, "https://api.deepseek.com");
        stubValue(LlmConfigService.KEY_API_KEY, "sk-db");
        stubValue(LlmConfigService.KEY_MODEL, "deepseek-chat");
        stubValue(LlmConfigService.KEY_TEMPERATURE, "0.5");
        stubValue(LlmConfigService.KEY_TIMEOUT_SECONDS, "30");
        stubValue(LlmConfigService.KEY_ENABLED, "false");

        LlmConfig config = service.getConfig();

        assertThat(config.getBaseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(config.getApiKey()).isEqualTo("sk-db");
        assertThat(config.getModel()).isEqualTo("deepseek-chat");
        assertThat(config.getTemperature()).isEqualTo(0.5);
        assertThat(config.getTimeoutSeconds()).isEqualTo(30);
        assertThat(config.isConfigured()).isFalse();
    }

    @Test
    void shouldPreferTenantConfigAndFallbackToSystem() {
        TenantContext.setTenantId(7L);
        stubTenantValue(LlmConfigService.KEY_MODEL, "7", "deepseek-chat");

        LlmConfig config = service.getConfig();

        assertThat(config.getModel()).isEqualTo("deepseek-chat");
        assertThat(config.getBaseUrl()).isEqualTo("https://api.openai.com/v1");
        assertThat(config.getApiKey()).isEqualTo("sk-default");
    }

    @Test
    void shouldHideSystemApiKeyInDisplayConfig() {
        TenantContext.setTenantId(7L);
        stubTenantValue(LlmConfigService.KEY_MODEL, "7", "deepseek-chat");

        LlmConfig display = service.getConfigForDisplay();

        assertThat(display.getApiKey()).isEmpty();
        assertThat(display.getModel()).isEqualTo("deepseek-chat");
        assertThat(display.getBaseUrl()).isEqualTo("https://api.openai.com/v1");
    }

    @Test
    void shouldShowTenantApiKeyInDisplayConfig() {
        TenantContext.setTenantId(7L);
        stubTenantValue(LlmConfigService.KEY_API_KEY, "7", "sk-tenant");

        assertThat(service.getConfigForDisplay().getApiKey()).isEqualTo("sk-tenant");
    }

    @Test
    void shouldIgnoreInvalidNumbers() {
        stubValue(LlmConfigService.KEY_TIMEOUT_SECONDS, "abc");

        assertThat(service.getConfig().getTimeoutSeconds()).isEqualTo(defaults.getTimeoutSeconds());
    }

    @Test
    void shouldPersistConfigForCurrentTenant() {
        TenantContext.setTenantId(9L);
        when(serviceConfigRepository.findByDfGroupAndDfKeyAndTenantId(any(), any(), any())).thenReturn(Optional.empty());

        LlmConfig config = new LlmConfig();
        config.setBaseUrl("https://api.deepseek.com");
        config.setApiKey("sk-db");
        config.setModel("deepseek-chat");
        config.setMaxToolRounds(3);
        service.saveConfig(config);

        org.mockito.ArgumentCaptor<ServiceConfig> captor = org.mockito.ArgumentCaptor.forClass(ServiceConfig.class);
        org.mockito.Mockito.verify(serviceConfigRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertThat(captor.getAllValues())
            .allMatch(sc -> "9".equals(sc.getTenantId()))
            .anyMatch(sc -> sc.getDfGroup().equals(LlmConfigService.CONFIG_GROUP) && "api-key".equals(sc.getDfKey()) && "sk-db".equals(sc.getDfValue()))
            .anyMatch(sc -> "model".equals(sc.getDfKey()) && "deepseek-chat".equals(sc.getDfValue()));
    }
}
