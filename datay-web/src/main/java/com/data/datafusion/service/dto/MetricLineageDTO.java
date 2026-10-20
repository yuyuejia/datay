package com.data.datafusion.service.dto;

import java.util.List;

/**
 * 指标全链路血缘。
 */
public record MetricLineageDTO(String currentMetricId, List<LineageNodeDTO> nodes, List<LineageEdgeDTO> edges) {}
