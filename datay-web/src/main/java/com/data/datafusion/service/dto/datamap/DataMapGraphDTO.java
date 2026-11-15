package com.data.datafusion.service.dto.datamap;

import java.util.List;

/**
 * 数据地图关系图响应。
 *
 * @param scope      full 或 subgraph
 * @param nodes      节点
 * @param links      边
 * @param categories 资产类型分类，用于 ECharts 图例
 * @param totalNodes 截断前的节点总数
 * @param totalLinks 截断前的边总数
 * @param truncated  是否发生截断
 */
public record DataMapGraphDTO(
    String scope,
    List<DataMapNodeDTO> nodes,
    List<DataMapEdgeDTO> links,
    List<String> categories,
    long totalNodes,
    long totalLinks,
    boolean truncated
) {}
