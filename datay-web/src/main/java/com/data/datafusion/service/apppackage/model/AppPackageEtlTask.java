package com.data.datafusion.service.apppackage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;

/**
 * 资产包中的 ETL 任务条目（含设计器画布的节点与连线）。
 *
 * <p>节点 {@code config} 中的 {@code sourceId}（数据源）与 {@code modelId}（数据模型）
 * 在导入时会按映射表重写为新租户的真实 ID。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppPackageEtlTask {

    /** 包内逻辑 ID（任务 ID）。 */
    public String oldId;

    /** 调度 Job 的包内逻辑 ID，供编排任务引用。 */
    public String jobOldId;

    public String taskName;

    public String taskCode;

    public String taskDesc;

    public String dir;

    public String type;

    public String cron;

    public String project;

    public List<AppPackageEtlNode> nodes = new ArrayList<>();

    public List<AppPackageEtlEdge> edges = new ArrayList<>();

    /** ETL 设计器节点。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AppPackageEtlNode {

        public String oldId;

        public String label;

        public String code;

        public String desc;

        public String type;

        /** 节点配置，保持结构化以便重写其中的数据源/模型引用。 */
        public JsonNode config;

        public String xAxis;

        public String yAxis;

        public String status;
    }

    /** ETL 设计器连线。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AppPackageEtlEdge {

        public String oldId;

        public String name;

        public String code;

        public String source;

        public String target;

        public String config;

        public String status;
    }
}
