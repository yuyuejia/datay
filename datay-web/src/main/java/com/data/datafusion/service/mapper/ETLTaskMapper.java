package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.ETLTask;
import com.data.datafusion.service.dto.ETLTaskDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link ETLTask} and its DTO {@link ETLTaskDTO}.
 */
@Mapper(componentModel = "spring")
public interface ETLTaskMapper extends EntityMapper<ETLTaskDTO, ETLTask> {}
