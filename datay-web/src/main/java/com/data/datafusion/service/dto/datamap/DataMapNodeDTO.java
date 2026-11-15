package com.data.datafusion.service.dto.datamap;

import java.util.Map;

/**
 * 数据地图节点。
 *
 * <p>{@code type} 取值见 {@link DataMapAssetTypes}，{@code layer} 仅在关系图响应中相对根节点计算。</p>
 */
public record DataMapNodeDTO(
    String id,
    String type,
    String name,
    String code,
    String description,
    String qualifiedName,
    String dataSourceId,
    String dataSourceName,
    String schema,
    String table,
    int degree,
    String status,
    String updatedAt,
    int layer,
    Map<String, Object> meta
) {
    public DataMapNodeDTO withDegree(int degree) {
        return new DataMapNodeDTO(
            id,
            type,
            name,
            code,
            description,
            qualifiedName,
            dataSourceId,
            dataSourceName,
            schema,
            table,
            degree,
            status,
            updatedAt,
            layer,
            meta
        );
    }

    public DataMapNodeDTO withLayer(int layer) {
        return new DataMapNodeDTO(
            id,
            type,
            name,
            code,
            description,
            qualifiedName,
            dataSourceId,
            dataSourceName,
            schema,
            table,
            degree,
            status,
            updatedAt,
            layer,
            meta
        );
    }
}
