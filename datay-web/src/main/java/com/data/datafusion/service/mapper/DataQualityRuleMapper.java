package com.data.datafusion.service.mapper;

import com.data.datafusion.domain.DataQualityRule;
import com.data.datafusion.service.dto.DataQualityRuleDTO;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface DataQualityRuleMapper extends EntityMapper<DataQualityRuleDTO, DataQualityRule> {}
