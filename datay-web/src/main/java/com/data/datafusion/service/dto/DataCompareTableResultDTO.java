package com.data.datafusion.service.dto;

import java.io.Serializable;

/**
 * 单个表对的比对结果。
 */
public class DataCompareTableResultDTO implements Serializable {

    private Integer index;

    private String leftTable;

    private String rightTable;

    private Long leftRowCount;

    private Long rightRowCount;

    private Long sameRowCount;

    private Long diffRowCount;

    private Long onlyLeftCount;

    private Long onlyRightCount;

    private Long valueDiffCount;

    private String resultPath;

    private String summaryPath;

    private String resultFormat;

    private String downloadUrl;

    private String summaryUrl;

    public Integer getIndex() {
        return index;
    }

    public void setIndex(Integer index) {
        this.index = index;
    }

    public String getLeftTable() {
        return leftTable;
    }

    public void setLeftTable(String leftTable) {
        this.leftTable = leftTable;
    }

    public String getRightTable() {
        return rightTable;
    }

    public void setRightTable(String rightTable) {
        this.rightTable = rightTable;
    }

    public Long getLeftRowCount() {
        return leftRowCount;
    }

    public void setLeftRowCount(Long leftRowCount) {
        this.leftRowCount = leftRowCount;
    }

    public Long getRightRowCount() {
        return rightRowCount;
    }

    public void setRightRowCount(Long rightRowCount) {
        this.rightRowCount = rightRowCount;
    }

    public Long getSameRowCount() {
        return sameRowCount;
    }

    public void setSameRowCount(Long sameRowCount) {
        this.sameRowCount = sameRowCount;
    }

    public Long getDiffRowCount() {
        return diffRowCount;
    }

    public void setDiffRowCount(Long diffRowCount) {
        this.diffRowCount = diffRowCount;
    }

    public Long getOnlyLeftCount() {
        return onlyLeftCount;
    }

    public void setOnlyLeftCount(Long onlyLeftCount) {
        this.onlyLeftCount = onlyLeftCount;
    }

    public Long getOnlyRightCount() {
        return onlyRightCount;
    }

    public void setOnlyRightCount(Long onlyRightCount) {
        this.onlyRightCount = onlyRightCount;
    }

    public Long getValueDiffCount() {
        return valueDiffCount;
    }

    public void setValueDiffCount(Long valueDiffCount) {
        this.valueDiffCount = valueDiffCount;
    }

    public String getResultPath() {
        return resultPath;
    }

    public void setResultPath(String resultPath) {
        this.resultPath = resultPath;
    }

    public String getSummaryPath() {
        return summaryPath;
    }

    public void setSummaryPath(String summaryPath) {
        this.summaryPath = summaryPath;
    }

    public String getResultFormat() {
        return resultFormat;
    }

    public void setResultFormat(String resultFormat) {
        this.resultFormat = resultFormat;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public String getSummaryUrl() {
        return summaryUrl;
    }

    public void setSummaryUrl(String summaryUrl) {
        this.summaryUrl = summaryUrl;
    }
}
