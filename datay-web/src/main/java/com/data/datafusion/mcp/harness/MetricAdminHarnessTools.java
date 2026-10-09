package com.data.datafusion.mcp.harness;

import com.data.datafusion.domain.Metric;
import com.data.datafusion.service.MetricDirectoryService;
import com.data.datafusion.service.MetricService;
import com.data.datafusion.service.MetricSqlService;
import com.data.datafusion.service.dto.MetricDTO;
import com.data.datafusion.service.dto.MetricDirectoryDTO;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static com.data.datafusion.mcp.harness.DatayHarnessArgs.boolVal;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.clamp;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.intVal;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.reqStr;
import static com.data.datafusion.mcp.harness.DatayHarnessArgs.str;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.bool;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.enumeration;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.integer;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.object;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.properties;
import static com.data.datafusion.mcp.harness.DatayHarnessSchema.string;

/**
 * 指标定义的 Harness 管理工具族（增删改查）。
 *
 * <p>与 {@link MetricHarnessTools}（指标问数，只读）共用 {@code metric} 分组，但职责不同：
 * 这里维护<strong>指标定义本身</strong>（口径、公式、事实表、业务限定、状态、目录），
 * 对齐 {@code /api/metrics} 与 {@code /api/metric-directories} 的 REST 行为。
 *
 * <p>工具名刻意用 {@code metric_*} 前缀，与问数工具沿用过来的 {@code list_metrics} /
 * {@code describe_metrics} 等旧名区分开：问数是「用指标取数」，这里是「管指标定义」，
 * 两者说明里都写了互相的指引，避免模型选错。
 *
 * <p>校验规则完全交给 {@link MetricService}（编码唯一、原子指标须绑定 DWD 事实表、
 * 衍生指标公式须引用已存在指标且不成环等），本类只做入参搬运与「部分更新」的字段合并，
 * 保证与页面保存走同一套口径。
 *
 * <p>{@code @Bean} 方法名统一加 {@code HarnessTool} 后缀：本包的指标命名空间里已有大量既有
 * Bean（如 {@code metricDirectoryService}），本仓库开启了 bean 覆盖，一旦同名会静默顶替原 Bean。
 */
@Configuration
public class MetricAdminHarnessTools {

    /**
     * 业务限定条件的类型枚举，供 filterConfig 说明使用。
     */
    private static final List<String> FILTER_CONDITION_TYPES = List.of("FACT_FIELD", "DIMENSION", "TIME");

    private final MetricService metricService;
    private final MetricDirectoryService metricDirectoryService;
    private final MetricSqlService metricSqlService;

    public MetricAdminHarnessTools(
        MetricService metricService,
        MetricDirectoryService metricDirectoryService,
        MetricSqlService metricSqlService
    ) {
        this.metricService = metricService;
        this.metricDirectoryService = metricDirectoryService;
        this.metricSqlService = metricSqlService;
    }

    // ------------------------------------------------------------------ 指标定义

    @Bean
    DatayHarnessTool metricListHarnessTool() {
        return DatayHarnessTool.read(
            "metric_list",
            "分页查询指标定义（管理视角，含停用指标与公式、事实表等完整字段）。" +
            "做指标问数、按口语找指标编码请改用 list_metrics。",
            object(
                properties(
                    "page", integer("页码，从 0 开始，默认 0"),
                    "size", integer("每页条数，默认 20，最大 200"),
                    "search", string("可选关键字，按指标名称/编码/口径过滤"),
                    "metricType", enumeration(List.of(Metric.TYPE_ATOMIC, Metric.TYPE_DERIVED), "指标类型过滤（可选）")
                )
            ),
            (args, context) -> {
                int pageIndex = Math.max(0, intVal(args, "page", 0));
                int size = clamp(intVal(args, "size", 20), 1, 200);
                Page<MetricDTO> page = metricService.findAll(
                    PageRequest.of(pageIndex, size),
                    str(args, "search"),
                    str(args, "metricType")
                );
                return DatayHarnessViews.page(page, MetricAdminHarnessTools::metricView);
            }
        );
    }

    @Bean
    DatayHarnessTool metricGetHarnessTool() {
        return DatayHarnessTool.read(
            "metric_get",
            "按 ID 查询单个指标定义，含公式、事实表、业务限定、状态与引用关系。",
            object(properties("id", string("指标 ID")), "id"),
            (args, context) -> metricView(requireMetric(reqStr(args, "id")))
        );
    }

