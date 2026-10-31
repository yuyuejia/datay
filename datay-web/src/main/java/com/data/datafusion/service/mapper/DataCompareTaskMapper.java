package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.DataCompareTask;
import com.data.datafusion.service.dto.DataCompareTaskDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface DataCompareTaskMapper extends EntityMapper<DataCompareTaskDTO, DataCompareTask> {
    @Override
    @Mapping(target = "lastResult", ignore = true)
    DataCompareTaskDTO toDto(DataCompareTask entity);
}
