package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

public record AlertRuleDTO(
    String id,
    String name,
    String code,
    String sourceType,
    String eventType,
    String severity,
    String project,
    String jobType,
    Integer silenceMinutes,
    String recipients,
    String channelIds,
    Boolean defaultRule,
    Boolean enabled,
    ZonedDateTime createTime,
    ZonedDateTime updateTime
) implements Serializable {}
