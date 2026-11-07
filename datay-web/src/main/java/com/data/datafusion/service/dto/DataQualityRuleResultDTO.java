package com.data.datafusion.service.dto;

import java.io.Serializable;

/**
 * 单条质量规则的执行结果。
 */
public class DataQualityRuleResultDTO implements Serializable {

    private String ruleKey;

    private String ruleName;

    private String ruleLevel;

    private String columnName;

    private String ruleType;

    private String severity;

    private Long checkedRows;

    private Long invalidRows;

    private Double invalidRatio;

    private Long maxInvalidRows;

    private Double maxInvalidRatio;

    private String status;

    private String message;

    private String detailUrl;

    public String getRuleKey() {
        return ruleKey;
    }

    public void setRuleKey(String ruleKey) {
        this.ruleKey = ruleKey;
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

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getRuleType() {
        return ruleType;
    }

    public void setRuleType(String ruleType) {
        this.ruleType = ruleType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public Long getCheckedRows() {
        return checkedRows;
    }

    public void setCheckedRows(Long checkedRows) {
        this.checkedRows = checkedRows;
    }

    public Long getInvalidRows() {
        return invalidRows;
    }

    public void setInvalidRows(Long invalidRows) {
        this.invalidRows = invalidRows;
    }

    public Double getInvalidRatio() {
        return invalidRatio;
    }

    public void setInvalidRatio(Double invalidRatio) {
        this.invalidRatio = invalidRatio;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDetailUrl() {
        return detailUrl;
    }

    public void setDetailUrl(String detailUrl) {
        this.detailUrl = detailUrl;
    }
}
