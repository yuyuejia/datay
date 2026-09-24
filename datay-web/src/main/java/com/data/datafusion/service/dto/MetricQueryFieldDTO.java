package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 指标查询的维度选择项。
 * <p>选择维度模型后，可指定一个或多个维度显示字段用于展示；分组使用维度主键。
 */
public class MetricQueryFieldDTO implements Serializable {

    private String factFieldName;
    private String dimensionModelCode;
    private String dimensionFieldName;

    /** 维度显示字段（可多个）。 */
    private List<String> dimensionFieldNames = new ArrayList<>();

    /** 层级维度的层级序号（1..N），指定后按该层级汇总。 */
    private Integer levelIndex;

    public String getFactFieldName() {
        return factFieldName;
    }

    public void setFactFieldName(String factFieldName) {
        this.factFieldName = factFieldName;
    }

    public String getDimensionModelCode() {
        return dimensionModelCode;
    }

    public void setDimensionModelCode(String dimensionModelCode) {
        this.dimensionModelCode = dimensionModelCode;
    }

    public String getDimensionFieldName() {
        return dimensionFieldName;
    }

    public void setDimensionFieldName(String dimensionFieldName) {
        this.dimensionFieldName = dimensionFieldName;
    }

    public List<String> getDimensionFieldNames() {
        return dimensionFieldNames;
    }

    public void setDimensionFieldNames(List<String> dimensionFieldNames) {
        this.dimensionFieldNames = dimensionFieldNames;
    }

    public Integer getLevelIndex() {
        return levelIndex;
    }

    public void setLevelIndex(Integer levelIndex) {
        this.levelIndex = levelIndex;
    }
}
