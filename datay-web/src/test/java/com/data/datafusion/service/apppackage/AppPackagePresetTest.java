package com.data.datafusion.service.apppackage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.repository.AnalysisDatasetRepository;
import com.data.datafusion.service.apppackage.model.AppPackageContent;
import com.data.datafusion.service.apppackage.model.AppPackageEtlTask;
import com.data.datafusion.service.apppackage.model.AppPackageMetric;
import com.data.datafusion.service.apppackage.model.AppPackageModel;
import com.data.datafusion.service.dashboard.AnalysisDatasetResolver;
import com.data.datafusion.service.dashboard.DashboardSpecValidator;
import com.data.job.ComponentDescriptor;
import com.data.job.ComponentFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * 系统预制资产包自检。
 *
 * <p>预置资产包是随程序发布的静态资源，一旦引用写错，租户初始化时才会暴露问题。
 * 这里对 {@code src/main/resources/app-packages/*.json} 做离线校验：格式合法、
 * 包内引用自洽（逻辑 ID 全部可解析）、衍生指标与编排任务的依赖都存在。
 */
class AppPackagePresetTest {

    private static final Pattern FORMULA_REF_PATTERN = Pattern.compile("\\$\\{([A-Za-z0-9_\\-]+)}");

    private static final String[] PRESET_FILES = { "app-packages/ecommerce-sales-app.json", "app-packages/datay-quickstart-app.json" };

    /**
     * 设计器侧组件：由 {@code ETLNodeTranslator} 在运行时注册，不在 {@code ComponentFactory} 的代码扫描结果里。
     * 与 {@code ModelWriteNodeTranslator} / {@code FileInputNodeTranslator} / {@code LlmNodeTranslator} 保持一致。
     */
    private static final Set<String> TRANSLATOR_COMPONENTS = Set.of("ModelWrite", "FileInput", "LlmComponent");

    @Test
    void presetsAreValidAndSelfContained() throws IOException {
        for (String file : PRESET_FILES) {
            AppPackageContent content = load(file);
            assertThat(content.getPackageFormat()).as(file).isEqualTo(AppPackageContent.FORMAT);
            assertThat(content.getMeta().getCode()).as(file).isNotBlank();
            assertThat(content.getMeta().getCategory()).as(file).isNotBlank();
            assertThat(content.rebuildSummary().get("total")).as(file).isPositive();
            assertReferences(file, content);
            assertEtlNodeTypesAreRegistered(file, content);
            assertEtlFieldLengths(file, content);
            assertDataSourceFieldLengths(file, content);
        }
    }

    @Test
    void quickstartIncludesCdcAndDecisionDemos() throws IOException {
        AppPackageContent content = load("app-packages/datay-quickstart-app.json");

        AppPackageEtlTask cdc = content.getEtlTasks().stream().filter(task -> "qs_etl_mysql_cdc".equals(task.taskCode)).findFirst().orElseThrow();
        assertThat(cdc.nodes).extracting(node -> node.type).contains("MySQLBinlogInput", "StreamJdbcOutput");

        AppPackageEtlTask decision = content.getEtlTasks().stream().filter(task -> "qs_etl_decision_demo".equals(task.taskCode)).findFirst().orElseThrow();
        assertThat(decision.nodes).extracting(node -> node.type).contains("GenerateFlowFile", "DecisionComponent", "StreamJdbcOutput");

        AppPackageEtlTask postgresLake = content.getEtlTasks().stream().filter(task -> "qs_etl_pg_cdc_ducklake".equals(task.taskCode)).findFirst().orElseThrow();
        assertThat(postgresLake.nodes).extracting(node -> node.type).contains("PostgresCDCInput", "DuckLakeWrite");
    }

    @Test
    void presetDashboardsMatchSpecStructure() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        AnalysisDatasetRepository repository = mock(AnalysisDatasetRepository.class);
        AnalysisDataset dataset = new AnalysisDataset();
        dataset.setId("dataset-id");
        dataset.setName("演示数据集");
        dataset.setType(AnalysisDataset.TYPE_SQL);
        dataset.setDefinition("{\"sql\":\"SELECT 1 AS value\"}");
        when(repository.existsById(anyString())).thenReturn(true);
        when(repository.findById(anyString())).thenReturn(Optional.of(dataset));
        DashboardSpecValidator validator = new DashboardSpecValidator(objectMapper, new AnalysisDatasetResolver(repository, objectMapper));

        for (String file : PRESET_FILES) {
            AppPackageContent content = load(file);
            for (var dashboard : content.getDashboards()) {
                assertThat(validator.validateAndNormalize(dashboard.spec.toString()))
                    .as("%s: dashboard %s spec", file, dashboard.code)
                    .containsKeys("datasets", "widgets", "filters", "layout");
            }
        }
    }


    @Test
    void presetDashboardsUseVersion1Spec() throws IOException {
        for (String file : PRESET_FILES) {
            AppPackageContent content = load(file);
            for (var dashboard : content.getDashboards()) {
                JsonNode spec = dashboard.spec;
                assertThat(spec.path("version").asInt()).as(file + ": " + dashboard.code).isEqualTo(1);
                assertThat(spec.has("dataRef")).isFalse();
                for (JsonNode dataset : spec.path("datasets")) {
                    assertThat(dataset.has("dataRef")).isFalse();
                }
                for (JsonNode widget : spec.path("widgets")) {
                    assertThat(widget.has("dataRef")).as(file + ": " + dashboard.code).isFalse();
                    String type = widget.path("type").asText();
                    if ("echarts".equals(type) || "kpi".equals(type)) {
                        JsonNode values = widget.path("encode").path("values");
                        assertThat(values.isArray() && !values.isEmpty()).as(file + ": " + dashboard.code + " " + widget.path("id").asText()).isTrue();
                        for (JsonNode value : values) {
                            assertThat(value.path("agg").asText()).isNotBlank();
                        }
                    }
                }
                for (JsonNode filter : spec.path("filters")) {
                    assertThat(filter.has("bindings")).isFalse();
                    assertThat(filter.has("datasetId")).isFalse();
                    assertThat(filter.path("targets").isArray()).isTrue();
                }
            }
        }
    }

    /**
     * ETL 任务与节点上的文本字段最终写入 varchar(255) 列，超长会在初始化时触发数据库截断错误。
     */
    private void assertEtlFieldLengths(String file, AppPackageContent content) {
        for (AppPackageEtlTask task : content.getEtlTasks()) {
            assertLength(file, task.taskCode, "taskCode", task.taskCode);
            assertLength(file, task.taskName, "taskName", task.taskCode);
            assertLength(file, task.taskDesc, "taskDesc", task.taskCode);
            assertLength(file, task.dir, "dir", task.taskCode);
            assertLength(file, task.type, "type", task.taskCode);
            assertLength(file, task.cron, "cron", task.taskCode);
            for (AppPackageEtlTask.AppPackageEtlNode node : task.nodes) {
                assertLength(file, node.label, "node.label", task.taskCode + "." + node.code);
                assertLength(file, node.desc, "node.desc", task.taskCode + "." + node.code);
                assertLength(file, node.type, "node.type", task.taskCode + "." + node.code);
            }
        }
    }

    private void assertDataSourceFieldLengths(String file, AppPackageContent content) {
        for (var dataSource : content.getDataSources()) {
            assertLength(file, dataSource.name, "dataSource.name", dataSource.name);
            assertLength(file, dataSource.description, "dataSource.description", dataSource.name);
            assertLength(file, dataSource.type, "dataSource.type", dataSource.name);
            assertLength(file, dataSource.url, "dataSource.url", dataSource.name);
        }
    }

    private void assertLength(String file, String value, String field, String owner) {
        assertThat(value == null ? 0 : value.length())
            .as("%s: %s 的 %s 超过 varchar(255)", file, owner, field)
            .isLessThanOrEqualTo(255);
    }

    /**
     * ETL 节点类型必须是引擎/设计器真实注册的组件。
     *
     * <p>这是曾经踩过的坑：包内写成 {@code JdbcInput}（类存在但 {@code @ComponentRegister} 被注释，
     * 未注册到组件工厂），离线校验一切正常，只有真正运行任务时才报「组件初始化失败」。
     */
    private void assertEtlNodeTypesAreRegistered(String file, AppPackageContent content) {
        Set<String> registered = ComponentFactory.listDescriptors().stream().map(ComponentDescriptor::getCode).collect(Collectors.toSet());
        for (AppPackageEtlTask task : content.getEtlTasks()) {
            for (AppPackageEtlTask.AppPackageEtlNode node : task.nodes) {
                assertThat(registered.contains(node.type) || TRANSLATOR_COMPONENTS.contains(node.type))
                    .as("%s: ETL 任务 %s 的节点类型 %s 未注册到组件工厂", file, task.taskCode, node.type)
                    .isTrue();
            }
        }
    }

    private AppPackageContent load(String file) throws IOException {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(file)) {
            assertThat(in).as("classpath resource %s", file).isNotNull();
            return AppPackageJson.readContent(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private void assertReferences(String file, AppPackageContent content) {
        Set<String> dataSourceIds = ids(content.getDataSources().stream().map(item -> item.oldId).collect(Collectors.toSet()));
        Set<String> modelIds = ids(content.getModels().stream().map(item -> item.oldId).collect(Collectors.toSet()));
        Set<String> modelDirectoryIds = ids(content.getModelDirectories().stream().map(item -> item.oldId).collect(Collectors.toSet()));
        Set<String> metricDirectoryIds = ids(content.getMetricDirectories().stream().map(item -> item.oldId).collect(Collectors.toSet()));
        Set<String> datasetIds = ids(content.getDatasets().stream().map(item -> item.oldId).collect(Collectors.toSet()));
        Set<String> modelFieldIds = new LinkedHashSet<>();
        content.getModels().forEach(model -> model.fields.forEach(field -> modelFieldIds.add(field.oldId)));
        Set<String> metricCodes = content.getMetrics().stream().map(metric -> metric.oldId).collect(Collectors.toSet());
        Set<String> metricCodeNames = content.getMetrics().stream().map(metric -> metric.code).collect(Collectors.toSet());

        // 数据源与目录
        for (AppPackageModel model : content.getModels()) {
            if (model.dataSourceOldId != null) {
                assertThat(dataSourceIds).as("%s: model %s dataSourceOldId", file, model.code).contains(model.dataSourceOldId);
            }
            if (model.directoryOldId != null) {
                assertThat(modelDirectoryIds).as("%s: model %s directoryOldId", file, model.code).contains(model.directoryOldId);
            }
            for (AppPackageModel.AppPackageModelField field : model.fields) {
                if (field.dimensionModelOldId != null) {
                    assertThat(modelIds).as("%s: field %s dimensionModelOldId", file, field.fieldName).contains(field.dimensionModelOldId);
                }
                if (field.dimensionFieldOldId != null) {
                    assertThat(modelFieldIds).as("%s: field %s dimensionFieldOldId", file, field.fieldName).contains(field.dimensionFieldOldId);
                }
            }
        }

        // 目录父子关系
        for (var directory : content.getModelDirectories()) {
            if (directory.parentOldId != null) {
                assertThat(modelDirectoryIds).as("%s: model directory %s parentOldId", file, directory.name).contains(directory.parentOldId);
            }
        }
        for (var directory : content.getMetricDirectories()) {
            if (directory.parentOldId != null) {
                assertThat(metricDirectoryIds).as("%s: metric directory %s parentOldId", file, directory.name).contains(directory.parentOldId);
            }
        }

        // 指标：事实模型、目录、衍生公式引用
        for (AppPackageMetric metric : content.getMetrics()) {
            if (metric.factModelOldId != null) {
                assertThat(modelIds).as("%s: metric %s factModelOldId", file, metric.code).contains(metric.factModelOldId);
            }
            if (metric.directoryOldId != null) {
                assertThat(metricDirectoryIds).as("%s: metric %s directoryOldId", file, metric.code).contains(metric.directoryOldId);
            }
            if ("DERIVED".equals(metric.metricType)) {
                Matcher matcher = FORMULA_REF_PATTERN.matcher(metric.formula == null ? "" : metric.formula);
                while (matcher.find()) {
                    assertThat(metricCodeNames).as("%s: metric %s references %s", file, metric.code, matcher.group(1)).contains(matcher.group(1));
                }
            }
        }

        // 分析数据集：数据源、指标编码、维度模型编码引用
        for (var dataset : content.getDatasets()) {
            if (dataset.dataSourceOldId != null) {
                assertThat(dataSourceIds).as("%s: dataset %s dataSourceOldId", file, dataset.code).contains(dataset.dataSourceOldId);
            }
            JsonNode definition = dataset.definition;
            if (definition == null) {
                continue;
            }
            JsonNode definitionDataSourceId = definition.get("dataSourceId");
            if (definitionDataSourceId != null && definitionDataSourceId.canConvertToLong()) {
                assertThat(dataSourceIds).as("%s: dataset %s definition.dataSourceId", file, dataset.code).contains(definitionDataSourceId.asText());
            }
            JsonNode metricQuery = definition.get("metricQuery");
            if (metricQuery != null && metricQuery.get("metricCodes") != null) {
                metricQuery
                    .get("metricCodes")
                    .forEach(code -> assertThat(metricCodeNames).as("%s: dataset %s metricCode", file, dataset.code).contains(code.asText()));
            }
        }

        // 分析看板：数据源与通过 refId 引用的数据集
        for (var dashboard : content.getDashboards()) {
            if (dashboard.dataSourceOldId != null) {
                assertThat(dataSourceIds).as("%s: dashboard %s dataSourceOldId", file, dashboard.code).contains(dashboard.dataSourceOldId);
            }
            JsonNode datasets = dashboard.spec == null ? null : dashboard.spec.get("datasets");
            assertThat(datasets).as("%s: dashboard %s datasets", file, dashboard.code).isNotNull();
            datasets.forEach(dataset -> {
                JsonNode refId = dataset.get("refId");
                if (refId != null && !refId.isNull()) {
                    assertThat(datasetIds).as("%s: dashboard %s refId", file, dashboard.code).contains(refId.asText());
                }
            });
            JsonNode widgets = dashboard.spec.get("widgets");
            assertThat(widgets).as("%s: dashboard %s widgets", file, dashboard.code).isNotNull();
            assertThat(widgets.size()).as("%s: dashboard %s widget count", file, dashboard.code).isPositive();
        }

        // ETL 节点里的数据源与模型引用
        for (AppPackageEtlTask task : content.getEtlTasks()) {
            for (AppPackageEtlTask.AppPackageEtlNode node : task.nodes) {
                JsonNode config = node.config;
                if (config == null) {
                    continue;
                }
                JsonNode sourceId = config.get("sourceId");
                if (sourceId != null && sourceId.canConvertToLong()) {
                    assertThat(dataSourceIds).as("%s: etl node %s sourceId", file, node.label).contains(sourceId.asText());
                }
                JsonNode modelId = config.get("modelId");
                if (modelId != null && modelId.canConvertToLong()) {
                    assertThat(modelIds).as("%s: etl node %s modelId", file, node.label).contains(modelId.asText());
                }
            }
        }

        // SQL 任务的数据源引用
        content
            .getSqlJobs()
            .forEach(job -> {
                JsonNode dataSourceId = job.jobContext == null ? null : job.jobContext.get("dataSourceId");
                if (dataSourceId != null && dataSourceId.canConvertToLong()) {
                    assertThat(dataSourceIds).as("%s: sql job %s dataSourceId", file, job.jobName).contains(dataSourceId.asText());
                }
                assertThat(job.type).as("%s: sql job %s type", file, job.jobName).isEqualTo(TaskConstants.TASK_TYPE_SQL);
            });

        // 编排任务引用的子任务必须存在于包内（SQL 任务按 Job ID、ETL 任务按其调度 Job ID）
        Set<String> jobIds = new LinkedHashSet<>();
        content.getSqlJobs().forEach(job -> jobIds.add(job.oldId));
        content.getOtherJobs().forEach(job -> jobIds.add(job.oldId));
        content.getEtlTasks().forEach(task -> jobIds.add(task.jobOldId));
        content.getDagJobs().forEach(job -> jobIds.add(job.oldId));
        content
            .getDagJobs()
            .forEach(job -> {
                assertThat(job.type).as("%s: dag job %s type", file, job.jobName).isEqualTo(TaskConstants.TASK_TYPE_DAG);
                JsonNode jobs = job.jobContext == null ? null : job.jobContext.get("jobs");
                assertThat(jobs).as("%s: dag job %s jobs", file, job.jobName).isNotNull();
                jobs.forEach(item -> {
                    JsonNode id = item.get("id");
                    assertThat(jobIds).as("%s: dag job %s child %s", file, job.jobName, id).contains(id.asText());
                });
                JsonNode depends = job.jobContext.get("jobDepends");
                if (depends != null) {
                    depends.forEach(depend -> {
                        assertThat(jobIds).as("%s: dag job %s parent", file, job.jobName).contains(depend.get("parentJobCode").asText());
                        assertThat(jobIds).as("%s: dag job %s child", file, job.jobName).contains(depend.get("childJobCode").asText());
                    });
                }
            });

        assertThat(metricCodes).as("%s: metric oldIds", file).doesNotContainNull();
    }

    private Set<String> ids(Set<String> source) {
        assertThat(source).doesNotContainNull();
        return source;
    }
}
