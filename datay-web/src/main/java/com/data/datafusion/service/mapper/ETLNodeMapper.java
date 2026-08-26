package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.ETLNode;
import com.data.datafusion.service.dto.ETLNodeDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link ETLNode} and its DTO {@link ETLNodeDTO}.
 */
@Mapper(componentModel = "spring")
public interface ETLNodeMapper extends EntityMapper<ETLNodeDTO, ETLNode> {}
