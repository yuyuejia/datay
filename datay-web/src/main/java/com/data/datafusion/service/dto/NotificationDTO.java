package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

public record NotificationDTO(
    String id,
    String eventId,
    String title,
    String content,
    Boolean readFlag,
    ZonedDateTime createTime,
    ZonedDateTime readTime
) implements Serializable {}