    @Bean
    DatayHarnessTool metricCreateHarnessTool() {
        return DatayHarnessTool.write(
            "metric_create",
            "新建指标定义。原子指标（ATOMIC）必须给出 factModelId（须为 DWD 模型）与 formula；" +
            "衍生指标（DERIVED）的 formula 需用 ${指标编码} 引用已有指标，例如 ${sales_amount} / ${sales_quantity}。" +
            "编码 code 全局唯一且只能包含字母、数字、下划线、中划线。",
            object(metricDefinitionProperties(), metricDefinitionRequired()),
            (args, context) -> metricView(metricService.save(toNewMetric(args)))
        );
    }

    @Bean
    DatayHarnessTool metricUpdateHarnessTool() {
        return DatayHarnessTool.write(
            "metric_update",
            "按 ID 更新指标定义，只提交需要修改的字段，未提交的字段沿用原值（内部先合并再走平台的完整校验）。" +
            "注意：原子指标改事实表后，原公式里的字段名可能不再存在于新事实表，校验会失败。",
            object(metricDefinitionProperties(), "id"),
            (args, context) -> {
                String id = reqStr(args, "id");
                MetricDTO existing = requireMetric(id);
                applyMetricFields(existing, args);
                return metricService
                    .update(id, existing)
                    .map(MetricAdminHarnessTools::metricView)
                    .orElseThrow(() -> new IllegalArgumentException("指标不存在: " + id));
            }
        );
    }

    @Bean
    DatayHarnessTool metricDeleteHarnessTool() {
        return DatayHarnessTool.write(
            "metric_delete",
            "按 ID 删除指标定义。若该指标被其它衍生指标的公式引用，平台会拒绝删除（需先解除引用）。",
            object(properties("id", string("指标 ID")), "id"),
            (args, context) -> {
                String id = reqStr(args, "id");
                MetricDTO existing = requireMetric(id);
                metricService.delete(id);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("deletedId", id);
                result.put("code", existing.getCode());
                result.put("name", existing.getName());
                return result;
            }
        );
    }

    @Bean
    DatayHarnessTool metricPreviewSqlHarnessTool() {
        return DatayHarnessTool.read(
            "metric_preview_sql",
            "预览指标的计算 SQL，不执行。传 id 时用已保存的定义；也可以直接传一份指标定义草稿来验证公式是否正确。",
            object(
                properties(
                    "id", string("已保存指标的 ID；给出时忽略下面的草稿字段"),
                    "code", string("草稿模式：指标编码"),
                    "name", string("草稿模式：指标名称（可选）"),
                    "metricType", enumeration(List.of(Metric.TYPE_ATOMIC, Metric.TYPE_DERIVED), "草稿模式：指标类型"),
                    "dataType", enumeration(MetricService.DATA_TYPES, "草稿模式：数据类型"),
                    "formula", string("草稿模式：计算公式"),
                    "factModelId", string("草稿模式：事实表模型 ID（原子指标）"),
                    "filterConfig", string("草稿模式：业务限定 JSON（可选）")
                )
            ),
            (args, context) -> {
                String id = str(args, "id");
                MetricDTO metric;
                if (id != null) {
                    metric = requireMetric(id);
                } else {
                    metric = new MetricDTO();
                    metric.setCode(reqStr(args, "code"));
                    metric.setName(str(args, "name"));
                    metric.setMetricType(str(args, "metricType"));
                    metric.setDataType(str(args, "dataType"));
                    metric.setFormula(str(args, "formula"));
                    metric.setFactModelId(str(args, "factModelId"));
                    metric.setFilterConfig(str(args, "filterConfig"));
                }
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("metric", metric.getCode());
                result.put("sql", metricSqlService.generateSql(metric));
                return result;
            }
        );
    }

    // ------------------------------------------------------------------ 指标目录

    @Bean
    DatayHarnessTool metricDirectoryListHarnessTool() {
        return DatayHarnessTool.read(
            "metric_directory_list",
            "查询指标目录。传 parentId 返回其下子目录，不传返回全部目录。",
            object(properties("parentId", string("父目录 ID（可选）"))),
            (args, context) -> {
                String parentId = str(args, "parentId");
                List<MetricDirectoryDTO> directories = parentId == null
                    ? metricDirectoryService.findAll()
                    : metricDirectoryService.findByParentId(parentId);
                return DatayHarnessViews.list(directories, MetricAdminHarnessTools::directoryView);
            }
        );
    }

