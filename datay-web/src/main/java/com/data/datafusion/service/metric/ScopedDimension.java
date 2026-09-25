package com.data.datafusion.service.metric;

import java.util.ArrayList;
import java.util.List;

/**
 * 已解析的角色数据范围：某个维度下允许的成员条件集合。
 * <p>
 * 同一维度内的多条条件在生成 SQL 时以 OR 组合，不同维度之间以 AND 组合。
 */
public class ScopedDimension {

    private Long dimensionModelId;

    private List<MetricFilterCondition> conditions = new ArrayList<>();

    public Long getDimensionModelId() {
        return dimensionModelId;
    }

    public void setDimensionModelId(Long dimensionModelId) {
        this.dimensionModelId = dimensionModelId;
    }

    public List<MetricFilterCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<MetricFilterCondition> conditions) {
        this.conditions = conditions;
    }
}
