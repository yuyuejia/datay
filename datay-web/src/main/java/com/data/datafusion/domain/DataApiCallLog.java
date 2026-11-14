package com.data.datafusion.domain;

import com.data.datafusion.util.entity.UuidV7Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import org.hibernate.annotations.Filter;

/**
 * 数据服务调用日志：记录一次对外或 MCP 调用的来源、结果与耗时，供审计和统计使用。
 */
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "dp_data_api_call_log")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class DataApiCallLog implements Serializable, TenantAware {

    public static final String STATUS_SUCCESS = "SUCCESS";

    public static final String STATUS_FAILED = "FAILED";

    public static final String REQUEST_SOURCE_OPEN_API = "OPEN_API";

    public static final String REQUEST_SOURCE_MCP = "MCP";

    public static final String REQUEST_SOURCE_INTERNAL = "INTERNAL";

    private static final long serialVersionUID = 1L;

    @Id
    @UuidV7Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "api_id", length = 36)
    private String apiId;

    @Column(name = "api_code", length = 255)
    private String apiCode;

    @Column(name = "api_name", length = 255)
    private String apiName;

    @Column(name = "source_type", length = 32)
    private String sourceType;

    @Column(name = "request_source", length = 32)
    private String requestSource;

    @Column(name = "request_method", length = 16)
    private String requestMethod;

    @Column(name = "request_path", length = 500)
    private String requestPath;

    /** 脱敏后的调用参数，避免日志泄露令牌、密码等敏感信息。 */
    @Lob
    @Column(name = "request_params")
    private String requestParams;

    @Column(name = "caller_user_id")
    private Long callerUserId;

    @Column(name = "caller_login", length = 100)
    private String callerLogin;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "http_status")
    private Integer httpStatus;

    /** 失败原因，截断后保存，避免单条日志过大。 */
    @Lob
    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "result_rows")
    private Long resultRows;

    @Column(name = "stat_date")
    private LocalDate statDate;

    @Column(name = "stat_hour")
    private Integer statHour;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    public String getId() {
        return id;
    }

    public DataApiCallLog id(String id) {
        this.setId(id);
        return this;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public DataApiCallLog tenantId(String tenantId) {
        this.setTenantId(tenantId);
        return this;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getApiId() {
        return apiId;
    }

    public DataApiCallLog apiId(String apiId) {
        this.setApiId(apiId);
        return this;
    }

    public void setApiId(String apiId) {
        this.apiId = apiId;
    }

    public String getApiCode() {
        return apiCode;
    }

    public DataApiCallLog apiCode(String apiCode) {
        this.setApiCode(apiCode);
        return this;
    }

    public void setApiCode(String apiCode) {
        this.apiCode = apiCode;
    }

    public String getApiName() {
        return apiName;
    }

    public DataApiCallLog apiName(String apiName) {
        this.setApiName(apiName);
        return this;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public DataApiCallLog sourceType(String sourceType) {
        this.setSourceType(sourceType);
        return this;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getRequestSource() {
        return requestSource;
    }

    public DataApiCallLog requestSource(String requestSource) {
        this.setRequestSource(requestSource);
        return this;
    }

    public void setRequestSource(String requestSource) {
        this.requestSource = requestSource;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public DataApiCallLog requestMethod(String requestMethod) {
        this.setRequestMethod(requestMethod);
        return this;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public DataApiCallLog requestPath(String requestPath) {
        this.setRequestPath(requestPath);
        return this;
    }

    public void setRequestPath(String requestPath) {
        this.requestPath = requestPath;
    }

    public String getRequestParams() {
        return requestParams;
    }

    public DataApiCallLog requestParams(String requestParams) {
        this.setRequestParams(requestParams);
        return this;
    }

    public void setRequestParams(String requestParams) {
        this.requestParams = requestParams;
    }

    public Long getCallerUserId() {
        return callerUserId;
    }

    public DataApiCallLog callerUserId(Long callerUserId) {
        this.setCallerUserId(callerUserId);
        return this;
    }

    public void setCallerUserId(Long callerUserId) {
        this.callerUserId = callerUserId;
    }

    public String getCallerLogin() {
        return callerLogin;
    }

    public DataApiCallLog callerLogin(String callerLogin) {
        this.setCallerLogin(callerLogin);
        return this;
    }

    public void setCallerLogin(String callerLogin) {
        this.callerLogin = callerLogin;
    }

    public String getClientIp() {
        return clientIp;
    }

    public DataApiCallLog clientIp(String clientIp) {
        this.setClientIp(clientIp);
        return this;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public DataApiCallLog userAgent(String userAgent) {
        this.setUserAgent(userAgent);
        return this;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getStatus() {
        return status;
    }

    public DataApiCallLog status(String status) {
        this.setStatus(status);
        return this;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public DataApiCallLog httpStatus(Integer httpStatus) {
        this.setHttpStatus(httpStatus);
        return this;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public DataApiCallLog errorMessage(String errorMessage) {
        this.setErrorMessage(errorMessage);
        return this;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public DataApiCallLog durationMs(Long durationMs) {
        this.setDurationMs(durationMs);
        return this;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public Long getResultRows() {
        return resultRows;
    }

    public DataApiCallLog resultRows(Long resultRows) {
        this.setResultRows(resultRows);
        return this;
    }

    public void setResultRows(Long resultRows) {
        this.resultRows = resultRows;
    }

    public LocalDate getStatDate() {
        return statDate;
    }

    public DataApiCallLog statDate(LocalDate statDate) {
        this.setStatDate(statDate);
        return this;
    }

    public void setStatDate(LocalDate statDate) {
        this.statDate = statDate;
    }

    public Integer getStatHour() {
        return statHour;
    }

    public DataApiCallLog statHour(Integer statHour) {
        this.setStatHour(statHour);
        return this;
    }

    public void setStatHour(Integer statHour) {
        this.statHour = statHour;
    }

    public ZonedDateTime getCreateTime() {
        return createTime;
    }

    public DataApiCallLog createTime(ZonedDateTime createTime) {
        this.setCreateTime(createTime);
        return this;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataApiCallLog)) {
            return false;
        }
        return getId() != null && getId().equals(((DataApiCallLog) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "DataApiCallLog{" +
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
