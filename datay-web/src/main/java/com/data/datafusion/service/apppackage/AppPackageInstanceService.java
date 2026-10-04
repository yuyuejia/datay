package com.data.datafusion.service.apppackage;

import com.data.datafusion.domain.AppPackageInstance;
import com.data.datafusion.repository.AppPackageInstanceRepository;
import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.service.dto.AppPackageInitResultDTO;
import com.data.datafusion.service.dto.AppPackageInstanceDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 数据应用初始化记录服务。
 *
 * <p>初始化失败时要留下痕迹，但失败会把初始化事务整体回滚，因此记录写入使用
 * {@link Propagation#REQUIRES_NEW} 独立事务，保证回滚后记录仍然存在。
 */
@Service
public class AppPackageInstanceService {

    private static final Logger LOG = LoggerFactory.getLogger(AppPackageInstanceService.class);

    private final AppPackageInstanceRepository appPackageInstanceRepository;

    public AppPackageInstanceService(AppPackageInstanceRepository appPackageInstanceRepository) {
        this.appPackageInstanceRepository = appPackageInstanceRepository;
    }

    /**
     * 记录一次初始化结果。
     *
     * @param packageId   资产包 ID
     * @param packageCode 资产包编码
     * @param packageName 资产包名称
     * @param result      初始化结果，失败时可为 null
     * @param message     结果说明
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long packageId, String packageCode, String packageName, AppPackageInitResultDTO result, String message) {
        AppPackageInstance entity = new AppPackageInstance();
        entity.setPackageId(packageId);
        entity.setPackageCode(packageCode);
        entity.setPackageName(packageName);
        entity.setCreateUser(SecurityUtils.getCurrentUserLogin().orElse("system"));
        entity.setCreateTime(ZonedDateTime.now());
        if (result == null) {
            entity.setStatus(AppPackageInstance.STATUS_FAILED);
            entity.setMessage(message);
        } else {
            entity.setStatus(AppPackageInstance.STATUS_SUCCESS);
            entity.setMessage(message);
            Map<String, Object> mapping = new LinkedHashMap<>();
            mapping.put("counts", result.counts);
            mapping.put("dataSources", result.dataSources);
            mapping.put("idMapping", result.idMapping);
            mapping.put("warnings", result.warnings);
            entity.setIdMapping(AppPackageJson.write(mapping));
        }
        try {
            appPackageInstanceRepository.save(entity);
        } catch (RuntimeException e) {
            LOG.warn("Failed to record app package instance: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<AppPackageInstanceDTO> findAll(Pageable pageable) {
        String tenantId = currentTenantId();
        return appPackageInstanceRepository.findByTenantIdOrderByIdDesc(tenantId, pageable).map(this::toDto);
    }

    private AppPackageInstanceDTO toDto(AppPackageInstance entity) {
        AppPackageInstanceDTO dto = new AppPackageInstanceDTO();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setPackageId(entity.getPackageId());
        dto.setPackageCode(entity.getPackageCode());
        dto.setPackageName(entity.getPackageName());
        dto.setStatus(entity.getStatus());
        dto.setMessage(entity.getMessage());
        dto.setCreateUser(entity.getCreateUser());
        dto.setCreateTime(entity.getCreateTime());
        if (entity.getIdMapping() != null) {
            try {
                dto.setMapping(AppPackageJson.mapper().readValue(entity.getIdMapping(), new TypeReference<Map<String, Object>>() {}));
            } catch (Exception e) {
                LOG.debug("Failed to parse app package instance mapping: {}", e.getMessage());
            }
        }
        return dto;
    }

    private String currentTenantId() {
        return SecurityUtils.getCurrentTenantId().map(String::valueOf).orElse(null);
    }
}
