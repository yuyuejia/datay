package com.data.datafusion.service.metric;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * 指标业务限定配置，序列化后存放于 {@code dp_metric.filter_config}。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetricFilterConfig {

    private List<MetricFilterCondition> conditions = new ArrayList<>();

    public List<MetricFilterCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<MetricFilterCondition> conditions) {
        this.conditions = conditions;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return conditions == null || conditions.isEmpty();
    }
}
