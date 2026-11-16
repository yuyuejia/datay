package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.data.datafusion.config.DeploymentProperties;
import com.data.datafusion.config.StatusStorageConfig;
import com.data.datafusion.domain.ServiceConfig;
import com.data.datafusion.repository.ServiceConfigRepository;
import com.data.datafusion.security.TenantContext;
import com.data.status.StatusStorageStrategy;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StatusStorageConfigServiceTest {

    private final ServiceConfigRepository repository = mock(ServiceConfigRepository.class);

    private final StatusStorageConfigService service = new StatusStorageConfigService(repository);

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void getConfigUsesDefaultsWhenNoRowsExist() {
        when(repository.findByDfGroup(StatusStorageConfigService.CONFIG_GROUP)).thenReturn(List.of());

        StatusStorageConfig config = service.getConfig();

        assertThat(config.getType()).isEqualTo("local");
        assertThat(config.getLocalBasePath()).isEqualTo("./log");
        assertThat(config.getMinioEndpoint()).isEmpty();
        assertThat(config.getMinioBucketName()).isEqualTo("datay-status");
        verify(repository).findByDfGroup(StatusStorageConfigService.CONFIG_GROUP);
    }

    @Test
    void getConfigReadsAllValuesInOneQuery() {
        when(repository.findByDfGroup(StatusStorageConfigService.CONFIG_GROUP))
            .thenReturn(
                List.of(
                    configItem(StatusStorageConfigService.KEY_TYPE, "minio"),
                    configItem(StatusStorageConfigService.KEY_LOCAL_BASE_PATH, "/tmp/state"),
                    configItem(StatusStorageConfigService.KEY_MINIO_ENDPOINT, "http://minio:9000"),
                    configItem(StatusStorageConfigService.KEY_MINIO_ACCESS_KEY, "access"),
                    configItem(StatusStorageConfigService.KEY_MINIO_SECRET_KEY, "secret"),
                    configItem(StatusStorageConfigService.KEY_MINIO_BUCKET_NAME, "bucket")
                )
            );

        StatusStorageConfig config = service.getConfig();

        assertThat(config.getType()).isEqualTo("minio");
        assertThat(config.getLocalBasePath()).isEqualTo("/tmp/state");
        assertThat(config.getMinioEndpoint()).isEqualTo("http://minio:9000");
        assertThat(config.getMinioAccessKey()).isEqualTo("access");
        assertThat(config.getMinioSecretKey()).isEqualTo("secret");
        assertThat(config.getMinioBucketName()).isEqualTo("bucket");
        verify(repository).findByDfGroup(StatusStorageConfigService.CONFIG_GROUP);
    }

    @Test
    void getConfigForTenantSwitchesAndRestoresTenantContext() {
        TenantContext.setTenantId(2L);
        TenantContext.setTenantCode("tenant-2");
        when(repository.findByDfGroup(StatusStorageConfigService.CONFIG_GROUP))
            .thenAnswer(invocation -> {
                assertThat(TenantContext.getTenantId()).isEqualTo(1L);
                return List.of(configItem(StatusStorageConfigService.KEY_TYPE, "minio"));
            });

        StatusStorageConfig config = service.getConfigForTenant("1");

        assertThat(config.getType()).isEqualTo("minio");
        assertThat(TenantContext.getTenantId()).isEqualTo(2L);
        assertThat(TenantContext.getTenantCode()).isEqualTo("tenant-2");
    }

    @Test
    void pureWorkerSkipsConfigPreload() throws Exception {
        DeploymentProperties deployment = new DeploymentProperties();
        deployment.setMode("worker");
        Field field = StatusStorageConfigService.class.getDeclaredField("deploymentProperties");
        field.setAccessible(true);
        field.set(service, deployment);

        service.init();

        verifyNoInteractions(repository);
    }

    @Test
    void getStrategyReusesStrategyForSameConfig() {
        StatusStorageConfig config = new StatusStorageConfig();
        config.setType("local");
        config.setLocalBasePath("/tmp/status");

        StatusStorageStrategy first = service.getStrategy(config);
        StatusStorageStrategy second = service.getStrategy(config);

        assertThat(second).isSameAs(first);
    }

    @Test
    void getConfigCachesResultAndReturnsDefensiveCopy() {
        when(repository.findByDfGroup(StatusStorageConfigService.CONFIG_GROUP))
            .thenReturn(
                List.of(
                    configItem(StatusStorageConfigService.KEY_TYPE, "minio"),
                    configItem(StatusStorageConfigService.KEY_MINIO_ENDPOINT, "http://minio:9000")
                )
            );

        StatusStorageConfig first = service.getConfig();
        first.setType("local");
        first.setMinioEndpoint("http://mutated");

        StatusStorageConfig second = service.getConfig();

        assertThat(second.getType()).isEqualTo("minio");
        assertThat(second.getMinioEndpoint()).isEqualTo("http://minio:9000");
        verify(repository, times(1)).findByDfGroup(StatusStorageConfigService.CONFIG_GROUP);
    }

    @Test
    void getConfigForTenantCachesIndependentlyPerTenant() {
        when(repository.findByDfGroup(StatusStorageConfigService.CONFIG_GROUP))
            .thenAnswer(invocation -> {
                Long tenantId = TenantContext.getTenantId();
                return List.of(
                    configItem(StatusStorageConfigService.KEY_TYPE, "tenant-" + tenantId),
                    configItem(StatusStorageConfigService.KEY_MINIO_ENDPOINT, "endpoint-" + tenantId)
                );
            });

        StatusStorageConfig tenant1 = service.getConfigForTenant("1");
        StatusStorageConfig tenant1Again = service.getConfigForTenant("1");
        StatusStorageConfig tenant2 = service.getConfigForTenant("2");

        assertThat(tenant1.getType()).isEqualTo("tenant-1");
        assertThat(tenant1Again.getType()).isEqualTo("tenant-1");
        assertThat(tenant2.getType()).isEqualTo("tenant-2");
        assertThat(tenant2.getMinioEndpoint()).isEqualTo("endpoint-2");
        verify(repository, times(2)).findByDfGroup(StatusStorageConfigService.CONFIG_GROUP);
    }

    @Test
    void updateAndReloadRefreshesCachedConfig() {
        when(repository.findByDfGroup(StatusStorageConfigService.CONFIG_GROUP))
            .thenReturn(List.of(configItem(StatusStorageConfigService.KEY_TYPE, "local")));
        when(repository.findByDfGroupAndDfKey(anyString(), anyString())).thenReturn(Optional.empty());

        // 先填充旧配置缓存
        assertThat(service.getConfig().getLocalBasePath()).isEqualTo("./log");

        StatusStorageConfig updated = new StatusStorageConfig();
        updated.setType("local");
        updated.setLocalBasePath("/tmp/new-status");
        service.updateAndReload(updated);

        StatusStorageConfig actual = service.getConfig();
        assertThat(actual.getType()).isEqualTo("local");
        assertThat(actual.getLocalBasePath()).isEqualTo("/tmp/new-status");
        verify(repository, times(1)).findByDfGroup(StatusStorageConfigService.CONFIG_GROUP);
    }

    @Test
    void configCacheCanBeDisabledWithZeroTtl() throws Exception {
        Field ttlField = StatusStorageConfigService.class.getDeclaredField("configCacheTtlSeconds");
        ttlField.setAccessible(true);
        ttlField.setLong(service, 0);
        when(repository.findByDfGroup(StatusStorageConfigService.CONFIG_GROUP)).thenReturn(List.of());

        service.getConfig();
        service.getConfig();

        verify(repository, times(2)).findByDfGroup(StatusStorageConfigService.CONFIG_GROUP);
    }

    private static ServiceConfig configItem(String key, String value) {
        ServiceConfig item = new ServiceConfig();
        item.setDfGroup(StatusStorageConfigService.CONFIG_GROUP);
        item.setDfKey(key);
        item.setDfValue(value);
        return item;
    }
}
