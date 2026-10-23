package com.data.datafusion.service.dataset;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据集行级数据权限配置，序列化后存放于 {@code dp_role_dataset_scope.filter_config}。
 */
public class DatasetScopeFilterConfig {

    private List<DatasetScopeCondition> conditions = new ArrayList<>();

    public List<DatasetScopeCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<DatasetScopeCondition> conditions) {
        this.conditions = conditions;
    }

    public boolean isEmpty() {
        return conditions == null || conditions.isEmpty();
    }
}
