package com.data.datafusion.service.apppackage.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 资产包中的指标条目。
 *
 * <p>衍生指标的 {@code formula} 通过 {@code ${指标编码}} 引用其它指标，
 * 业务限定 {@code filterConfig} 通过维度模型编码 + 字段名引用，二者都不依赖数据库 ID，
 * 因此导入时无需重写编码；只有 {@link #factModelOldId} 需要替换为新建/复用的模型 ID，
 * 而 {@code filterConfig} 里的 {@code dimensionModelId} 需要做 ID 替换。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppPackageMetric {

    /** 包内逻辑 ID。 */
    public Long oldId;

    /** 所属指标目录的包内逻辑 ID。 */
    public Long directoryOldId;

    public String name;

    public String code;

    public String description;

    public String metricType;

    public String status;

    /** 事实表模型的包内逻辑 ID。 */
    public Long factModelOldId;

    /** 业务限定，保持结构化以便重写其中的 {@code dimensionModelId}。 */
    public JsonNode filterConfig;

    public String unit;

    public String dataType;

    public Boolean isAdditive;

    public String formula;
}
