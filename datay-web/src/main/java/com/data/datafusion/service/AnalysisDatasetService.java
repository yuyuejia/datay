package com.data.datafusion.service;

import com.data.datafusion.domain.AnalysisDashboard;
import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.repository.AnalysisDashboardRepository;
import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.data.datafusion.service.dashboard.AnalysisDatasetResolver;
import com.data.datafusion.service.dashboard.DashboardSqlGuard;
import com.data.datafusion.service.dashboard.DatasetQueryService;
import com.data.datafusion.service.dto.AnalysisDatasetDTO;
import com.data.datafusion.service.mapper.AnalysisDatasetMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 分析数据集管理：指标数据集与 SQL 数据集的增删改查、定义校验和预览。
 */
@Service
@Transactional
public class AnalysisDatasetService {

    private static final Logger LOG = LoggerFactory.getLogger(AnalysisDatasetService.class);

    private final AnalysisDatasetRepository repository;
    private final AnalysisDashboardRepository dashboardRepository;
    private final AnalysisDatasetMapper mapper;
    private final AnalysisDatasetResolver datasetResolver;
    private final DatasetQueryService datasetQueryService;
    private final ObjectMapper objectMapper;

    public AnalysisDatasetService(
        AnalysisDatasetRepository repository,
        AnalysisDashboardRepository dashboardRepository,
        AnalysisDatasetMapper mapper,
        AnalysisDatasetResolver datasetResolver,
        DatasetQueryService datasetQueryService,
        ObjectMapper objectMapper
    ) {
        this.repository = repository;
        this.dashboardRepository = dashboardRepository;
        this.mapper = mapper;
        this.datasetResolver = datasetResolver;
        this.datasetQueryService = datasetQueryService;
        this.objectMapper = objectMapper;
    }

    public AnalysisDatasetDTO save(AnalysisDatasetDTO dto) {
        validate(dto);
        String code = resolveCode(dto.getCode(), null);
        ZonedDateTime now = ZonedDateTime.now();
        dto.setId(null);
        dto.setTenantId(null);
        dto.setFields(null);
        dto.setCode(code);
        dto.setType(normalizeType(dto.getType()));
        dto.setCreateTime(now);
        dto.setUpdateTime(now);
        if (dto.getStatus() == null || dto.getStatus().isBlank()) {
            dto.setStatus(AnalysisDataset.STATUS_ENABLED);
        }
        dto.setDefinition(normalizeDefinition(dto.getType(), dto.getDefinition(), dto.getDataSourceId()));
        AnalysisDataset entity = repository.save(mapper.toEntity(dto));
        return mapper.toDto(entity);
    }

    public Optional<AnalysisDatasetDTO> update(String id, AnalysisDatasetDTO dto) {
        validate(dto);
        return repository
            .findById(id)
            .map(existing -> {
                dto.setId(id);
                dto.setTenantId(existing.getTenantId());
                dto.setType(normalizeType(dto.getType()));
                dto.setCreateTime(existing.getCreateTime());
                dto.setUpdateTime(ZonedDateTime.now());
                dto.setCode(resolveCode(dto.getCode() == null || dto.getCode().isBlank() ? existing.getCode() : dto.getCode(), id));
                if (dto.getStatus() == null || dto.getStatus().isBlank()) {
                    dto.setStatus(existing.getStatus() == null ? AnalysisDataset.STATUS_ENABLED : existing.getStatus());
                }
                String definition = normalizeDefinition(dto.getType(), dto.getDefinition(), dto.getDataSourceId());
                if (!definition.equals(existing.getDefinition())) {
                    // 定义变化后旧的字段缓存失效，等待下一次预览重新生成。
                    dto.setFields(null);
                } else if (dto.getFields() == null) {
                    dto.setFields(existing.getFields());
                }
                dto.setDefinition(definition);
                AnalysisDataset entity = repository.save(mapper.toEntity(dto));
                return mapper.toDto(entity);
            });
    }

