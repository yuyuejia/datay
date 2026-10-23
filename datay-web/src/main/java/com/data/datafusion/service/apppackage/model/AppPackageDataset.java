package com.data.datafusion.service.apppackage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 资产包中的分析数据集条目。
 *
 * <p>{@code definition} 保存与类型相关的定义 JSON：
 * {@code METRIC} 为 {@code {"metricQuery": {...}}}，{@code SQL} 为
 * {@code {"sql": "...", "params": []}}；其中引用的数据源与指标编码在导入时替换为
 * 目标租户的新 ID / 新编码。{@code fields} 为最近一次预览得到的字段缓存，可缺省。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppPackageDataset {

    /** 包内逻辑 ID。 */
    public String oldId;

    /** 绑定的数据源包内逻辑 ID。 */
    public String dataSourceOldId;

    public String name;

    public String code;

    public String description;

    /** 数据集类型：METRIC 指标数据集 / SQL 物理表 SQL 数据集。 */
    public String type;

    public String status;

    /** 数据集定义，结构见类注释。 */
    public JsonNode definition;

    /** 最近一次预览得到的字段缓存（columns / columnLabels）。 */
    public JsonNode fields;
}
