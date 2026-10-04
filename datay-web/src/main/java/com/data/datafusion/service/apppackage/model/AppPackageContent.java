package com.data.datafusion.service.apppackage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据应用资产包内容模型（资产包的 JSON 清单）。
 *
 * <p>包内所有对象都带有一个「包内逻辑 ID」{@code oldId}，其它对象通过
 * {@code xxxOldId} 引用它。初始化到目标租户时，逻辑 ID 会被替换为新生成的真实 ID，
 * 因此同一份资产包可以在不同租户中反复初始化而不会串号。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppPackageContent {

    /** 资产包格式标识，用于导入时做格式校验。 */
    public static final String FORMAT = "datay-app-package";

    /** 当前格式版本。 */
    public static final String FORMAT_VERSION = "1.0";

    private String packageFormat = FORMAT;

    private String formatVersion = FORMAT_VERSION;

    /** 资产包元信息。 */
    private Meta meta = new Meta();

    /** 数据源。 */
    private List<AppPackageDataSource> dataSources = new ArrayList<>();

    /** 数据模型目录。 */
    private List<AppPackageDirectory> modelDirectories = new ArrayList<>();

    /** 数据模型（含字段定义）。 */
    private List<AppPackageModel> models = new ArrayList<>();

    /** 指标目录。 */
    private List<AppPackageDirectory> metricDirectories = new ArrayList<>();

    /** 指标。 */
    private List<AppPackageMetric> metrics = new ArrayList<>();

    /** ETL 任务。 */
    private List<AppPackageEtlTask> etlTasks = new ArrayList<>();

    /** SQL 任务。 */
    private List<AppPackageJob> sqlJobs = new ArrayList<>();

    /** 编排任务（DAG）。 */
    private List<AppPackageJob> dagJobs = new ArrayList<>();

    /** 其它被编排任务引用的任务（Shell / Spark / Flink 等），保证 DAG 导入后节点完整。 */
    private List<AppPackageJob> otherJobs = new ArrayList<>();

    /** 任务依赖关系。 */
    private List<AppPackageJobDepend> jobDepends = new ArrayList<>();

    /** 资产构成摘要，导出时自动生成。 */
    private Map<String, Integer> summary = new LinkedHashMap<>();

    public String getPackageFormat() {
        return packageFormat;
    }

    public void setPackageFormat(String packageFormat) {
        this.packageFormat = packageFormat;
    }

    public String getFormatVersion() {
        return formatVersion;
    }

    public void setFormatVersion(String formatVersion) {
        this.formatVersion = formatVersion;
    }

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }

    public List<AppPackageDataSource> getDataSources() {
        return dataSources;
    }

    public void setDataSources(List<AppPackageDataSource> dataSources) {
        this.dataSources = dataSources == null ? new ArrayList<>() : dataSources;
    }

    public List<AppPackageDirectory> getModelDirectories() {
        return modelDirectories;
    }

    public void setModelDirectories(List<AppPackageDirectory> modelDirectories) {
        this.modelDirectories = modelDirectories == null ? new ArrayList<>() : modelDirectories;
    }

    public List<AppPackageModel> getModels() {
        return models;
    }

    public void setModels(List<AppPackageModel> models) {
        this.models = models == null ? new ArrayList<>() : models;
    }

    public List<AppPackageDirectory> getMetricDirectories() {
        return metricDirectories;
    }

    public void setMetricDirectories(List<AppPackageDirectory> metricDirectories) {
        this.metricDirectories = metricDirectories == null ? new ArrayList<>() : metricDirectories;
    }

    public List<AppPackageMetric> getMetrics() {
        return metrics;
    }

    public void setMetrics(List<AppPackageMetric> metrics) {
        this.metrics = metrics == null ? new ArrayList<>() : metrics;
    }

    public List<AppPackageEtlTask> getEtlTasks() {
        return etlTasks;
    }

    public void setEtlTasks(List<AppPackageEtlTask> etlTasks) {
        this.etlTasks = etlTasks == null ? new ArrayList<>() : etlTasks;
    }

    public List<AppPackageJob> getSqlJobs() {
        return sqlJobs;
    }

    public void setSqlJobs(List<AppPackageJob> sqlJobs) {
        this.sqlJobs = sqlJobs == null ? new ArrayList<>() : sqlJobs;
    }

    public List<AppPackageJob> getDagJobs() {
        return dagJobs;
    }

    public void setDagJobs(List<AppPackageJob> dagJobs) {
        this.dagJobs = dagJobs == null ? new ArrayList<>() : dagJobs;
    }

    public List<AppPackageJob> getOtherJobs() {
        return otherJobs;
    }

    public void setOtherJobs(List<AppPackageJob> otherJobs) {
        this.otherJobs = otherJobs == null ? new ArrayList<>() : otherJobs;
    }

    public List<AppPackageJobDepend> getJobDepends() {
        return jobDepends;
    }

    public void setJobDepends(List<AppPackageJobDepend> jobDepends) {
        this.jobDepends = jobDepends == null ? new ArrayList<>() : jobDepends;
    }

    public Map<String, Integer> getSummary() {
        return summary;
    }

    public void setSummary(Map<String, Integer> summary) {
        this.summary = summary == null ? new LinkedHashMap<>() : summary;
    }

    /** 重新计算资产构成摘要。 */
    public Map<String, Integer> rebuildSummary() {
        int fieldCount = models.stream().mapToInt(model -> model.fields == null ? 0 : model.fields.size()).sum();
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("dataSources", dataSources.size());
        result.put("modelDirectories", modelDirectories.size());
        result.put("models", models.size());
        result.put("modelFields", fieldCount);
        result.put("metricDirectories", metricDirectories.size());
        result.put("metrics", metrics.size());
        result.put("etlTasks", etlTasks.size());
        result.put("sqlJobs", sqlJobs.size());
        result.put("dagJobs", dagJobs.size());
        result.put("otherJobs", otherJobs.size());
        result.put("jobDepends", jobDepends.size());
        result.put("total", result.values().stream().mapToInt(Integer::intValue).sum());
        this.summary = result;
        return result;
    }

    /** 资产包元信息。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {

        private String name;

        private String code;

        private String description;

        private String category;

        private String version;

        /** 导出时间，ISO-8601 字符串。 */
        private String exportTime;

        /** 来源租户 ID。 */
        private String sourceTenantId;

        /** 来源租户编码。 */
        private String sourceTenantCode;

        /** 导出人。 */
        private String exportedBy;

        /** 来源标识：TENANT（租户导出）/ SYSTEM（系统预制）。 */
        private String source;

        private String remark;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getExportTime() {
            return exportTime;
        }

        public void setExportTime(String exportTime) {
            this.exportTime = exportTime;
        }

        public String getSourceTenantId() {
            return sourceTenantId;
        }

        public void setSourceTenantId(String sourceTenantId) {
            this.sourceTenantId = sourceTenantId;
        }

        public String getSourceTenantCode() {
            return sourceTenantCode;
        }

        public void setSourceTenantCode(String sourceTenantCode) {
            this.sourceTenantCode = sourceTenantCode;
        }

        public String getExportedBy() {
            return exportedBy;
        }

        public void setExportedBy(String exportedBy) {
            this.exportedBy = exportedBy;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getRemark() {
            return remark;
        }

        public void setRemark(String remark) {
            this.remark = remark;
        }
    }

}
