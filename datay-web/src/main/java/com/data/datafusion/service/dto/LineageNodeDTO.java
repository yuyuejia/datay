package com.data.datafusion.service.dto;

import java.util.Map;

/**
 * 血缘图节点。
 */
public record LineageNodeDTO(
    String id,
    String type,
    String name,
    String subType,
    int layer,
    Map<String, Object> meta
) {}
