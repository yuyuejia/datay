package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.AnalysisDataset;
import com.data.datafusion.service.dto.AnalysisDatasetDTO;
import org.mapstruct.Mapper;

/**
 * Mapper for the entity {@link AnalysisDataset} and its DTO {@link AnalysisDatasetDTO}.
 */
@Mapper(componentModel = "spring")
public interface AnalysisDatasetMapper extends EntityMapper<AnalysisDatasetDTO, AnalysisDataset> {}
