package com.data.datafusion.service;

import com.data.datafusion.domain.ServiceConfig;
import com.data.datafusion.repository.ServiceConfigRepository;
import com.data.datafusion.security.SecurityUtils;
import java.time.ZonedDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing the tenant default data warehouse, persisted in {@link ServiceConfig}.
 */
@Service
@Transactional
public class DefaultWarehouseConfigService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultWarehouseConfigService.class);

    public static final String CONFIG_GROUP = "data-warehouse";

    public static final String KEY_DEFAULT_DATASOURCE_ID = "default-datasource-id";

    private final ServiceConfigRepository serviceConfigRepository;

    public DefaultWarehouseConfigService(ServiceConfigRepository serviceConfigRepository) {
        this.serviceConfigRepository = serviceConfigRepository;
    }

    @Transactional(readOnly = true)
    public Optional<String> getDefaultDataSourceId() {
        String tenantId = currentTenantId();
        if (tenantId == null) {
            return Optional.empty();
        }
        return serviceConfigRepository
            .findByDfGroupAndDfKeyAndTenantId(CONFIG_GROUP, KEY_DEFAULT_DATASOURCE_ID, tenantId)
            .map(ServiceConfig::getDfValue)
            .filter(value -> value != null && !value.isBlank())
            .map(String::trim);
    }

    public void setDefaultDataSourceId(String dataSourceId) {
        String tenantId = currentTenantId();
        LOG.debug("Request to set default data source id : {} for tenant : {}", dataSourceId, tenantId);
        Optional<ServiceConfig> existing = tenantId == null
            ? Optional.empty()
            : serviceConfigRepository.findByDfGroupAndDfKeyAndTenantId(CONFIG_GROUP, KEY_DEFAULT_DATASOURCE_ID, tenantId);
        ServiceConfig serviceConfig;
        if (existing.isPresent()) {
            serviceConfig = existing.get();
        } else {
            serviceConfig = new ServiceConfig();
            serviceConfig.setDfGroup(CONFIG_GROUP);
            serviceConfig.setDfKey(KEY_DEFAULT_DATASOURCE_ID);
            serviceConfig.setCreateTime(ZonedDateTime.now());
        }
        serviceConfig.setDfValue(dataSourceId);
        serviceConfig.setTenantId(tenantId);
        serviceConfigRepository.save(serviceConfig);
    }

    private String currentTenantId() {
        return SecurityUtils.getCurrentTenantId().map(String::valueOf).orElse(null);
    }
}
