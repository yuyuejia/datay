package com.data.datafusion.service.apppackage;

import com.data.datafusion.domain.AppPackage;
import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.ETLTask;
import com.data.datafusion.domain.Job;
import com.data.datafusion.domain.Metric;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.*;
import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.security.TenantContext;
import com.data.datafusion.service.apppackage.model.AppPackageContent;
import com.data.datafusion.service.dto.*;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.criteria.Predicate;
import java.time.ZonedDateTime;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 数据服务应用市场服务。
 *
 * <p>负责资产包的目录管理（系统预制包 + 租户导出包）、导出与初始化编排。
 * 实际的内容组装与安装分别由 {@link AppPackageExportService} 与
 * {@link AppPackageImportService} 完成。
 *
 * <p>资产包不做 Hibernate 租户过滤，租户隔离由本类的查询条件显式表达：
 * 租户始终能看到系统预制包与自己的包，看不到其它租户的包。
 */
@Service
public class AppPackageService {

    private static final Logger LOG = LoggerFactory.getLogger(AppPackageService.class);

    private static final String DEFAULT_VERSION = "1.0.0";

    private final AppPackageRepository appPackageRepository;

    private final DataSourceRepository dataSourceRepository;

    private final DataModelRepository dataModelRepository;

    private final MetricRepository metricRepository;

    private final ETLTaskRepository etlTaskRepository;

    private final JobRepository jobRepository;

    private final AppPackageExportService exportService;

    private final AppPackageImportService importService;

    private final AppPackageInstanceService instanceService;

    public AppPackageService(
        AppPackageRepository appPackageRepository,
        DataSourceRepository dataSourceRepository,
        DataModelRepository dataModelRepository,
        MetricRepository metricRepository,
        ETLTaskRepository etlTaskRepository,
        JobRepository jobRepository,
        AppPackageExportService exportService,
        AppPackageImportService importService,
        AppPackageInstanceService instanceService
    ) {
        this.appPackageRepository = appPackageRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.dataModelRepository = dataModelRepository;
        this.metricRepository = metricRepository;
        this.etlTaskRepository = etlTaskRepository;
        this.jobRepository = jobRepository;
        this.exportService = exportService;
        this.importService = importService;
        this.instanceService = instanceService;
    }

    // ------------------------------------------------------------------ 市场查询