    @Transactional(readOnly = true)
    public Page<AnalysisDatasetDTO> findAll(Pageable pageable, String search) {
        return repository.findAll(buildSearchSpecification(search), pageable).map(mapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<AnalysisDatasetDTO> findAllSimple() {
        return repository.findAll().stream().map(mapper::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<AnalysisDatasetDTO> findOne(String id) {
        return repository.findById(id).map(mapper::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<AnalysisDatasetDTO> findByCode(String code) {
        return repository.findByCode(code).map(mapper::toDto);
    }

    public void delete(String id) {
        long references = countReferences(id);
        if (references > 0) {
            throw new IllegalArgumentException("数据集正被 " + references + " 个看板引用，请先解除引用后再删除");
        }
        repository.deleteById(id);
    }

    /**
     * 预览已保存数据集。结果列会缓存到 fields 字段，供设计器做字段选择。
     */
    public Map<String, Object> preview(String id, Map<String, Object> filterValues) throws SQLException {
        AnalysisDataset entity = repository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("数据集不存在：" + id));
        Map<String, Object> dataset = toQueryDataset(entity);
        Map<String, Object> result = datasetQueryService.query(dataset, entity.getDataSourceId(), List.of(), filterValues);
        cacheFields(entity, result);
        return result;
    }

    /**
     * 预览未保存的数据集定义，用于数据集编辑器实时查看结果。
     */
    public Map<String, Object> previewDefinition(AnalysisDatasetDTO dto, Map<String, Object> filterValues) throws SQLException {
        validate(dto);
        String type = normalizeType(dto.getType());
        String definition = normalizeDefinition(type, dto.getDefinition(), dto.getDataSourceId());
        Map<String, Object> dataset = buildQueryDataset("__preview__", type, definition, dto.getDataSourceId());
        return datasetQueryService.query(dataset, dto.getDataSourceId(), List.of(), filterValues);
    }

    /**
     * 校验并规范化定义，返回规范化后的 DTO（不落库）。
     */
    public AnalysisDatasetDTO validateDefinition(AnalysisDatasetDTO dto) {
        validate(dto);
        dto.setType(normalizeType(dto.getType()));
        dto.setDefinition(normalizeDefinition(dto.getType(), dto.getDefinition(), dto.getDataSourceId()));
        return dto;
    }

    private void validate(AnalysisDatasetDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("数据集不能为空");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("数据集名称不能为空");
        }
        String type = normalizeType(dto.getType());
        if (!AnalysisDataset.TYPE_METRIC.equals(type) && !AnalysisDataset.TYPE_SQL.equals(type)) {
            throw new IllegalArgumentException("数据集类型只支持 METRIC 或 SQL");
        }
        if (
            dto.getStatus() != null &&
            !dto.getStatus().isBlank() &&
            !AnalysisDataset.STATUS_ENABLED.equals(dto.getStatus()) &&
            !AnalysisDataset.STATUS_DISABLED.equals(dto.getStatus())
        ) {
            throw new IllegalArgumentException("状态只能是 ENABLED 或 DISABLED");
        }
        if (dto.getDefinition() == null || dto.getDefinition().isBlank()) {
            throw new IllegalArgumentException("数据集定义不能为空");
        }
    }

    private String normalizeType(String type) {
        return type == null ? AnalysisDataset.TYPE_METRIC : type.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 校验并规范化定义 JSON：
     * <ul>
     *   <li>METRIC：必须有 metricQuery，且 metricCodes 非空；</li>
     *   <li>SQL：必须有 sql，且为单条只读查询；SQL 数据集必须绑定数据源。</li>
     * </ul>
     */
    private String normalizeDefinition(String rawType, String definitionJson, String dataSourceId) {
        String type = normalizeType(rawType);
        Map<String, Object> definition = parseDefinition(definitionJson);
        if (AnalysisDataset.TYPE_SQL.equals(type)) {
            Object sqlValue = definition.get("sql");
            String sql = sqlValue == null ? null : String.valueOf(sqlValue);
            if (sql == null || sql.isBlank()) {
                throw new IllegalArgumentException("SQL 数据集必须提供 sql");
            }
            definition.put("sql", DashboardSqlGuard.requireReadOnly(sql));
            String effectiveDataSourceId = dataSourceId;
            if (effectiveDataSourceId == null || effectiveDataSourceId.isBlank()) {
                effectiveDataSourceId = DatasetQueryService.asString(definition.get("dataSourceId"));
            }
            if (effectiveDataSourceId == null || effectiveDataSourceId.isBlank()) {
                throw new IllegalArgumentException("SQL 数据集必须绑定数据源");
            }
            definition.put("dataSourceId", effectiveDataSourceId);
            if (!(definition.get("params") instanceof List<?>)) {
                definition.put("params", new ArrayList<>());
            }
        } else {
            Map<String, Object> metricQuery = definition.get("metricQuery") instanceof Map<?, ?> map
                ? DatasetQueryService.asMap(map)
                : definition;
            List<String> metricCodes = DatasetQueryService.asStringList(metricQuery.get("metricCodes"));
            if (metricCodes.isEmpty()) {
                throw new IllegalArgumentException("指标数据集必须至少选择一个指标");
            }
            metricQuery.put("metricCodes", metricCodes);
            if (!(metricQuery.get("dimensions") instanceof List<?>)) {
                metricQuery.put("dimensions", new ArrayList<>());
            }
            Map<String, Object> normalized = new LinkedHashMap<>();
            normalized.put("metricQuery", metricQuery);
            definition = normalized;
        }
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("数据集定义序列化失败：" + e.getOriginalMessage());
        }
    }

    private Map<String, Object> parseDefinition(String definitionJson) {
        if (definitionJson == null || definitionJson.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(definitionJson, new TypeReference<>() {});
            return map == null ? new LinkedHashMap<>() : map;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("数据集定义不是合法 JSON：" + e.getOriginalMessage());
        }
    }

    private Map<String, Object> toQueryDataset(AnalysisDataset entity) {
        return buildQueryDataset(entity.getId(), entity.getType(), entity.getDefinition(), entity.getDataSourceId());
    }

    private Map<String, Object> buildQueryDataset(String id, String type, String definitionJson, String dataSourceId) {
        Map<String, Object> definition = datasetResolver.parseDefinition(definitionJson);
        Map<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("id", id);
        dataset.put("type", normalizeType(type));
        if (AnalysisDataset.TYPE_SQL.equals(normalizeType(type))) {
            dataset.put("sql", definition.get("sql"));
            String effectiveDataSourceId = dataSourceId;
            if (effectiveDataSourceId == null || effectiveDataSourceId.isBlank()) {
                effectiveDataSourceId = DatasetQueryService.asString(definition.get("dataSourceId"));
            }
            dataset.put("dataSourceId", effectiveDataSourceId);
        } else {
            Object metricQuery = definition.get("metricQuery");
            dataset.put("metricQuery", metricQuery instanceof Map<?, ?> ? metricQuery : definition);
        }
        return dataset;
    }

    @SuppressWarnings("unchecked")
    private void cacheFields(AnalysisDataset entity, Map<String, Object> result) {
        Object columns = result.get("columns");
        if (!(columns instanceof List<?> columnList)) {
            return;
        }
        List<Map<String, Object>> rows = result.get("rows") instanceof List<?> rowList
            ? (List<Map<String, Object>>) rowList
            : List.of();
        try {
            List<Map<String, Object>> fieldsColumns = new ArrayList<>();
            for (Object rawColumn : columnList) {
                if (rawColumn == null) {
                    continue;
                }
                String name = String.valueOf(rawColumn);
                Map<String, Object> column = new LinkedHashMap<>();
                column.put("name", name);
                column.put("type", inferColumnType(rows, name));
                fieldsColumns.add(column);
            }
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("columns", fieldsColumns);
            fields.put("columnLabels", result.get("columnLabels"));
            entity.setFields(objectMapper.writeValueAsString(fields));
            entity.setUpdateTime(ZonedDateTime.now());
            repository.save(entity);
        } catch (JsonProcessingException e) {
            LOG.warn("缓存数据集字段失败: {}", e.getMessage());
        }
    }

    /**
     * 预览结果不携带 JDBC 元数据时，用首个非空值推断列类型。
     * 数值类型字段会被设计器优先选作图表数值字段。
     */
    private String inferColumnType(List<Map<String, Object>> rows, String column) {
        for (Map<String, Object> row : rows) {
            Object value = row.get(column);
            if (value == null) {
                continue;
            }
            if (value instanceof Number) {
                return "number";
            }
            if (value instanceof Boolean) {
                return "boolean";
            }
            if (value instanceof String text) {
                return looksLikeDate(text) ? "date" : "string";
            }
            return "string";
        }
        return "unknown";
    }

    private boolean looksLikeDate(String value) {
        return value.matches("\\d{4}-\\d{2}-\\d{2}([ T].*)?") || value.matches("\\d{4}/\\d{2}/\\d{2}.*");
    }

    private long countReferences(String datasetId) {
        long count = 0;
        for (AnalysisDashboard dashboard : dashboardRepository.findAll()) {
            Map<String, Object> spec;
            try {
                spec = objectMapper.readValue(dashboard.getSpec(), new TypeReference<>() {});
            } catch (Exception e) {
                continue;
            }
            if (datasetResolver.collectRefIds(spec).contains(datasetId)) {
                count++;
            }
        }
        return count;
    }

    private String resolveCode(String requested, String excludeId) {
        String candidate = requested == null ? null : requested.trim();
        if (candidate == null || candidate.isBlank()) {
            candidate = "dataset_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        if (!candidate.matches("[A-Za-z0-9_\\-]+")) {
            throw new IllegalArgumentException("数据集编码只能包含字母、数字、下划线和中划线");
        }
        Optional<AnalysisDataset> conflict = excludeId == null
            ? repository.findByCode(candidate)
            : repository.findByCodeAndIdNot(candidate, excludeId);
        if (conflict.isPresent()) {
            throw new IllegalArgumentException("数据集编码已存在：" + candidate);
        }
        return candidate;
    }

    private Specification<AnalysisDataset> buildSearchSpecification(String search) {
        return (root, query, cb) -> {
            if (search == null || search.trim().isEmpty()) {
                return cb.conjunction();
            }
            String keyword = "%" + search.trim().toLowerCase() + "%";
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            for (String field : new String[] { "name", "code", "description" }) {
                predicates.add(cb.like(cb.lower(root.get(field)), keyword));
            }
            return cb.or(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }
}
