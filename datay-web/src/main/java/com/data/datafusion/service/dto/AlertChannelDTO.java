package com.data.datafusion.service.dto;

import java.io.Serializable;
import java.time.ZonedDateTime;

public record AlertChannelDTO(
    String id,
    String name,
    String code,
    String type,
    String config,
    Boolean enabled,
    ZonedDateTime createTime,
    ZonedDateTime updateTime
) implements Serializable {}
