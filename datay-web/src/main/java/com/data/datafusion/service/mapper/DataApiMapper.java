package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.DataApi;
import com.data.datafusion.service.dto.DataApiDTO;
import org.mapstruct.Mapper;

/**
 * Mapper for the entity {@link DataApi} and its DTO {@link DataApiDTO}.
 */
@Mapper(componentModel = "spring")
public interface DataApiMapper extends EntityMapper<DataApiDTO, DataApi> {}
