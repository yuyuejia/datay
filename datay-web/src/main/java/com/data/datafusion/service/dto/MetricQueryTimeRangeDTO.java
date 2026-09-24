package com.data.datafusion.service.dto;

import java.io.Serializable;

/**
 * 指标查询的时间统计范围。
 * <p>仅需提供起止值，事实表使用的过滤字段由事实表模型配置的「时间周期字段」决定。
 */
public class MetricQueryTimeRangeDTO implements Serializable {

    private String start;

    private String end;

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
