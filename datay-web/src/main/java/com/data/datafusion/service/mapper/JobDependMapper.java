package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.JobDepend;
import com.data.datafusion.service.dto.JobDependDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link JobDepend} and its DTO {@link JobDependDTO}.
 */
@Mapper(componentModel = "spring")
public interface JobDependMapper extends EntityMapper<JobDependDTO, JobDepend> {}
