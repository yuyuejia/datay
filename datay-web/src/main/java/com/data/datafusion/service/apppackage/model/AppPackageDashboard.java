package com.data.datafusion.service.apppackage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 资产包中的分析看板条目。
 *
 * <p>{@code spec} 为看板定义 JSON（datasets / widgets / filters / layout）。
 * 其中 {@code datasets[].refId} 引用包内分析数据集的逻辑 ID，其余 SQL / 指标定义
 * 可通过 {@code dataSourceId} 引用包内数据源；导入时会统一替换为新 ID。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppPackageDashboard {

    /** 包内逻辑 ID。 */
    public String oldId;

    /** 看板默认数据源的包内逻辑 ID。 */
    public String dataSourceOldId;

    public String name;

    public String code;

    public String description;

    public String status;

    /** 看板定义 JSON。 */
    public JsonNode spec;
}
