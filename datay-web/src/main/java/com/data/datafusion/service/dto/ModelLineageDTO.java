package com.data.datafusion.service.dto;

import java.util.List;

/**
 * 数据模型全链路血缘。
 */
public record ModelLineageDTO(String currentModelId, List<LineageNodeDTO> nodes, List<LineageEdgeDTO> edges) {}
