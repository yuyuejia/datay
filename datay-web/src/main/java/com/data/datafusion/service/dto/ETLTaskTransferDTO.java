package com.data.datafusion.service.dto;

import com.data.datafusion.service.apppackage.model.AppPackageEtlTask;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * ETL 任务导出 / 导入的传输对象（单任务 JSON 文件格式）。
 *
 * <p>与资产包内的 ETL 任务条目保持一致：节点 {@code config} 中的 {@code sourceId}（数据源）
 * 与 {@code modelId}（数据模型）在导入时按名称 / 编码在本租户内复用并重写。
 * 由于单任务导出不携带完整的数据源 / 模型定义，这里额外用 {@link #references} 记录
 * 每个被引用对象的名称、编码，供导入方按名称匹配复用。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ETLTaskTransferDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 格式标识，用于导入时校验文件类型。 */
    public String format = "datay-etl-task";

    /** 格式版本。 */
    public String version = "1.0";

    /** 任务定义（含节点与连线）。 */
    public AppPackageEtlTask task;

    /** 节点中引用的数据源 / 模型清单。 */
    public References references = new References();

    /** 被引用对象的清单。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class References implements Serializable {

        private static final long serialVersionUID = 1L;

        public List<DataSourceRef> dataSources = new ArrayList<>();

        public List<ModelRef> models = new ArrayList<>();
    }

    /** 被引用的数据源。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataSourceRef implements Serializable {

        private static final long serialVersionUID = 1L;

        public String oldId;

        public String name;

        public String type;

        public String url;
    }

    /** 被引用的数据模型。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ModelRef implements Serializable {

        private static final long serialVersionUID = 1L;

        public String oldId;

        public String code;

        public String name;
    }
}
