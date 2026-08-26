package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.JobInstance;
import com.data.datafusion.service.dto.JobInstanceDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link JobInstance} and its DTO {@link JobInstanceDTO}.
 */
@Mapper(componentModel = "spring")
public interface JobInstanceMapper extends EntityMapper<JobInstanceDTO, JobInstance> {}
