package com.data.datafusion.service;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.Metric;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.MetricRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.dto.MetricDTO;
import com.data.datafusion.service.dto.MetricRefDTO;
import com.data.datafusion.service.mapper.MetricMapper;
import com.data.datafusion.service.metric.MetricFilterCondition;
import com.data.datafusion.service.metric.MetricFilterConfig;
import com.data.datafusion.service.metric.MetricFilterOperator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link Metric}.
 */
@Service
@Transactional
public class MetricService {

    private static final Logger LOG = LoggerFactory.getLogger(MetricService.class);

    /** 支持的数据类型：仅字符串与数值类型。 */
    public static final List<String> DATA_TYPES = List.of("INTEGER", "LONG", "DOUBLE", "DECIMAL", "VARCHAR", "TEXT");

    private static final Pattern FORMULA_REF_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9_\\-]+)}");

    /** 衍生指标公式去掉引用占位符后，只允许数字、运算符、括号、小数点与空白。 */
    private static final Pattern FORMULA_ALLOWED_PATTERN = Pattern.compile("[0-9+\\-*/().\\s]*");

    /** 原子指标公式为 SQL 片段，只允许标识符、数字、运算符、括号、逗号、点、百分号、字符串字面量与空白。 */
    private static final Pattern SQL_FORMULA_ALLOWED_PATTERN = Pattern.compile("[A-Za-z0-9_+\\-*/().,%'\\s]*");

    private static final Set<String> FILTER_TYPES = Set.of(
        MetricFilterCondition.TYPE_FACT_FIELD,
        MetricFilterCondition.TYPE_DIMENSION,
        MetricFilterCondition.TYPE_TIME
    );

    private final MetricRepository metricRepository;
    private final DataModelRepository dataModelRepository;
    private final ModelFieldRepository modelFieldRepository;
    private final MetricMapper metricMapper;
    private final ObjectMapper objectMapper;

    public MetricService(
        MetricRepository metricRepository,
        DataModelRepository dataModelRepository,
        ModelFieldRepository modelFieldRepository,
        MetricMapper metricMapper,
        ObjectMapper objectMapper
    ) {
        this.metricRepository = metricRepository;
        this.dataModelRepository = dataModelRepository;
        this.modelFieldRepository = modelFieldRepository;
        this.metricMapper = metricMapper;
        this.objectMapper = objectMapper;
    }

    public MetricDTO save(MetricDTO dto) {
        LOG.debug("Request to save Metric : {}", dto);
        validate(dto);
        ensureCodeUnique(dto.getCode(), null);

        ZonedDateTime now = ZonedDateTime.now();
        dto.setCreateTime(now);
        dto.setUpdateTime(now);
        dto.setId(null);
        if (dto.getStatus() == null || dto.getStatus().isBlank()) {
            dto.setStatus(Metric.STATUS_ENABLED);
        }
        normalize(dto);
        Metric entity = metricMapper.toEntity(dto);
        entity = metricRepository.save(entity);
        return enrich(findOne(entity.getId()).orElseThrow());
    }

    public Optional<MetricDTO> update(String id, MetricDTO dto) {
        LOG.debug("Request to update Metric : {}, {}", id, dto);
        validate(dto);
        ensureCodeUnique(dto.getCode(), id);

        return metricRepository
            .findById(id)
            .map(existing -> {
                dto.setId(id);
                dto.setTenantId(existing.getTenantId());
                dto.setCreateTime(existing.getCreateTime());
                dto.setUpdateTime(ZonedDateTime.now());
                if (dto.getStatus() == null || dto.getStatus().isBlank()) {
                    dto.setStatus(Metric.STATUS_ENABLED);
                }
                normalize(dto);
                Metric entity = metricMapper.toEntity(dto);
                entity = metricRepository.save(entity);
                return enrich(metricMapper.toDto(entity));
            });
    }

    @Transactional(readOnly = true)
    public Page<MetricDTO> findAll(Pageable pageable, String search, String metricType) {
        LOG.debug("Request to get all Metrics with search: {}, metricType: {}", search, metricType);
        Page<Metric> page = metricRepository.findAll(buildSearchSpecification(search, metricType), pageable);
        return page.map(metricMapper::toDto).map(this::enrich);
    }

    @Transactional(readOnly = true)
    public List<MetricDTO> findByDirectoryId(String directoryId) {
        LOG.debug("Request to get Metrics by directoryId : {}", directoryId);
        return metricRepository.findByDirectoryId(directoryId).stream()
            .map(metricMapper::toDto)
            .map(this::enrich)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<MetricDTO> findByMetricType(String metricType) {
        LOG.debug("Request to get Metrics by metricType : {}", metricType);
        return metricRepository.findByMetricType(metricType).stream()
            .map(metricMapper::toDto)
            .map(this::enrich)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<MetricDTO> findOne(String id) {
        LOG.debug("Request to get Metric : {}", id);
        return metricRepository.findById(id).map(metricMapper::toDto).map(this::enrich);
    }

    @Transactional(readOnly = true)
    public List<MetricDTO> findAllSimple() {
        LOG.debug("Request to get all Metrics (simple)");
        return metricRepository.findAll().stream().map(metricMapper::toDto).map(this::enrich).collect(Collectors.toList());
    }

    public void delete(String id) {
        LOG.debug("Request to delete Metric : {}", id);
        Optional<Metric> target = metricRepository.findById(id);
        if (target.isPresent()) {
            String code = target.get().getCode();
            boolean referenced = metricRepository
                .findAll()
                .stream()
                .filter(m -> Metric.TYPE_DERIVED.equals(m.getMetricType()))
                .filter(m -> !id.equals(m.getId()))
                .anyMatch(m -> parseFormulaRefs(m.getFormula()).contains(code));
            if (referenced) {
                throw new IllegalArgumentException("该指标已被其它衍生指标引用，请先解除引用后再删除");
            }
        }
        metricRepository.deleteById(id);
    }

    /**
     * 对 DTO 做展示信息补全：事实表名称、引用指标信息。
     */
    public MetricDTO enrich(MetricDTO dto) {
        if (dto == null) {
            return null;
        }
        if (dto.getFactModelId() != null) {
            dataModelRepository.findById(dto.getFactModelId()).ifPresent(model -> {
                dto.setFactModelName(model.getName());
                dto.setFactTableName(buildPhysicalTableName(model));
            });
        }
        List<MetricRefDTO> refMetrics = new ArrayList<>();
        if (Metric.TYPE_DERIVED.equals(dto.getMetricType()) && dto.getFormula() != null) {
            Map<String, Metric> byCode = metricRepository.findAll().stream().collect(Collectors.toMap(Metric::getCode, m -> m, (a, b) -> a));
            for (String code : parseFormulaRefs(dto.getFormula())) {
                Metric refMetric = byCode.get(code);
                if (refMetric != null) {
                    refMetrics.add(new MetricRefDTO(refMetric.getId(), refMetric.getCode(), refMetric.getName(), refMetric.getMetricType(), refMetric.getStatus()));
                }
            }
        }
        dto.setRefMetrics(refMetrics);
        return dto;
    }

    private String buildPhysicalTableName(DataModel model) {
        List<String> parts = new ArrayList<>();
        if (model.getSchemaName() != null && !model.getSchemaName().isBlank()) {
            parts.add(model.getSchemaName());
        }
        if (model.getTableName() != null && !model.getTableName().isBlank()) {
            parts.add(model.getTableName());
        } else if (model.getCode() != null && !model.getCode().isBlank()) {
            parts.add(model.getCode());
        }
        return String.join(".", parts);
    }

    /**
     * 解析公式中的指标引用编码。
     */
    public Set<String> parseFormulaRefs(String formula) {
        Set<String> codes = new LinkedHashSet<>();
        if (formula == null) {
            return codes;
        }
        Matcher matcher = FORMULA_REF_PATTERN.matcher(formula);
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        return codes;
    }

    private Specification<Metric> buildSearchSpecification(String search, String metricType) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (metricType != null && !metricType.isBlank()) {
                predicates.add(cb.equal(root.get("metricType"), metricType));
            }
            if (search != null && !search.trim().isEmpty()) {
                String keyword = search.trim().toLowerCase();
                List<Predicate> ors = new ArrayList<>();
                for (String field : new String[] { "name", "code", "description", "unit", "formula" }) {
                    ors.add(cb.like(cb.lower(root.get(field)), "%" + keyword + "%"));
                }
                predicates.add(cb.or(ors.toArray(new Predicate[0])));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void normalize(MetricDTO dto) {
        if (dto.getFormula() != null) {
            dto.setFormula(dto.getFormula().trim());
        }
        if (Metric.TYPE_DERIVED.equals(dto.getMetricType())) {
            dto.setFactModelId(null);
            dto.setFilterConfig(null);
        }
        if (dto.getFilterConfig() != null && dto.getFilterConfig().isBlank()) {
            dto.setFilterConfig(null);
        }
    }

    private void validate(MetricDTO dto) {
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("指标名称不能为空");
        }
        if (dto.getCode() == null || dto.getCode().trim().isEmpty()) {
            throw new IllegalArgumentException("指标编码不能为空");
        }
        if (!dto.getCode().matches("[A-Za-z0-9_\\-]+")) {
            throw new IllegalArgumentException("指标编码只能包含字母、数字、下划线和中划线");
        }
        if (dto.getStatus() != null && !Metric.STATUS_ENABLED.equals(dto.getStatus()) && !Metric.STATUS_DISABLED.equals(dto.getStatus())) {
            throw new IllegalArgumentException("状态只能是 ENABLED 或 DISABLED");
        }
        if (dto.getDataType() == null || dto.getDataType().isBlank()) {
            throw new IllegalArgumentException("请选择数据类型");
        }
        if (!DATA_TYPES.contains(dto.getDataType())) {
            throw new IllegalArgumentException("不支持的数据类型：" + dto.getDataType());
        }
        if (Metric.TYPE_ATOMIC.equals(dto.getMetricType())) {
            validateAtomic(dto);
        } else if (Metric.TYPE_DERIVED.equals(dto.getMetricType())) {
            validateDerived(dto);
        } else {
            throw new IllegalArgumentException("指标类型必须是 ATOMIC 或 DERIVED");
        }
    }

    private void validateAtomic(MetricDTO dto) {
        if (dto.getFactModelId() == null) {
            throw new IllegalArgumentException("请选择事实表");
        }
        DataModel factModel = dataModelRepository
            .findById(dto.getFactModelId())
            .orElseThrow(() -> new IllegalArgumentException("事实表不存在"));
        if (!"DWD".equalsIgnoreCase(factModel.getModelType())) {
            throw new IllegalArgumentException("事实表必须是模型类型为 DWD 的模型");
        }
        if (dto.getFormula() == null || dto.getFormula().trim().isEmpty()) {
            throw new IllegalArgumentException("原子指标计算公式不能为空");
        }
        String formula = dto.getFormula().trim();
        if (!SQL_FORMULA_ALLOWED_PATTERN.matcher(formula).matches()) {
            throw new IllegalArgumentException("原子指标计算公式只能包含字段名、数字、运算符、函数括号及字符串字面量");
        }
        List<ModelField> fields = modelFieldRepository.findByModelIdOrderBySortOrderAsc(dto.getFactModelId());
        Set<String> fieldNames = fields.stream().map(ModelField::getFieldName).collect(Collectors.toSet());
        validateFilterConfig(dto.getFilterConfig(), fields, fieldNames);
    }

    private void validateFilterConfig(String filterConfigJson, List<ModelField> factFields, Set<String> factFieldNames) {
        if (filterConfigJson == null || filterConfigJson.isBlank()) {
            return;
        }
        MetricFilterConfig config = parseFilterConfig(filterConfigJson);
        if (config == null || config.isEmpty()) {
            return;
        }
        for (MetricFilterCondition condition : config.getConditions()) {
            if (condition.getType() == null || !FILTER_TYPES.contains(condition.getType())) {
                throw new IllegalArgumentException("不支持的业务限定类型：" + condition.getType());
            }
            MetricFilterOperator operator = MetricFilterOperator.fromCode(condition.getOperator());
            if (operator == null) {
                throw new IllegalArgumentException("不支持的业务限定运算符：" + condition.getOperator());
            }
            String logic = condition.getLogic();
            if (logic != null && !"AND".equalsIgnoreCase(logic) && !"OR".equalsIgnoreCase(logic)) {
                throw new IllegalArgumentException("条件组合逻辑只能是 AND 或 OR");
            }
            if (MetricFilterCondition.TYPE_FACT_FIELD.equals(condition.getType())) {
                requireFactField(condition.getFactFieldName(), factFieldNames);
            } else if (MetricFilterCondition.TYPE_DIMENSION.equals(condition.getType())) {
                requireFactField(condition.getFactFieldName(), factFieldNames);
                if (condition.getDimensionModelId() == null) {
                    throw new IllegalArgumentException("维度条件请选择关联维度");
                }
                if (condition.getDimensionFieldName() == null || condition.getDimensionFieldName().isBlank()) {
                    throw new IllegalArgumentException("维度条件请选择维度字段");
                }
                List<ModelField> dimFields = modelFieldRepository.findByModelIdOrderBySortOrderAsc(condition.getDimensionModelId());
                boolean exists = dimFields.stream().anyMatch(f -> condition.getDimensionFieldName().equals(f.getFieldName()));
                if (!exists) {
                    throw new IllegalArgumentException("维度字段不存在：" + condition.getDimensionFieldName());
                }
            } else {
                requireFactField(condition.getFactFieldName(), factFieldNames);
            }
        }
    }

    private void requireFactField(String factFieldName, Set<String> factFieldNames) {
        if (factFieldName == null || factFieldName.isBlank()) {
            throw new IllegalArgumentException("业务限定请选择事实表字段");
        }
        if (!factFieldNames.contains(factFieldName)) {
            throw new IllegalArgumentException("业务限定字段不存在于所选事实表：" + factFieldName);
        }
    }

    private void validateDerived(MetricDTO dto) {
        if (dto.getFormula() == null || dto.getFormula().trim().isEmpty()) {
            throw new IllegalArgumentException("衍生指标公式不能为空");
        }
        String formula = dto.getFormula().trim();
        Set<String> codes = parseFormulaRefs(formula);
        if (codes.isEmpty()) {
            throw new IllegalArgumentException("衍生指标公式至少需要引用一个指标，如 ${sales_amount} / ${sales_quantity}");
        }
        String stripped = FORMULA_REF_PATTERN.matcher(formula).replaceAll("");
        if (!FORMULA_ALLOWED_PATTERN.matcher(stripped).matches()) {
            throw new IllegalArgumentException("衍生指标公式只能包含数字、运算符 + - * / ( ) 及 ${指标编码} 引用");
        }
        Map<String, Metric> byCode = metricRepository.findAll().stream().collect(Collectors.toMap(Metric::getCode, m -> m, (a, b) -> a));
        for (String code : codes) {
            if (!byCode.containsKey(code)) {
                throw new IllegalArgumentException("公式引用的指标不存在：" + code);
            }
        }
        if (dto.getId() != null && wouldCreateCycle(dto.getId(), formula, byCode)) {
            throw new IllegalArgumentException("衍生指标公式存在循环引用");
        }
    }

    /**
     * 根据计算公式解析引用关系，判断新增引用后是否会形成循环依赖。
     */
    boolean wouldCreateCycle(String metricId, String formula, Map<String, Metric> byCode) {
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (Metric metric : byCode.values()) {
            if (!Metric.TYPE_DERIVED.equals(metric.getMetricType()) || metricId.equals(metric.getId())) {
                continue;
            }
            adjacency.put(metric.getId(), resolveRefIds(metric.getFormula(), byCode));
        }
        for (String refId : resolveRefIds(formula, byCode)) {
            if (reaches(refId, metricId, adjacency, new HashSet<>())) {
                return true;
            }
        }
        return false;
    }

    private Set<String> resolveRefIds(String formula, Map<String, Metric> byCode) {
        Set<String> ids = new HashSet<>();
        for (String code : parseFormulaRefs(formula)) {
            Metric metric = byCode.get(code);
            if (metric != null) {
                ids.add(metric.getId());
            }
        }
        return ids;
    }

    private boolean reaches(String current, String target, Map<String, Set<String>> adjacency, Set<String> visited) {
        if (current == null) {
            return false;
        }
        if (current.equals(target)) {
            return true;
        }
        if (!visited.add(current)) {
            return false;
        }
        Set<String> next = adjacency.get(current);
        if (next == null) {
            return false;
        }
        for (String node : next) {
            if (reaches(node, target, adjacency, visited)) {
                return true;
            }
        }
        return false;
    }

    private void ensureCodeUnique(String code, String excludeId) {
        Optional<Metric> conflict = excludeId == null ? metricRepository.findByCode(code) : metricRepository.findByCodeAndIdNot(code, excludeId);
        if (conflict.isPresent()) {
            throw new IllegalArgumentException("指标编码已存在：" + code);
        }
    }

    public MetricFilterConfig parseFilterConfig(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MetricFilterConfig.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("业务限定格式错误：" + e.getOriginalMessage());
        }
    }
}
