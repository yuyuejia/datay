package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.RoleDatasetScope;
import com.data.datafusion.service.dto.RoleDatasetScopeDTO;
import org.mapstruct.Mapper;

/**
 * Mapper for the entity {@link RoleDatasetScope} and its DTO {@link RoleDatasetScopeDTO}.
 */
@Mapper(componentModel = "spring")
public interface RoleDatasetScopeMapper extends EntityMapper<RoleDatasetScopeDTO, RoleDatasetScope> {}
