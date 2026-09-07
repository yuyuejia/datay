package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.Tenant;
import com.data.datafusion.service.dto.TenantDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TenantMapper extends EntityMapper<TenantDTO, Tenant> {}