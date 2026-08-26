package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.DataSyncTableConfig;
import com.data.datafusion.service.dto.DataSyncTableConfigDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link DataSyncTableConfig} and its DTO {@link DataSyncTableConfigDTO}.
 */
@Mapper(componentModel = "spring")
public interface DataSyncTableConfigMapper extends EntityMapper<DataSyncTableConfigDTO, DataSyncTableConfig> {}
