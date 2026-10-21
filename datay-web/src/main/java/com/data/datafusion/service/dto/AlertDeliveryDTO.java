package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

public record AlertDeliveryDTO(
    String id,
    String eventId,
    String channelId,
    String channelType,
    String target,
    String status,
    Integer attempt,
    String errorMessage,
    ZonedDateTime sentTime,
    ZonedDateTime createTime
) implements Serializable {}
