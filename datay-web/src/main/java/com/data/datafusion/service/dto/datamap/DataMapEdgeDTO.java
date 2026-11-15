package com.data.datafusion.service.dto.datamap;

/**
 * 数据地图关系边。
 */
public record DataMapEdgeDTO(String id, String source, String target, String relation, String label, String confidence) {}
