package com.data.datafusion.service.dto.datamap;

import java.util.Map;

/**
 * 数据地图概览。
 */
public record DataMapOverviewDTO(
    boolean enabled,
    DataMapIndexStatusDTO indexStatus,
    long totalNodes,
    long totalEdges,
    Map<String, Long> typeCounts,
    String coverage
) {}
