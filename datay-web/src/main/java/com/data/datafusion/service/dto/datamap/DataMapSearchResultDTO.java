package com.data.datafusion.service.dto.datamap;

import java.util.List;
import java.util.Map;

/**
 * 数据地图检索响应。
 */
public record DataMapSearchResultDTO(
    List<DataMapSearchItemDTO> items,
    long total,
    Map<String, List<DataMapFacetDTO>> facets,
    long tookMs,
    boolean indexing,
    String indexStatus
) {}
