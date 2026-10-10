package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * ETL 任务导入结果：新建任务的标识、编码 / 名称重命名情况，以及引用数据源 / 模型的匹配明细。
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class ETLTaskImportResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    public String taskId;

    public String taskCode;

    public String taskName;

    /** 任务编码或名称是否因冲突被自动重命名。 */
    public boolean renamed;

    /** 数据源引用匹配明细。 */
    public List<ReferenceMapping> dataSources = new ArrayList<>();

    /** 数据模型引用匹配明细。 */
    public List<ReferenceMapping> models = new ArrayList<>();

    /** 导入过程中的告警，例如未被匹配因而保留原值的引用。 */
    public List<String> warnings = new ArrayList<>();

    /** 单个引用的匹配结果。 */
    public static class ReferenceMapping implements Serializable {

        private static final long serialVersionUID = 1L;

        public String oldId;

        /** 引用对象的展示名（数据源名称或模型编码）。 */
        public String label;

        /** 是否在本租户中匹配到同名 / 同编码对象。 */
        public boolean matched;

        /** 匹配到的新 ID，未匹配时为 null。 */
        public String newId;
    }
}
