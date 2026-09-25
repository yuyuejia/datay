package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.RoleDataScope;
import com.data.datafusion.service.dto.RoleDataScopeDTO;
import org.mapstruct.Mapper;

/**
 * Mapper for the entity {@link RoleDataScope} and its DTO {@link RoleDataScopeDTO}.
 */
@Mapper(componentModel = "spring")
public interface RoleDataScopeMapper extends EntityMapper<RoleDataScopeDTO, RoleDataScope> {}
