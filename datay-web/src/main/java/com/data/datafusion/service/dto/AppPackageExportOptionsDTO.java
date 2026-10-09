package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 资产包导出向导的可选资产清单。
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AppPackageExportOptionsDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    public List<Option> dataSources = new ArrayList<>();

    public List<Option> models = new ArrayList<>();

    public List<Option> metrics = new ArrayList<>();

    public List<Option> etlTasks = new ArrayList<>();

    public List<Option> sqlJobs = new ArrayList<>();

    public List<Option> dagJobs = new ArrayList<>();

    public List<String> categories = new ArrayList<>();

    /** 通用候选项：id + 名称 + 副标题 + 归属。 */
    public static class Option implements Serializable {

        private static final long serialVersionUID = 1L;

        public String id;

        public String name;

        public String code;

        /** 副标题，例如模型类型、指标口径、任务类型。 */
        public String subtitle;

        /** 归属，例如指标的事实模型名称、任务绑定的数据源名称。 */
        public String owner;
    }
}