    @Bean
    DatayHarnessTool metricDirectoryCreateHarnessTool() {
        return DatayHarnessTool.write(
            "metric_directory_create",
            "新建指标目录，用于归类指标定义。",
            object(
                properties(
                    "name", string("目录名称"),
                    "parentId", string("父目录 ID（可选，不传为根目录）"),
                    "sortOrder", integer("排序值（可选，默认 0）")
                ),
                "name"
            ),
            (args, context) -> {
                MetricDirectoryDTO dto = new MetricDirectoryDTO();
                dto.setName(reqStr(args, "name"));
                dto.setParentId(str(args, "parentId"));
                dto.setSortOrder(intVal(args, "sortOrder", 0));
                return directoryView(metricDirectoryService.save(dto));
            }
        );
    }

    @Bean
    DatayHarnessTool metricDirectoryUpdateHarnessTool() {
        return DatayHarnessTool.write(
            "metric_directory_update",
            "按 ID 更新指标目录，只提交需要修改的字段（可改名或调整父目录、排序）。",
            object(
                properties(
                    "id", string("指标目录 ID"),
                    "name", string("目录名称"),
                    "parentId", string("父目录 ID（传 0 或留空表示根目录）"),
                    "sortOrder", integer("排序值")
                ),
                "id"
            ),
            (args, context) -> {
                String id = reqStr(args, "id");
                MetricDirectoryDTO existing = metricDirectoryService
                    .findOne(id)
                    .orElseThrow(() -> new IllegalArgumentException("指标目录不存在: " + id));
                if (str(args, "name") != null) {
                    existing.setName(str(args, "name"));
                }
                if (args.containsKey("parentId")) {
                    String parentId = str(args, "parentId");
                    existing.setParentId(parentId == null || "0".equals(parentId) ? null : parentId);
                }
                if (args.containsKey("sortOrder")) {
                    existing.setSortOrder(intVal(args, "sortOrder", existing.getSortOrder()));
                }
                return directoryView(metricDirectoryService.update(existing));
            }
        );
    }

    @Bean
    DatayHarnessTool metricDirectoryDeleteHarnessTool() {
        return DatayHarnessTool.write(
            "metric_directory_delete",
            "按 ID 删除指标目录。子目录与目录下的指标会被移动到根目录，不会被级联删除。",
            object(properties("id", string("指标目录 ID")), "id"),
            (args, context) -> {
                String id = reqStr(args, "id");
                MetricDirectoryDTO existing = metricDirectoryService
                    .findOne(id)
                    .orElseThrow(() -> new IllegalArgumentException("指标目录不存在: " + id));
                metricDirectoryService.delete(id);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("success", true);
                result.put("deletedId", id);
                result.put("name", existing.getName());
                result.put("note", "子目录与目录下的指标已移动到根目录");
                return result;
            }
        );
    }

    // ------------------------------------------------------------------ schema

    private static Map<String, Object> metricDefinitionProperties() {
        Map<String, Object> properties = properties(
            "id", string("指标 ID（更新时必填）"),
            "name", string("指标名称"),
            "code", string("指标编码，唯一，仅字母/数字/下划线/中划线，如 sales_amount"),
            "description", string("口径说明"),
            "directoryId", string("所属指标目录 ID（可选）"),
            "metricType", enumeration(List.of(Metric.TYPE_ATOMIC, Metric.TYPE_DERIVED), "指标类型：ATOMIC 原子 / DERIVED 衍生"),
            "status", enumeration(List.of(Metric.STATUS_ENABLED, Metric.STATUS_DISABLED), "状态，缺省 ENABLED"),
            "factModelId", string("事实表模型 ID（原子指标必填，且模型类型须为 DWD）"),
            "formula", string(
                "计算公式。原子指标为字段聚合表达式（如 SUM(amount)）；衍生指标用 ${指标编码} 引用其它指标（如 ${sales_amount} / ${sales_quantity}）"
            ),
            "filterConfig", string(
                "业务限定配置，JSON 字符串，形如 {\"conditions\":[{\"type\":\"DIMENSION\",\"dimensionModelCode\":\"dim_store\"," +
                "\"dimensionFieldName\":\"city\",\"operator\":\"EQ\",\"value\":\"北京\",\"logic\":\"AND\"}]}。" +
                "type 取 " + FILTER_TYPES_HINT + "；operator 取 EQ/NE/GT/GE/LT/LE/IN/NOT_IN/LIKE/IS_NULL/IS_NOT_NULL/BETWEEN；" +
                "FACT_FIELD 用 factFieldName，DIMENSION/TIME 用 dimensionModelCode + dimensionFieldName；衍生指标会忽略该字段"
            ),
            "unit", string("单位，如 元 / 件 / 单 / 比例"),
            "dataType", enumeration(MetricService.DATA_TYPES, "数据类型"),
            "isAdditive", bool("是否可加（可选）")
        );
        return properties;
    }

