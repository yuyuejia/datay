package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.DataQualityTask;
import com.data.datafusion.service.dto.DataQualityTaskDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DataQualityTaskMapper extends EntityMapper<DataQualityTaskDTO, DataQualityTask> {
    @Override
    @Mapping(target = "rules", ignore = true)
    @Mapping(target = "lastResult", ignore = true)
    DataQualityTaskDTO toDto(DataQualityTask entity);
}
