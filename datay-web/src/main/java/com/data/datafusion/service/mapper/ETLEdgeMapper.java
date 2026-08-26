package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.ETLEdge;
import com.data.datafusion.service.dto.ETLEdgeDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link ETLEdge} and its DTO {@link ETLEdgeDTO}.
 */
@Mapper(componentModel = "spring")
public interface ETLEdgeMapper extends EntityMapper<ETLEdgeDTO, ETLEdge> {}
