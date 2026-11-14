package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.DataApiCallLog;
import com.data.datafusion.service.dto.DataApiCallLogDTO;
import org.mapstruct.Mapper;

/**
 * Mapper for the entity {@link DataApiCallLog} and its DTO {@link DataApiCallLogDTO}.
 */
@Mapper(componentModel = "spring")
public interface DataApiCallLogMapper extends EntityMapper<DataApiCallLogDTO, DataApiCallLog> {}
