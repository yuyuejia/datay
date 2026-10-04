package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据应用初始化结果：创建/复用统计、数据源映射与 ID 对照表。
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AppPackageInitResultDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    public Long packageId;

    public String packageName;

    public String packageCode;

    /** SUCCESS / FAILED。 */
    public String status;

    public String message;

    /** 各类资产的创建/复用数量统计。 */
    public Map<String, Integer> counts = new LinkedHashMap<>();

    /** 数据源处理明细（复用还是新建、映射到哪个 ID）。 */
    public List<DataSourceMapping> dataSources = new ArrayList<>();

    /** 各类资产的「包内逻辑 ID → 新 ID」对照表，键为资产类型。 */
    public Map<String, Map<String, Long>> idMapping = new LinkedHashMap<>();

    /** 导入过程中的告警，例如被跳过的资产、缺失的引用。 */
    public List<String> warnings = new ArrayList<>();

    /** 初始化完成后的建议操作，例如「运行某某编排任务」，避免用户不知道先跑哪个任务。 */
    public List<String> nextSteps = new ArrayList<>();

    /** 数据源处理明细。 */
    public static class DataSourceMapping implements Serializable {

        private static final long serialVersionUID = 1L;

        public Long oldId;

        public Long newId;

        public String key;

        public String name;

        /** REUSED 复用已有 / CREATED 新建 / MAPPED 按用户指定绑定。 */
        public String action;
    }
}
