package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

public record AlertEventDTO(
    String id,
    String ruleId,
    String sourceType,
    String eventType,
    String severity,
    String title,
    String content,
    String jobCode,
    String jobName,
    String jobInstanceId,
    String instanceCode,
    String project,
    String status,
    Integer eventCount,
    ZonedDateTime firstSeen,
    ZonedDateTime lastSeen,
    String ackBy,
    ZonedDateTime ackTime,
    String resolveBy,
    ZonedDateTime resolveTime,
    ZonedDateTime createTime,
    ZonedDateTime updateTime
) implements Serializable {}
