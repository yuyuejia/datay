package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.ModelDirectory;
import com.data.datafusion.service.dto.ModelDirectoryDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ModelDirectoryMapper extends EntityMapper<ModelDirectoryDTO, ModelDirectory> {}