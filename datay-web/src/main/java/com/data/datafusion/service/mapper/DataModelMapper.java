package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.DataModel;
import com.data.datafusion.service.dto.DataModelDTO;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface DataModelMapper extends EntityMapper<DataModelDTO, DataModel> {}