    private static final String FILTER_TYPES_HINT = String.join("/", FILTER_CONDITION_TYPES);

    private static String[] metricDefinitionRequired() {
        return new String[] { "name", "code", "metricType", "dataType", "formula" };
    }

    // ------------------------------------------------------------------ helpers

    private MetricDTO requireMetric(String id) {
        return metricService.findOne(id).orElseThrow(() -> new IllegalArgumentException("指标不存在: " + id));
    }

    /**
     * 新建：只搬运入参，校验交给 {@link MetricService#save}。
     */
    private static MetricDTO toNewMetric(Map<String, Object> args) {
        MetricDTO dto = new MetricDTO();
        dto.setName(reqStr(args, "name"));
        dto.setCode(reqStr(args, "code"));
        dto.setMetricType(reqStr(args, "metricType"));
        dto.setDataType(reqStr(args, "dataType"));
        applyMetricFields(dto, args);
        return dto;
    }

    /**
     * 把入参搬到 DTO 上：只覆盖显式给出的字段，未给出的保持原值，
     * 因此同一个方法既能用于新建（在已有 DTO 上继续填充），也能用于更新的部分合并。
     */
    private static void applyMetricFields(MetricDTO dto, Map<String, Object> args) {
        if (args.containsKey("name")) {
            dto.setName(str(args, "name"));
        }
        if (args.containsKey("code")) {
            dto.setCode(str(args, "code"));
        }
        if (args.containsKey("description")) {
            dto.setDescription(str(args, "description"));
        }
        if (args.containsKey("directoryId")) {
            dto.setDirectoryId(str(args, "directoryId"));
        }
        if (args.containsKey("metricType")) {
            dto.setMetricType(str(args, "metricType"));
        }
        if (args.containsKey("status")) {
            dto.setStatus(str(args, "status"));
        }
        if (args.containsKey("factModelId")) {
            dto.setFactModelId(str(args, "factModelId"));
        }
        if (args.containsKey("formula")) {
            dto.setFormula(str(args, "formula"));
        }
        if (args.containsKey("filterConfig")) {
            dto.setFilterConfig(str(args, "filterConfig"));
        }
        if (args.containsKey("unit")) {
            dto.setUnit(str(args, "unit"));
        }
        if (args.containsKey("dataType")) {
            dto.setDataType(str(args, "dataType"));
        }
        if (args.containsKey("isAdditive")) {
            dto.setIsAdditive(boolVal(args, "isAdditive", false));
        }
    }

    /**
     * 指标定义视图：把校验相关的关键字段都带上，便于模型确认后再改。
     */
    private static Map<String, Object> metricView(MetricDTO dto) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", dto.getId());
        view.put("code", dto.getCode());
        view.put("name", dto.getName());
        view.put("description", dto.getDescription());
        view.put("directoryId", dto.getDirectoryId());
        view.put("metricType", dto.getMetricType());
        view.put("status", dto.getStatus());
        view.put("factModelId", dto.getFactModelId());
        view.put("factModelName", dto.getFactModelName());
        view.put("factTableName", dto.getFactTableName());
        view.put("formula", dto.getFormula());
        view.put("filterConfig", dto.getFilterConfig());
        view.put("unit", dto.getUnit());
        view.put("dataType", dto.getDataType());
        view.put("isAdditive", dto.getIsAdditive());
        view.put("createTime", dto.getCreateTime());
        view.put("updateTime", dto.getUpdateTime());
        List<Map<String, Object>> refs = new ArrayList<>();
        if (dto.getRefMetrics() != null) {
            dto.getRefMetrics().forEach(ref -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", ref.getId());
                item.put("code", ref.getCode());
                item.put("name", ref.getName());
                item.put("metricType", ref.getMetricType());
                item.put("status", ref.getStatus());
                refs.add(item);
            });
        }
        view.put("refMetrics", refs);
        return view;
    }

    private static Map<String, Object> directoryView(MetricDirectoryDTO dto) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", dto.getId());
        view.put("name", dto.getName());
        view.put("parentId", dto.getParentId());
        view.put("sortOrder", dto.getSortOrder());
        view.put("createTime", dto.getCreateTime());
        view.put("updateTime", dto.getUpdateTime());
        return view;
    }
}
