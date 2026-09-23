package com.data.datafusion.service.dto;

import java.io.Serializable;

/**
 * 衍生指标引用项的展示信息。
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class MetricRefDTO implements Serializable {

    private Long id;
    private String code;
    private String name;
    private String metricType;
    private String status;

    public MetricRefDTO() {}

    public MetricRefDTO(Long id, String code, String name, String metricType, String status) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.metricType = metricType;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
