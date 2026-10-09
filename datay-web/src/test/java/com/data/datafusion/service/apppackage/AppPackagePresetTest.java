package com.data.datafusion.service.apppackage;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.job.TaskConstants;
import com.data.datafusion.service.apppackage.model.AppPackageContent;
import com.data.datafusion.service.apppackage.model.AppPackageEtlTask;
import com.data.datafusion.service.apppackage.model.AppPackageMetric;
import com.data.datafusion.service.apppackage.model.AppPackageModel;
import com.data.job.ComponentDescriptor;
import com.data.job.ComponentFactory;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
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
        }
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
