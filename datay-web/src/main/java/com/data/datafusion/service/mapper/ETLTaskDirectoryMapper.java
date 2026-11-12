package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.ETLTaskDirectory;
import com.data.datafusion.service.dto.ETLTaskDirectoryDTO;
import org.mapstruct.Mapper;

/**
 * Mapper for the entity {@link ETLTaskDirectory} and its DTO {@link ETLTaskDirectoryDTO}.
 */
@Mapper(componentModel = "spring")
public interface ETLTaskDirectoryMapper extends EntityMapper<ETLTaskDirectoryDTO, ETLTaskDirectory> {}
