package com.data.datafusion.service.dto;

import java.io.Serializable;

/**
 * 指标查询的时间统计范围。
 */
public class MetricQueryTimeRangeDTO implements Serializable {

    /** 日期字段所在事实表字段（关联维度时作为连接键）。 */
    private String factFieldName;

    /** 日期字段所在维度模型（可选）。 */
    private Long dimensionModelId;

    /** 维度日期字段（可选）。 */
    private String dimensionFieldName;

    private String start;

    private String end;

    public String getFactFieldName() {
        return factFieldName;
    }

    public void setFactFieldName(String factFieldName) {
        this.factFieldName = factFieldName;
    }

    public Long getDimensionModelId() {
        return dimensionModelId;
    }

    public void setDimensionModelId(Long dimensionModelId) {
        this.dimensionModelId = dimensionModelId;
    }

    public String getDimensionFieldName() {
        return dimensionFieldName;
    }

    public void setDimensionFieldName(String dimensionFieldName) {
        this.dimensionFieldName = dimensionFieldName;
    }

    public String getStart() {
        return start;
    }

    public void setStart(String start) {
        this.start = start;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }
}
