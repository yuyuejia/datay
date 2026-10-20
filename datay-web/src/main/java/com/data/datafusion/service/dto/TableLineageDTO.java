package com.data.datafusion.service.dto;

import java.util.List;

/**
 * 物理表全链路血缘。
 */
public record TableLineageDTO(String currentTableId, List<LineageNodeDTO> nodes, List<LineageEdgeDTO> edges) {}
