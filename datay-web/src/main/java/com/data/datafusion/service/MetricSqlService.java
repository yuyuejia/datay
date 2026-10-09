package com.data.datafusion.service;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.domain.Metric;
import com.data.datafusion.domain.ModelField;
import com.data.datafusion.repository.DataModelRepository;
import com.data.datafusion.repository.MetricRepository;
import com.data.datafusion.repository.ModelFieldRepository;
import com.data.datafusion.service.dto.MetricDTO;
import com.data.datafusion.service.mapper.MetricMapper;
import com.data.datafusion.service.metric.MetricFilterCondition;
import com.data.datafusion.service.metric.MetricFilterConfig;
import com.data.datafusion.service.metric.MetricFilterOperator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 根据指标定义生成计算 SQL（仅预览，不执行）。
 */
@Service
public class MetricSqlService {

    private static final Logger LOG = LoggerFactory.getLogger(MetricSqlService.class);

    private static final Pattern FORMULA_REF_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9_\\-]+)}");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("^-?\\d+(\\.\\d+)?$");

    private final MetricRepository metricRepository;
    private final DataModelRepository dataModelRepository;
    private final ModelFieldRepository modelFieldRepository;
    private final MetricMapper metricMapper;
    private final ObjectMapper objectMapper;

    public MetricSqlService(
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

    /**
     * 生成指标计算 SQL。
     */
    public String generateSql(MetricDTO metric) {
        if (Metric.TYPE_ATOMIC.equals(metric.getMetricType())) {
            return atomicSelect(metric, metric.getCode());
        }
        if (Metric.TYPE_DERIVED.equals(metric.getMetricType())) {
            Map<String, String> ctes = new LinkedHashMap<>();
            registerCte(metric, ctes, new HashSet<>());
            StringBuilder sb = new StringBuilder();
            sb.append("WITH ");
            boolean first = true;
            for (Map.Entry<String, String> entry : ctes.entrySet()) {
                if (!first) {
                    sb.append(",\n");
                }
                sb.append(cteName(entry.getKey())).append(" AS (\n").append(entry.getValue()).append("\n)");
                first = false;
            }
            sb.append("\nSELECT v AS ").append(metric.getCode()).append(" FROM ").append(cteName(metric.getCode()));
            return sb.toString();
        }
        throw new IllegalArgumentException("无法为未知指标类型生成 SQL：" + metric.getMetricType());
    }

    private void registerCte(MetricDTO metric, Map<String, String> ctes, Set<String> visiting) {
        String code = metric.getCode();
        if (code == null || ctes.containsKey(code) || !visiting.add(code)) {
            return;
        }
        if (Metric.TYPE_ATOMIC.equals(metric.getMetricType())) {
            ctes.put(code, atomicSelect(metric, "v"));
        } else {
            Map<String, Metric> byCode = metricRepository.findAll().stream().collect(Collectors.toMap(Metric::getCode, m -> m, (a, b) -> a));
            for (String refCode : parseFormulaRefs(metric.getFormula())) {
                Metric refMetric = byCode.get(refCode);
                if (refMetric == null) {
                    throw new IllegalArgumentException("公式引用的指标不存在：" + refCode);
                }
                registerCte(metricMapper.toDto(refMetric), ctes, visiting);
            }
            ctes.put(code, "SELECT " + buildFormulaExpr(metric.getFormula()) + " AS v");
        }
        visiting.remove(code);
    }

    private String buildFormulaExpr(String formula) {
        if (formula == null) {
            return "NULL";
        }
        Matcher matcher = FORMULA_REF_PATTERN.matcher(formula);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement("(SELECT v FROM " + cteName(matcher.group(1)) + ")"));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private Set<String> parseFormulaRefs(String formula) {
        Set<String> codes = new java.util.LinkedHashSet<>();
        if (formula == null) {
            return codes;
        }
        Matcher matcher = FORMULA_REF_PATTERN.matcher(formula);
        while (matcher.find()) {
            codes.add(matcher.group(1));
        }
        return codes;
    }

    private String cteName(String code) {
        return "m_" + (code == null ? "unknown" : code.replaceAll("[^A-Za-z0-9_]", "_"));
    }

    /**
     * 生成原子指标 SELECT。列命名为 alias。
     */
    private String atomicSelect(MetricDTO metric, String alias) {
        DataModel factModel = dataModelRepository
            .findById(metric.getFactModelId())
            .orElseThrow(() -> new IllegalArgumentException("事实表不存在：" + metric.getFactModelId()));
        String table = physicalTableName(factModel);
        String factAlias = "f";

        StringBuilder joins = new StringBuilder();
        Map<String, String> dimensionAlias = new HashMap<>();
        List<String> conditions = new ArrayList<>();

        MetricFilterConfig config = parseFilterConfig(metric.getFilterConfig());
        if (config != null && !config.isEmpty()) {
            for (MetricFilterCondition condition : config.getConditions()) {
                String column;
                if (MetricFilterCondition.TYPE_DIMENSION.equals(condition.getType())) {
                    String dimAlias = ensureDimensionJoin(condition, dimensionAlias, joins);
                    column = dimAlias + "." + condition.getDimensionFieldName();
                } else if (
                    MetricFilterCondition.TYPE_TIME.equals(condition.getType()) &&
                    condition.getDimensionModelId() != null &&
                    condition.getDimensionFieldName() != null &&
                    !condition.getDimensionFieldName().isBlank()
                ) {
                    String dimAlias = ensureDimensionJoin(condition, dimensionAlias, joins);
                    column = dimAlias + "." + condition.getDimensionFieldName();
                } else {
                    column = factAlias + "." + condition.getFactFieldName();
                }
                String predicate = buildPredicate(condition, column);
                if (predicate != null) {
                    boolean first = conditions.isEmpty();
                    String logic = first ? "" : " " + normalizeLogic(condition.getLogic()) + " ";
                    conditions.add(logic + "(" + predicate + ")");
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("SELECT ").append(buildAtomicExpression(metric)).append(" AS ").append(alias);
        sb.append("\nFROM ").append(table).append(" ").append(factAlias);
        sb.append(joins);
        if (!conditions.isEmpty()) {
            sb.append("\nWHERE ").append(String.join("", conditions));
        }
        return sb.toString();
    }

    private String ensureDimensionJoin(MetricFilterCondition condition, Map<String, String> dimensionAlias, StringBuilder joins) {
        String dimModelId = condition.getDimensionModelId();
        String alias = dimensionAlias.get(dimModelId);
        if (alias != null) {
            return alias;
        }
        DataModel dimModel = dataModelRepository
            .findById(dimModelId)
            .orElseThrow(() -> new IllegalArgumentException("维度模型不存在：" + dimModelId));
        String dimTable = physicalTableName(dimModel);
        List<ModelField> dimFields = modelFieldRepository.findByModelIdOrderBySortOrderAsc(dimModelId);
        String dimPk = dimFields
            .stream()
            .filter(f -> Boolean.TRUE.equals(f.getIsPrimaryKey()))
            .map(ModelField::getFieldName)
            .findFirst()
            .orElseGet(() ->
                dimFields.stream().map(ModelField::getFieldName).filter(condition.getFactFieldName()::equals).findFirst().orElse(null)
            );
        alias = "d" + dimensionAlias.size();
        dimensionAlias.put(dimModelId, alias);
        if (dimPk == null) {
            joins.append("\nLEFT JOIN ").append(dimTable).append(" ").append(alias);
        } else {
            joins
                .append("\nLEFT JOIN ")
                .append(dimTable)
                .append(" ")
                .append(alias)
                .append(" ON f.")
                .append(condition.getFactFieldName())
                .append(" = ")
                .append(alias)
                .append(".")
                .append(dimPk);
        }
        return alias;
    }

    private String buildAtomicExpression(MetricDTO metric) {
        String formula = metric.getFormula();
        return formula == null || formula.trim().isEmpty() ? "1" : formula.trim();
    }

    private String buildPredicate(MetricFilterCondition condition, String column) {
        MetricFilterOperator operator = MetricFilterOperator.fromCode(condition.getOperator());
        if (operator == null) {
            throw new IllegalArgumentException("不支持的业务限定运算符：" + condition.getOperator());
        }
        if (operator.isUnary()) {
            return column + " " + operator.getSymbol();
        }
        if (operator.isRange()) {
            if (condition.getValue() == null || condition.getValueEnd() == null) {
                throw new IllegalArgumentException("BETWEEN 条件需要提供起止值");
            }
            return column + " BETWEEN " + quote(condition.getValue()) + " AND " + quote(condition.getValueEnd());
        }
        if (operator == MetricFilterOperator.IN || operator == MetricFilterOperator.NOT_IN) {
            String[] parts = condition.getValue() == null ? new String[0] : condition.getValue().split(",");
            String values = java.util.Arrays.stream(parts).map(String::trim).filter(s -> !s.isEmpty()).map(this::quote).collect(Collectors.joining(", "));
            return column + " " + operator.getSymbol() + " (" + values + ")";
        }
        if (condition.getValue() == null) {
            throw new IllegalArgumentException("业务限定缺少取值");
        }
        return column + " " + operator.getSymbol() + " " + quote(condition.getValue());
    }

    private String normalizeLogic(String logic) {
        return "OR".equalsIgnoreCase(logic) ? "OR" : "AND";
    }

    private String quote(String value) {
        if (value == null) {
            return "NULL";
        }
        String trimmed = value.trim();
        if (NUMBER_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }
        return "'" + trimmed.replace("'", "''") + "'";
    }

    private String physicalTableName(DataModel model) {
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

    private MetricFilterConfig parseFilterConfig(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MetricFilterConfig.class);
        } catch (JsonProcessingException e) {
            LOG.warn("解析业务限定失败：{}", e.getOriginalMessage());
            throw new IllegalArgumentException("业务限定格式错误：" + e.getOriginalMessage());
        }
    }
}
