package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 指标数据查询请求。
 */
public class MetricQueryDTO implements Serializable {

    /** 查询的指标（原子或衍生原子指标），支持多个。 */
    private List<Long> metricIds = new ArrayList<>();

    /** 兼容单个指标查询。 */
    private Long metricId;

    /** 分组维度。 */
    private List<MetricQueryFieldDTO> dimensions = new ArrayList<>();

    /** 用户指定的业务限定（与指标 filter_config 结构一致）。 */
    private String filterConfig;

    /** 时间统计范围。 */
    private MetricQueryTimeRangeDTO timeRange;

    public List<Long> getMetricIds() {
        return metricIds;
    }

    public void setMetricIds(List<Long> metricIds) {
        this.metricIds = metricIds;
    }

    public Long getMetricId() {
        return metricId;
    }

    public void setMetricId(Long metricId) {
        this.metricId = metricId;
    }

    public List<MetricQueryFieldDTO> getDimensions() {
        return dimensions;
    }

    public void setDimensions(List<MetricQueryFieldDTO> dimensions) {
        this.dimensions = dimensions;
    }

    public String getFilterConfig() {
        return filterConfig;
    }

    public void setFilterConfig(String filterConfig) {
        this.filterConfig = filterConfig;
    }

    public MetricQueryTimeRangeDTO getTimeRange() {
        return timeRange;
    }

    public void setTimeRange(MetricQueryTimeRangeDTO timeRange) {
        this.timeRange = timeRange;
    }
}
