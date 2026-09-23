package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.Metric;
import com.data.datafusion.service.dto.MetricDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper for the entity {@link Metric} and its DTO {@link MetricDTO}.
 */
@Mapper(componentModel = "spring")
public interface MetricMapper extends EntityMapper<MetricDTO, Metric> {
    @Mapping(target = "refMetrics", ignore = true)
    @Mapping(target = "factModelName", ignore = true)
    @Mapping(target = "factTableName", ignore = true)
    MetricDTO toDto(Metric metric);
}
