package com.data.datafusion.domain;

import com.data.datafusion.util.entity.UuidV7Id;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.ZonedDateTime;
import org.hibernate.annotations.Filter;

/**
 * 告警规则：定义什么事件、什么范围内触发告警，以及通知谁。
 */
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "dp_alert_rule")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AlertRule implements Serializable, TenantAware {

    public static final String SOURCE_JOB = "JOB";

    public static final String EVENT_FAILED = "FAILED";

    public static final String EVENT_TIMEOUT = "TIMEOUT";

    public static final String SEVERITY_P0 = "P0";

    public static final String SEVERITY_P1 = "P1";

    public static final String SEVERITY_P2 = "P2";

    private static final long serialVersionUID = 1L;

    @Id
    @UuidV7Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "name")
    private String name;

    @Column(name = "code")
    private String code;

    @Column(name = "source_type", length = 32)
    private String sourceType = SOURCE_JOB;

    /** 逗号分隔的事件类型，例如 FAILED,TIMEOUT。 */
    @Column(name = "event_type", length = 64)
    private String eventType = EVENT_FAILED + "," + EVENT_TIMEOUT;

    @Column(name = "severity", length = 16)
    private String severity = SEVERITY_P1;

    @Column(name = "project")
    private String project;

    @Column(name = "job_type", length = 64)
    private String jobType;

    @Column(name = "silence_minutes")
    private Integer silenceMinutes = 30;

    /** JSON 数组，例如 ["role:ROLE_TENANT_ADMIN","user:admin"]。 */
    @Column(name = "recipients")
    private String recipients = "[\"role:ROLE_TENANT_ADMIN\"]";

    /** JSON 数组，选中的通知渠道 ID。站内信始终开启，无需放入这里。 */
    @Column(name = "channel_ids")
    private String channelIds = "[]";

    @Column(name = "is_default")
    private Boolean defaultRule = Boolean.FALSE;

    @Column(name = "enabled")
    private Boolean enabled = Boolean.TRUE;

    @Column(name = "create_time")
    private ZonedDateTime createTime;

    @Column(name = "update_time")
    private ZonedDateTime updateTime;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getJobType() {
        return jobType;
    }

    public void setJobType(String jobType) {
        this.jobType = jobType;
    }

    public Integer getSilenceMinutes() {
        return silenceMinutes;
    }

    public void setSilenceMinutes(Integer silenceMinutes) {
        this.silenceMinutes = silenceMinutes;
    }

    public String getRecipients() {
        return recipients;
    }

    public void setRecipients(String recipients) {
        this.recipients = recipients;
    }

    public String getChannelIds() {
        return channelIds;
    }

    public void setChannelIds(String channelIds) {
        this.channelIds = channelIds;
    }

    public Boolean getDefaultRule() {
        return defaultRule;
    }

    public void setDefaultRule(Boolean defaultRule) {
        this.defaultRule = defaultRule;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public ZonedDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(ZonedDateTime createTime) {
        this.createTime = createTime;
    }

    public ZonedDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(ZonedDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
