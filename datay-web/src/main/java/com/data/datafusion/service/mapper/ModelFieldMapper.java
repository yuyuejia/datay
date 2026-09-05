package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.ModelField;
import com.data.datafusion.service.dto.ModelFieldDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ModelFieldMapper extends EntityMapper<ModelFieldDTO, ModelField> {}