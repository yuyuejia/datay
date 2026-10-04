package com.data.datafusion.service.apppackage;

import com.data.datafusion.domain.AppPackage;
import com.data.datafusion.repository.AppPackageRepository;
import com.data.datafusion.service.apppackage.model.AppPackageContent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 系统预制资产包加载器。
 *
 * <p>启动时扫描 classpath 下的 {@code app-packages/*.json}，把每个场景资产包登记为
 * {@code packageType=SYSTEM} 的市场条目（{@code tenant_id} 为空，所有租户可见）。
 *
 * <p>以资产包编码为唯一键做幂等 upsert：程序升级带来新版本的资产包时，内容会随之刷新；
 * 租户已经基于旧版本初始化过的数据应用不受影响。
 *
 * <p>新增业务场景只需要往 {@code src/main/resources/app-packages/} 丢一个资产包 JSON，
 * 无需改动数据库脚本。
 */
@Component
public class SystemAppPackageLoader implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(SystemAppPackageLoader.class);

    private static final String LOCATION_PATTERN = "classpath*:app-packages/*.json";

    private final AppPackageRepository appPackageRepository;

    public SystemAppPackageLoader(AppPackageRepository appPackageRepository) {
        this.appPackageRepository = appPackageRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources(LOCATION_PATTERN);
        } catch (IOException e) {
            LOG.warn("Scan system app packages failed: {}", e.getMessage());
            return;
        }
        int loaded = 0;
        for (Resource resource : resources) {
            if (load(resource)) {
                loaded++;
            }
        }
        if (loaded > 0) {
            LOG.info("Loaded {} system app package(s) from {}", loaded, LOCATION_PATTERN);
        }
    }

    private boolean load(Resource resource) {
        try {
            String json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            AppPackageContent content = AppPackageJson.readContent(json);
            AppPackageContent.Meta meta = content.getMeta();
            String code = StringUtils.hasText(meta.getCode()) ? meta.getCode().trim() : resource.getFilename();
            if (!StringUtils.hasText(code)) {
                LOG.warn("System app package {} has no code, skipped", resource.getFilename());
                return false;
            }
            content.rebuildSummary();
            AppPackage entity = appPackageRepository.findByCodeAndPackageType(code, AppPackage.TYPE_SYSTEM).orElseGet(AppPackage::new);
            boolean creating = entity.getId() == null;
            entity.setTenantId(null);
            entity.setPackageType(AppPackage.TYPE_SYSTEM);
            entity.setCode(code);
            entity.setName(StringUtils.hasText(meta.getName()) ? meta.getName() : code);
            entity.setDescription(meta.getDescription());
            entity.setCategory(meta.getCategory());
            entity.setVersion(StringUtils.hasText(meta.getVersion()) ? meta.getVersion() : "1.0.0");
            entity.setStatus(AppPackage.STATUS_ENABLED);
            entity.setCreateUser("system");
            entity.setItemSummary(AppPackageJson.write(content.getSummary()));
            String normalized = AppPackageJson.write(content);
            entity.setContent(normalized);
            entity.setContentSize((long) normalized.length());
            ZonedDateTime now = ZonedDateTime.now();
            if (creating) {
                entity.setCreateTime(now);
            }
            entity.setUpdateTime(now);
            appPackageRepository.save(entity);
            LOG.debug("System app package {} loaded from {}", code, resource.getFilename());
            return true;
        } catch (Exception e) {
            LOG.error("Load system app package from {} failed: {}", resource.getFilename(), e.getMessage(), e);
            return false;
        }
    }
}
