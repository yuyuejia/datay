package com.data.datafusion.service;

import com.data.datafusion.domain.DataSource;
import com.data.datafusion.repository.DataSourceRepository;
import com.data.datafusion.service.dto.DataSourceDTO;
import com.data.datafusion.service.mapper.DataSourceMapper;
import com.data.datasource.DataSourceConnection;
import com.data.datasource.DataSourceProviderFactory;
import com.data.metadata.util.ConnectionPoolManager;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.data.datafusion.domain.DataSource}.
 */
@Service
@Transactional
public class DataSourceService {

    private static final Logger LOG = LoggerFactory.getLogger(DataSourceService.class);

    private static final int MAX_ERROR_DETAIL_LENGTH = 500;

    private final DataSourceRepository dataSourceRepository;

    private final DataSourceMapper dataSourceMapper;

    public DataSourceService(DataSourceRepository dataSourceRepository, DataSourceMapper dataSourceMapper) {
        this.dataSourceRepository = dataSourceRepository;
        this.dataSourceMapper = dataSourceMapper;
    }

    /**
     * Save a dataSource.
     *
     * @param dataSourceDTO the entity to save.
     * @return the persisted entity.
     */
    public DataSourceDTO save(DataSourceDTO dataSourceDTO) {
        LOG.debug("Request to save DataSource : {}", dataSourceDTO);
        normalizeType(dataSourceDTO);
        DataSource dataSource = dataSourceMapper.toEntity(dataSourceDTO);
        dataSource = dataSourceRepository.save(dataSource);
        evictConnectionPools(dataSource.getUrl(), dataSource.getUsername());
        return dataSourceMapper.toDto(dataSource);
    }

    /**
     * 归一化数据源类型标识为规范大写，避免不同入口（界面选择大写、MCP 传入小写）导致
     * 类型匹配、图标与表单配置失灵。
     */
    private static void normalizeType(DataSourceDTO dataSourceDTO) {
        if (dataSourceDTO != null && dataSourceDTO.getType() != null) {
            dataSourceDTO.setType(dataSourceDTO.getType().trim().toUpperCase());
        }
    }

    /**
     * 读取时归一化类型，兼容历史存量为小写的数据源。
     */
    private static DataSourceDTO normalize(DataSourceDTO dataSourceDTO) {
        normalizeType(dataSourceDTO);
        return dataSourceDTO;
    }

    /**
     * Update a dataSource.
     *
     * @param dataSourceDTO the entity to save.
     * @return the persisted entity.
     */
    public DataSourceDTO update(DataSourceDTO dataSourceDTO) {
        LOG.debug("Request to update DataSource : {}", dataSourceDTO);
        normalizeType(dataSourceDTO);
        Optional<DataSource> previous = dataSourceDTO.getId() == null
            ? Optional.empty()
            : dataSourceRepository.findById(dataSourceDTO.getId());
        String previousUrl = previous.map(DataSource::getUrl).orElse(null);
        String previousUsername = previous.map(DataSource::getUsername).orElse(null);

        DataSource dataSource = dataSourceMapper.toEntity(dataSourceDTO);
        dataSource = dataSourceRepository.save(dataSource);

        evictConnectionPools(previousUrl, previousUsername);
        evictConnectionPools(dataSource.getUrl(), dataSource.getUsername());
        return dataSourceMapper.toDto(dataSource);
    }

    /**
     * Partially update a dataSource.
     *
     * @param dataSourceDTO the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<DataSourceDTO> partialUpdate(DataSourceDTO dataSourceDTO) {
        LOG.debug("Request to partially update DataSource : {}", dataSourceDTO);
        normalizeType(dataSourceDTO);

        return dataSourceRepository
            .findById(dataSourceDTO.getId())
            .map(existingDataSource -> {
                String previousUrl = existingDataSource.getUrl();
                String previousUsername = existingDataSource.getUsername();

                dataSourceMapper.partialUpdate(existingDataSource, dataSourceDTO);
                DataSource saved = dataSourceRepository.save(existingDataSource);

                evictConnectionPools(previousUrl, previousUsername);
                evictConnectionPools(saved.getUrl(), saved.getUsername());
                return saved;
            })
            .map(dataSourceMapper::toDto);
    }

    /**
     * Get all the dataSources.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<DataSourceDTO> findAll(Pageable pageable) {
        LOG.debug("Request to get all DataSources");
        return dataSourceRepository.findAll(pageable).map(entity -> normalize(dataSourceMapper.toDto(entity)));
    }

    /**
     * Get all the dataSources with an optional keyword search.
     *
     * @param pageable the pagination information.
     * @param search   the optional keyword used to filter by name, hostname/IP, port, url, schema name or username.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<DataSourceDTO> findAll(Pageable pageable, String search) {
        LOG.debug("Request to get all DataSources with search: {}", search);
        return dataSourceRepository.findAll(buildSearchSpecification(search), pageable).map(entity -> normalize(dataSourceMapper.toDto(entity)));
    }

    private Specification<DataSource> buildSearchSpecification(String search) {
        if (search == null || search.trim().isEmpty()) {
            return Specification.where(null);
        }
        String keyword = search.trim().toLowerCase();
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (String field : new String[] { "name", "hostname", "port", "url", "schemaName", "username" }) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get(field)), "%" + keyword + "%"));
            }
            return criteriaBuilder.or(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * Get one dataSource by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<DataSourceDTO> findOne(String id) {
        LOG.debug("Request to get DataSource : {}", id);
        return dataSourceRepository.findById(id).map(entity -> normalize(dataSourceMapper.toDto(entity)));
    }

    /**
     * Delete the dataSource by id.
     *
     * @param id the id of the entity.
     */
    public void delete(String id) {
        LOG.debug("Request to delete DataSource : {}", id);
        Optional<DataSource> dataSource = dataSourceRepository.findById(id);
        dataSourceRepository.deleteById(id);
        dataSource.ifPresent(entity -> evictConnectionPools(entity.getUrl(), entity.getUsername()));
    }

