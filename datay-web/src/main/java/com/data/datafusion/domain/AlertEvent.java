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
 * 告警事件：一次真实触发的告警，重复触发时累计次数而不是重复建事件。
 */
@Entity
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Table(name = "dp_alert_event")
@EntityListeners(com.data.datafusion.config.TenantAwareEntityListener.class)
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AlertEvent implements Serializable, TenantAware {

    public static final String STATUS_FIRING = "FIRING";

    public static final String STATUS_ACKNOWLEDGED = "ACKNOWLEDGED";

    public static final String STATUS_RESOLVED = "RESOLVED";

    private static final long serialVersionUID = 1L;

    @Id
    @UuidV7Id
    @Column(name = "id", length = 36)
    private String id;

    @Column(name = "tenant_id")
    private String tenantId;

    @Column(name = "rule_id", length = 36)
    private String ruleId;

    @Column(name = "fingerprint", length = 64)
    private String fingerprint;

    @Column(name = "source_type", length = 32)
    private String sourceType;

    @Column(name = "event_type", length = 32)
    private String eventType;

    @Column(name = "severity", length = 16)
    private String severity;

    @Column(name = "title")
    private String title;

    @Column(name = "content")
    private String content;

    @Column(name = "job_code")
    private String jobCode;

    @Column(name = "job_name")
    private String jobName;

    @Column(name = "job_instance_id", length = 36)
    private String jobInstanceId;

    @Column(name = "instance_code")
    private String instanceCode;

    @Column(name = "project")
    private String project;

    @Column(name = "status", length = 32)
    private String status = STATUS_FIRING;

    @Column(name = "event_count")
    private Integer eventCount = 1;

    @Column(name = "first_seen")
    private ZonedDateTime firstSeen;

    @Column(name = "last_seen")
    private ZonedDateTime lastSeen;

    @Column(name = "ack_by", length = 100)
    private String ackBy;

    @Column(name = "ack_time")
    private ZonedDateTime ackTime;

    @Column(name = "resolve_by", length = 100)
    private String resolveBy;

    @Column(name = "resolve_time")
    private ZonedDateTime resolveTime;

    /** 触发时的接收人快照，避免规则修改后历史事件无法恢复通知。 */
    @Column(name = "recipients")
    private String recipients;

    /** 触发时选中的通知渠道 ID 快照。 */
    @Column(name = "channel_ids")
    private String channelIds;

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

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getJobCode() {
        return jobCode;
    }

    public void setJobCode(String jobCode) {
        this.jobCode = jobCode;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getJobInstanceId() {
        return jobInstanceId;
    }

    public void setJobInstanceId(String jobInstanceId) {
        this.jobInstanceId = jobInstanceId;
    }

    public String getInstanceCode() {
        return instanceCode;
    }

    public void setInstanceCode(String instanceCode) {
        this.instanceCode = instanceCode;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getEventCount() {
        return eventCount;
    }

    public void setEventCount(Integer eventCount) {
        this.eventCount = eventCount;
    }

    public ZonedDateTime getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(ZonedDateTime firstSeen) {
        this.firstSeen = firstSeen;
    }

    public ZonedDateTime getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(ZonedDateTime lastSeen) {
        this.lastSeen = lastSeen;
    }

    public String getAckBy() {
        return ackBy;
    }

    public void setAckBy(String ackBy) {
        this.ackBy = ackBy;
    }

    public ZonedDateTime getAckTime() {
        return ackTime;
    }

    public void setAckTime(ZonedDateTime ackTime) {
        this.ackTime = ackTime;
    }

    public String getResolveBy() {
        return resolveBy;
    }

    public void setResolveBy(String resolveBy) {
        this.resolveBy = resolveBy;
    }

    public ZonedDateTime getResolveTime() {
        return resolveTime;
    }

    public void setResolveTime(ZonedDateTime resolveTime) {
        this.resolveTime = resolveTime;
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
