package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * A DTO for the {@link com.data.datafusion.domain.DataApiCallLog} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DataApiCallLogDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String id;

    private String apiId;

    private String apiCode;

    private String apiName;

    private String sourceType;

    private String requestSource;

    private String requestMethod;

    private String requestPath;

    private String requestParams;

    private Long callerUserId;

    private String callerLogin;

    private String clientIp;

    private String userAgent;

    private String status;

    private Integer httpStatus;

    private String errorMessage;

    private Long durationMs;

    private Long resultRows;

    private LocalDate statDate;

    private Integer statHour;

    private ZonedDateTime createTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getApiId() {
        return apiId;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    public String getApiCode() {
        return apiCode;
    }

    public void setApiCode(String apiCode) {
        this.apiCode = apiCode;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getRequestSource() {
        return requestSource;
    }

    public void setRequestSource(String requestSource) {
        this.requestSource = requestSource;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public String getRequestParams() {
        return requestParams;
    }

    public void setRequestParams(String requestParams) {
        this.requestParams = requestParams;
    }

    public Long getCallerUserId() {
        return callerUserId;
    }

    public void setCallerUserId(Long callerUserId) {
        this.callerUserId = callerUserId;
    }

    public String getCallerLogin() {
        return callerLogin;
    }

    public void setCallerLogin(String callerLogin) {
        this.callerLogin = callerLogin;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Long getResultRows() {
        return resultRows;
    }

    public void setResultRows(Long resultRows) {
        this.resultRows = resultRows;
    }

    public LocalDate getStatDate() {
        return statDate;
    }

    public void setStatDate(LocalDate statDate) {
        this.statDate = statDate;
    }

    public Integer getStatHour() {
        return statHour;
    }

    public void setStatHour(Integer statHour) {
        this.statHour = statHour;
    }

    public ZonedDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataApiCallLogDTO)) {
            return false;
        }
        return getId() != null && getId().equals(((DataApiCallLogDTO) o).getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return "DataApiCallLogDTO{" +
            "id=" + getId() +
            ", apiId=" + getApiId() +
            ", apiCode='" + getApiCode() + "'" +
            ", requestSource='" + getRequestSource() + "'" +
            ", status='" + getStatus() + "'" +
            ", httpStatus=" + getHttpStatus() +
            ", durationMs=" + getDurationMs() +
            ", createTime='" + getCreateTime() + "'" +
            "}";
    }
}
