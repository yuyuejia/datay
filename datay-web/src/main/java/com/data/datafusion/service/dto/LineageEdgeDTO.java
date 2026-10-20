package com.data.datafusion.service.dto;

/**
 * 血缘图边。
 */
public record LineageEdgeDTO(String id, String source, String target, String kind, String label, String confidence) {}
