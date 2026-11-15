package com.data.datafusion.service.dto.datamap;

import java.util.List;

/**
 * 数据地图资产详情。
 */
public record DataMapAssetDetailDTO(DataMapNodeDTO node, List<DataMapNodeDTO> fields, DataMapGraphDTO graph, String link) {}