    /**
     * 数据源新增、修改或删除后关闭其旧连接池。
     *
     * <p>连接池按 URL、用户名与密码指纹缓存；配置变更后若继续复用旧池，会出现
     * 「配置已改但仍连旧库/旧密码」的问题。关闭失败只记录日志，不影响数据源本身的操作。
     */
    private void evictConnectionPools(String url, String username) {
        if (url == null) {
            return;
        }
        try {
            ConnectionPoolManager.closePool(url, username);
        } catch (Exception e) {
            LOG.warn("Failed to close connection pool for data source {} / {}", url, username, e);
        }
    }

    /**
     * 测试数据源连接，并返回可直接展示给用户的失败原因。
     *
     * @param dataSourceDTO 数据源DTO
     * @return 连接测试结果及提示信息
     */
    public ConnectionTestResult testConnectionWithDetail(DataSourceDTO dataSourceDTO) {
        LOG.debug("Request to test DataSource connection: {}", dataSourceDTO);
        try (DataSourceConnection connection = DataSourceProviderFactory.connect(DataSourceQueryService.toDatasourceInfo(dataSourceDTO))) {
            boolean success = connection.testConnection();
            return new ConnectionTestResult(success, success ? "连接测试成功" : "连接测试失败：未能建立可用连接");
        } catch (Exception e) {
            String message = describeConnectionFailure(e);
            LOG.warn("Data source connection test failed: {}", message, e);
            return new ConnectionTestResult(false, message);
        }
    }

    /**
     * 测试数据源连接
     * @param dataSourceDTO 数据源DTO
     * @return 连接测试结果，true表示连接成功，false表示连接失败
     */
    public boolean testConnection(DataSourceDTO dataSourceDTO) {
        return testConnectionWithDetail(dataSourceDTO).success();
    }

    /**
     * 将连接异常转换为便于排查的提示：优先使用最底层原因，并针对常见错误补充处理建议。
     */
    static String describeConnectionFailure(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }

        String detail = firstNonBlank(root.getMessage(), error.getMessage(), root.getClass().getSimpleName());
        detail = detail.replaceAll("\\s+", " ").trim();
        detail = detail.replaceAll("(?i)(password|pwd)\\s*[=:]\\s*[^;&\\s]+", "$1=***");
        if (detail.length() > MAX_ERROR_DETAIL_LENGTH) {
            detail = detail.substring(0, MAX_ERROR_DETAIL_LENGTH) + "…";
        }

        String hint = connectionFailureHint(detail);
        return hint == null ? "连接测试失败：" + detail : "连接测试失败：" + detail + "（" + hint + "）";
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "未知错误";
    }

    private static String connectionFailureHint(String detail) {
        String lower = detail.toLowerCase(Locale.ROOT);
        if (
            lower.contains("access denied") ||
            lower.contains("authentication failed") ||
            lower.contains("invalid username/password") ||
            lower.contains("login failed")
        ) {
            return "请检查用户名、密码及账号权限";
        }
        if (lower.contains("unknown database") || (lower.contains("database") && lower.contains("does not exist"))) {
            return "请检查数据库名是否存在";
        }
        if (
            lower.contains("unknownhost") ||
            lower.contains("unknown host") ||
            lower.contains("name or service not known") ||
            lower.contains("nodename nor servname")
        ) {
            return "请检查主机地址或 DNS 解析";
        }
        if (lower.contains("connection refused") || lower.contains("connectexception")) {
            return "请检查端口是否开放、数据库服务是否已启动";
        }
        if (lower.contains("timed out") || lower.contains("timeout")) {
            return "请检查网络连通性、防火墙或超时配置";
        }
        if (lower.contains("no suitable driver") || lower.contains("classnotfound") || lower.contains("driver class")) {
            return "请检查 JDBC URL、驱动类名及驱动文件";
        }
        if (lower.contains("public key retrieval")) {
            return "请在 JDBC URL 中增加 allowPublicKeyRetrieval=true";
        }
        if (lower.contains("ssl")) {
            return "请检查 SSL 相关连接参数";
        }
        return null;
    }

    /**
     * 连接测试结果。
     */
    public record ConnectionTestResult(boolean success, String message) {}
}
