package com.data.datafusion.service.dto.datamap;

/**
 * 数据地图索引状态。
 */
public record DataMapIndexStatusDTO(
    String status,
    String startedAt,
    String finishedAt,
    long nodeCount,
    long edgeCount,
    String message,
    boolean stale,
    String coverage
) {}
