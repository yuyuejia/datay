package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.ServiceConfig;
import com.data.datafusion.service.dto.ServiceConfigDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link ServiceConfig} and its DTO {@link ServiceConfigDTO}.
 */
@Mapper(componentModel = "spring")
public interface ServiceConfigMapper extends EntityMapper<ServiceConfigDTO, ServiceConfig> {}
