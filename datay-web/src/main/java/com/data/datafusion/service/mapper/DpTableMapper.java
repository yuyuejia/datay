package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.DpTable;
import com.data.datafusion.service.dto.DpTableDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link DpTable} and its DTO {@link DpTableDTO}.
 */
@Mapper(componentModel = "spring")
public interface DpTableMapper extends EntityMapper<DpTableDTO, DpTable> {}
