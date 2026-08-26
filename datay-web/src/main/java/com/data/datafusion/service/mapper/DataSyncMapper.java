package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.DataSync;
import com.data.datafusion.service.dto.DataSyncDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link DataSync} and its DTO {@link DataSyncDTO}.
 */
@Mapper(componentModel = "spring")
public interface DataSyncMapper extends EntityMapper<DataSyncDTO, DataSync> {}
