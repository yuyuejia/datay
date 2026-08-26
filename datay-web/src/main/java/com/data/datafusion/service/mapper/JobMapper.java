package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.Job;
import com.data.datafusion.service.dto.JobDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Job} and its DTO {@link JobDTO}.
 */
@Mapper(componentModel = "spring")
public interface JobMapper extends EntityMapper<JobDTO, Job> {}
