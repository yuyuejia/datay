package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务中的一条质量规则绑定。
 *
 * <p>保存任务时会把规则名称、类型和参数快照进任务，后续规则库变更不影响已有任务。</p>
 */
public class DataQualityRuleBindingDTO implements Serializable {

    private String ruleId;

    private String ruleCode;

    private String ruleName;

    private String ruleLevel;

    private String ruleType;

    private String column;

    private List<String> columns = new ArrayList<>();

    private Map<String, Object> config = new LinkedHashMap<>();

    private String severity;

    private Long maxInvalidRows;

    private Double maxInvalidRatio;

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getRuleLevel() {
        return ruleLevel;
    }

    public void setRuleLevel(String ruleLevel) {
        this.ruleLevel = ruleLevel;
    }

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public List<String> getColumns() {
        return columns;
    }

    public void setColumns(List<String> columns) {
        this.columns = columns == null ? new ArrayList<>() : columns;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config == null ? new LinkedHashMap<>() : config;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public Long getMaxInvalidRows() {
        return maxInvalidRows;
    }

    public void setMaxInvalidRows(Long maxInvalidRows) {
        this.maxInvalidRows = maxInvalidRows;
    }

    public Double getMaxInvalidRatio() {
        return maxInvalidRatio;
    }

    public void setMaxInvalidRatio(Double maxInvalidRatio) {
        this.maxInvalidRatio = maxInvalidRatio;
    }
}
