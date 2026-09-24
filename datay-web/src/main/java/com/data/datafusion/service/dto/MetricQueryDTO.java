package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 指标数据查询请求。
 */
public class MetricQueryDTO implements Serializable {

    /** 查询的指标编码（原子或衍生原子指标），支持多个。 */
    private List<String> metricCodes = new ArrayList<>();

    /** 分组维度。 */
    private List<MetricQueryFieldDTO> dimensions = new ArrayList<>();

    /** 用户指定的业务限定（与指标 filter_config 结构一致）。 */
    private String filterConfig;

    /** 时间统计范围。 */
    private MetricQueryTimeRangeDTO timeRange;

    public List<String> getMetricCodes() {
        return metricCodes;
    }

    public void setMetricCodes(List<String> metricCodes) {
        this.metricCodes = metricCodes;
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
