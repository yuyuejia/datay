package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.DataSource;
import com.data.datafusion.service.dto.DataSourceDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link DataSource} and its DTO {@link DataSourceDTO}.
 */
@Mapper(componentModel = "spring")
public interface DataSourceMapper extends EntityMapper<DataSourceDTO, DataSource> {}