    @Transactional(readOnly = true)
    public Page<AppPackageDTO> findAll(Pageable pageable, String search, String source, String category) {
        Specification<AppPackage> specification = visibleSpecification();
        if (StringUtils.hasText(source) && !"ALL".equalsIgnoreCase(source)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("packageType"), source.trim().toUpperCase(Locale.ROOT)));
        }
        if (StringUtils.hasText(category)) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("category"), category.trim()));
        }
        if (StringUtils.hasText(search)) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) -> {
                List<Predicate> ors = new ArrayList<>();
                for (String field : new String[] { "name", "code", "description", "category" }) {
                    ors.add(cb.like(cb.lower(root.get(field)), pattern));
                }
                return cb.or(ors.toArray(new Predicate[0]));
            });
        }
        return appPackageRepository.findAll(specification, pageable).map(entity -> toDto(entity, false));
    }

    /** 当前租户可见：系统预制包 + 自己租户的包。 */
    private Specification<AppPackage> visibleSpecification() {
        String tenantId = currentTenantId();
        return (root, query, cb) ->
            cb.or(cb.equal(root.get("packageType"), AppPackage.TYPE_SYSTEM), cb.equal(root.get("tenantId"), tenantId));
    }

    @Transactional(readOnly = true)
    public List<String> findCategories() {
        Set<String> categories = new TreeSet<>();
        for (AppPackage entity : appPackageRepository.findAll(visibleSpecification())) {
            if (StringUtils.hasText(entity.getCategory())) {
                categories.add(entity.getCategory());
            }
        }
        return new ArrayList<>(categories);
    }

    @Transactional(readOnly = true)
    public Optional<AppPackageDTO> findOne(Long id, boolean includeContent) {
        return appPackageRepository.findById(id).filter(this::isVisible).map(entity -> toDto(entity, includeContent));
    }

    private boolean isVisible(AppPackage entity) {
        if (entity.isSystemPackage()) {
            return true;
        }
        return Objects.equals(entity.getTenantId(), currentTenantId());
    }

    // ------------------------------------------------------------------ 导出

    /**
     * 按选择导出资产包；默认同时登记到市场，便于本租户后续复用或下载。
     */
    @Transactional
    public AppPackageDTO export(AppPackageExportRequestDTO request) {
        if (request == null) {
            throw new IllegalArgumentException("导出参数不能为空");
        }
        AppPackageContent content = exportService.buildContent(request);
        AppPackageContent.Meta meta = content.getMeta();
        meta.setSource(AppPackage.TYPE_TENANT);
        meta.setExportTime(ZonedDateTime.now().toString());
        meta.setSourceTenantId(currentTenantId());
        meta.setSourceTenantCode(TenantContext.getTenantCode());
        meta.setExportedBy(SecurityUtils.getCurrentUserLogin().orElse("system"));
        if (!StringUtils.hasText(meta.getName())) {
            meta.setName("未命名数据应用");
        }
        if (!StringUtils.hasText(meta.getVersion())) {
            meta.setVersion(DEFAULT_VERSION);
        }
        if (content.rebuildSummary().getOrDefault("total", 0) == 0) {
            throw new IllegalArgumentException("请至少选择一项要打包的资产");
        }

        boolean saveToMarket = request.saveToMarket == null || Boolean.TRUE.equals(request.saveToMarket);
        AppPackage entity = null;
        if (saveToMarket) {
            entity = persistPackage(content, request.overwrite);
            meta.setCode(entity.getCode());
        } else if (!StringUtils.hasText(meta.getCode())) {
            meta.setCode(generateCode(meta.getName()));
        }
        AppPackageDTO dto = new AppPackageDTO();
        dto.setId(entity == null ? null : entity.getId());
        dto.setName(meta.getName());
        dto.setCode(meta.getCode());
        dto.setDescription(meta.getDescription());
        dto.setCategory(meta.getCategory());
        dto.setVersion(meta.getVersion());
        dto.setPackageType(AppPackage.TYPE_TENANT);
        dto.setStatus(AppPackage.STATUS_ENABLED);
        dto.setTenantId(currentTenantId());
        dto.setItemSummary(content.rebuildSummary());
        dto.setContent(AppPackageJson.write(content));
        dto.setContentSize((long) dto.getContent().length());
        if (entity != null) {
            dto.setCreateUser(entity.getCreateUser());
            dto.setCreateTime(entity.getCreateTime());
            dto.setUpdateTime(entity.getUpdateTime());
        }
        return dto;
    }

    /**
     * 把导出内容登记到市场。
     */
    private AppPackage persistPackage(AppPackageContent content, Boolean overwrite) {
        AppPackageContent.Meta meta = content.getMeta();
        String tenantId = currentTenantId();
        String code = StringUtils.hasText(meta.getCode()) ? meta.getCode().trim() : generateCode(meta.getName());
        AppPackage existing = appPackageRepository
            .findByCodeAndPackageTypeAndTenantId(code, AppPackage.TYPE_TENANT, tenantId)
            .orElse(null);
        AppPackage entity;
        ZonedDateTime now = ZonedDateTime.now();
        if (existing != null && Boolean.TRUE.equals(overwrite)) {
            entity = existing;
        } else {
            if (existing != null) {
                code = uniquePackageCode(code);
            }
            entity = new AppPackage();
            entity.setPackageType(AppPackage.TYPE_TENANT);
            entity.setCreateUser(SecurityUtils.getCurrentUserLogin().orElse("system"));
            entity.setCreateTime(now);
        }
        meta.setCode(code);
        entity.setName(meta.getName());
        entity.setCode(code);
        entity.setDescription(meta.getDescription());
        entity.setCategory(meta.getCategory());
        entity.setVersion(StringUtils.hasText(meta.getVersion()) ? meta.getVersion() : DEFAULT_VERSION);
        entity.setStatus(AppPackage.STATUS_ENABLED);
        entity.setItemSummary(AppPackageJson.write(content.getSummary()));
        String json = AppPackageJson.write(content);
        entity.setContent(json);
        entity.setContentSize((long) json.length());
        entity.setUpdateTime(now);
        return appPackageRepository.save(entity);
    }

    private String uniquePackageCode(String code) {
        String candidate = code + "-" + System.currentTimeMillis() % 100000;
        while (appPackageRepository.findByCodeAndPackageTypeAndTenantId(candidate, AppPackage.TYPE_TENANT, currentTenantId()).isPresent()) {
            candidate = code + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return candidate;
    }

    private String generateCode(String name) {
        String slug = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (!StringUtils.hasText(slug)) {
            slug = "app";
        }
        return uniquePackageCode(slug);
    }

    // ------------------------------------------------------------------ 初始化

    /**
     * 基于资产包初始化数据应用：支持市场中已有的包（packageId），也支持直接上传资产包 JSON（content）。
     *
     * <p>初始化成功后写入成功记录；失败时先回滚导入事务，再用独立事务写入失败记录，最后抛出异常。
     */
    public AppPackageInitResultDTO initialize(AppPackageInitRequestDTO request) {
        AppPackage entity = null;
        AppPackageContent content;
        if (request != null && request.packageId != null) {
            entity = appPackageRepository.findById(request.packageId).filter(this::isVisible).orElseThrow(() ->
                new IllegalArgumentException("资产包不存在或当前租户不可见：" + request.packageId)
            );
            if (!StringUtils.hasText(entity.getContent())) {
                throw new IllegalArgumentException("资产包内容为空：" + entity.getName());
            }
            content = AppPackageJson.readContent(entity.getContent());
        } else if (request != null && StringUtils.hasText(request.content)) {
            content = AppPackageJson.readContent(request.content);
        } else {
            throw new IllegalArgumentException("请指定要初始化的资产包，或上传资产包 JSON 内容");
        }
        validateContent(content);

        Long packageId = entity == null ? null : entity.getId();
        String packageCode = entity == null ? content.getMeta().getCode() : entity.getCode();
        String packageName = entity == null ? content.getMeta().getName() : entity.getName();
        try {
            AppPackageInitResultDTO result = importService.importContent(content, request, packageId);
            instanceService.record(packageId, packageCode, packageName, result, result.message);
            return result;
        } catch (RuntimeException e) {
            LOG.error("Initialize app package failed: {}", e.getMessage(), e);
            instanceService.record(packageId, packageCode, packageName, null, e.getMessage());
            throw e;
        }
    }

    private void validateContent(AppPackageContent content) {
        if (content == null) {
            throw new IllegalArgumentException("资产包内容为空");
        }
        if (!AppPackageContent.FORMAT.equals(content.getPackageFormat())) {
            throw new IllegalArgumentException("不是合法的数据应用资产包（packageFormat=" + content.getPackageFormat() + "）");
        }
        int total = content.rebuildSummary().getOrDefault("total", 0);
        if (total == 0) {
            throw new IllegalArgumentException("资产包内没有任何资产");
        }
    }

    // ------------------------------------------------------------------ 删除

    @Transactional
    public void delete(Long id) {
        AppPackage entity = appPackageRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("资产包不存在：" + id));
        if (entity.isSystemPackage()) {
            throw new IllegalArgumentException("系统预制资产包不允许删除");
        }
        if (!Objects.equals(entity.getTenantId(), currentTenantId())) {
            throw new IllegalArgumentException("只能删除本租户的资产包");
        }
        appPackageRepository.deleteById(id);
    }

    // ------------------------------------------------------------------ 导出向导可选资产

    @Transactional(readOnly = true)
    public AppPackageExportOptionsDTO findExportOptions() {
        AppPackageExportOptionsDTO options = new AppPackageExportOptionsDTO();
        dataSourceRepository
            .findAll()
            .forEach(source -> {
                AppPackageExportOptionsDTO.Option option = new AppPackageExportOptionsDTO.Option();
                option.id = source.getId();
                option.name = source.getName();
                option.code = source.getType();
                option.subtitle = source.getType();
                option.owner = source.getUrl();
                options.dataSources.add(option);
            });
        dataModelRepository
            .findAll()
            .forEach(model -> {
                AppPackageExportOptionsDTO.Option option = new AppPackageExportOptionsDTO.Option();
                option.id = model.getId();
                option.name = model.getName();
                option.code = model.getCode();
                option.subtitle = model.getModelType();
                option.owner = model.getTableName();
                options.models.add(option);
            });
        metricRepository
            .findAll()
            .forEach(metric -> {
                AppPackageExportOptionsDTO.Option option = new AppPackageExportOptionsDTO.Option();
                option.id = metric.getId();
                option.name = metric.getName();
                option.code = metric.getCode();
                option.subtitle = metric.getMetricType();
                option.owner = metric.getUnit();
                options.metrics.add(option);
            });
        etlTaskRepository
            .findAll()
            .forEach(task -> {
                AppPackageExportOptionsDTO.Option option = new AppPackageExportOptionsDTO.Option();
                option.id = task.getId();
                option.name = task.getTaskName();
                option.code = task.getTaskCode();
                option.subtitle = task.getType();
                option.owner = task.getDir();
                options.etlTasks.add(option);
            });
        jobRepository.findByTypeOrderByIdAsc(TaskConstants.TASK_TYPE_SQL).forEach(job -> options.sqlJobs.add(toJobOption(job)));
        jobRepository.findByTypeOrderByIdAsc(TaskConstants.TASK_TYPE_DAG).forEach(job -> options.dagJobs.add(toJobOption(job)));
        options.categories = findCategories();
        return options;
    }

    private AppPackageExportOptionsDTO.Option toJobOption(Job job) {
        AppPackageExportOptionsDTO.Option option = new AppPackageExportOptionsDTO.Option();
        option.id = job.getId();
        option.name = job.getJobName();
        option.code = job.getType();
        option.subtitle = job.getStatus();
        option.owner = job.getCron();
        return option;
    }

    // ------------------------------------------------------------------ 映射

    private AppPackageDTO toDto(AppPackage entity, boolean includeContent) {
        AppPackageDTO dto = new AppPackageDTO();
        dto.setId(entity.getId());
        dto.setTenantId(entity.getTenantId());
        dto.setName(entity.getName());
        dto.setCode(entity.getCode());
        dto.setDescription(entity.getDescription());
        dto.setCategory(entity.getCategory());
        dto.setVersion(entity.getVersion());
        dto.setPackageType(entity.getPackageType());
        dto.setStatus(entity.getStatus());
        dto.setContentSize(entity.getContentSize());
        dto.setCreateUser(entity.getCreateUser());
        dto.setCreateTime(entity.getCreateTime());
        dto.setUpdateTime(entity.getUpdateTime());
        dto.setItemSummary(parseSummary(entity.getItemSummary()));
        if (includeContent) {
            dto.setContent(entity.getContent());
        }
        return dto;
    }

    private Map<String, Integer> parseSummary(String summary) {
        if (!StringUtils.hasText(summary)) {
            return new LinkedHashMap<>();
        }
        try {
            return AppPackageJson.mapper().readValue(summary, new TypeReference<Map<String, Integer>>() {});
        } catch (Exception e) {
            LOG.debug("Failed to parse app package summary: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String currentTenantId() {
        return SecurityUtils.getCurrentTenantId().map(String::valueOf).orElse(null);
    }
}
