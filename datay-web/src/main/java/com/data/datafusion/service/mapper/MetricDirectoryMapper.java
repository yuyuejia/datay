package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.MetricDirectory;
import com.data.datafusion.service.dto.MetricDirectoryDTO;
import org.mapstruct.Mapper;

/**
 * Mapper for the entity {@link MetricDirectory} and its DTO {@link MetricDirectoryDTO}.
 */
@Mapper(componentModel = "spring")
public interface MetricDirectoryMapper extends EntityMapper<MetricDirectoryDTO, MetricDirectory> {}
