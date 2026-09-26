package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.AnalysisDashboard;
import com.data.datafusion.service.dto.AnalysisDashboardDTO;
import org.mapstruct.Mapper;

/**
 * Mapper for the entity {@link AnalysisDashboard} and its DTO {@link AnalysisDashboardDTO}.
 */
@Mapper(componentModel = "spring")
public interface AnalysisDashboardMapper extends EntityMapper<AnalysisDashboardDTO, AnalysisDashboard> {}
