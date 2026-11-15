package com.data.datafusion.service.dto.datamap;

/**
 * 检索结果条目。
 */
public record DataMapSearchItemDTO(DataMapNodeDTO node, double score, String highlight) {}
