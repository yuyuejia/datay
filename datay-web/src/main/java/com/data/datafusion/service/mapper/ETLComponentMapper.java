package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.ETLComponent;
import com.data.datafusion.service.dto.ETLComponentDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link ETLComponent} and its DTO {@link ETLComponentDTO}.
 */
@Mapper(componentModel = "spring")
public interface ETLComponentMapper extends EntityMapper<ETLComponentDTO, ETLComponent> {}
