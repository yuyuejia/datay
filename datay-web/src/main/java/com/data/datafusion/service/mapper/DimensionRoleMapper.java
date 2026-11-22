package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.DimensionRole;
import com.data.datafusion.service.dto.DimensionRoleDTO;
import org.mapstruct.Mapper;

/**
 * Mapper for the entity {@link DimensionRole} and its DTO {@link DimensionRoleDTO}.
 */
@Mapper(componentModel = "spring")
public interface DimensionRoleMapper extends EntityMapper<DimensionRoleDTO, DimensionRole> {}
