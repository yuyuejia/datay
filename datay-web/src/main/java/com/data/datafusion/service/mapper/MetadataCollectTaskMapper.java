package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.MetadataCollectTask;
import com.data.datafusion.service.dto.MetadataCollectTaskDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface MetadataCollectTaskMapper extends EntityMapper<MetadataCollectTaskDTO, MetadataCollectTask> {
    @Override
    @Mapping(target = "dataSourceName", ignore = true)
    @Mapping(target = "lastRun", ignore = true)
    MetadataCollectTaskDTO toDto(MetadataCollectTask entity);
}
