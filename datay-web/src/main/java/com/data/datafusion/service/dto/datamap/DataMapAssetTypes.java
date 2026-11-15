package com.data.datafusion.service.dto.datamap;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据地图资产类型与中文名称。
 */
public final class DataMapAssetTypes {

    public static final String DATASOURCE = "DATASOURCE";
    public static final String SCHEMA = "SCHEMA";
    public static final String TABLE = "TABLE";
    public static final String COLUMN = "COLUMN";
    public static final String ETL_TASK = "ETL_TASK";
    public static final String DATA_SYNC = "DATA_SYNC";
    public static final String DATA_MODEL = "DATA_MODEL";
    public static final String MODEL_FIELD = "MODEL_FIELD";
    public static final String METRIC = "METRIC";
    public static final String DATASET = "DATASET";
    public static final String DASHBOARD = "DASHBOARD";
    public static final String DATA_API = "DATA_API";
    public static final String QUALITY_TASK = "QUALITY_TASK";

    private static final Map<String, String> LABELS = new LinkedHashMap<>();

    static {
        LABELS.put(DATASOURCE, "数据源");
        LABELS.put(SCHEMA, "Schema");
        LABELS.put(TABLE, "物理表");
        LABELS.put(COLUMN, "字段");
        LABELS.put(ETL_TASK, "ETL 任务");
        LABELS.put(DATA_SYNC, "数据同步");
        LABELS.put(DATA_MODEL, "数据模型");
        LABELS.put(MODEL_FIELD, "模型字段");
        LABELS.put(METRIC, "指标");
        LABELS.put(DATASET, "数据集");
        LABELS.put(DASHBOARD, "看板");
        LABELS.put(DATA_API, "API 服务");
        LABELS.put(QUALITY_TASK, "质量任务");
    }

    private DataMapAssetTypes() {}

    public static Map<String, String> labels() {
        return new LinkedHashMap<>(LABELS);
    }

    public static String label(String type) {
        return LABELS.getOrDefault(type, type);
    }

    /** 字段类节点，前端“显示字段”开关关闭时隐藏。 */
    public static boolean isFieldType(String type) {
        return COLUMN.equals(type) || MODEL_FIELD.equals(type);
    }
}
