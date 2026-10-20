package com.data.datafusion.service.dto;

import java.util.List;
import java.util.Map;

/**
 * 数据模型物理表预览结果。
 */
public record ModelPreviewDTO(
    List<String> columns,
    List<Map<String, Object>> rows,
    String table,
    String dataSourceId,
    String dataSourceName,
    int limit
) {}
