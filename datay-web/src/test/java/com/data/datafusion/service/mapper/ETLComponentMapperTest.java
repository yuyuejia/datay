package com.data.datafusion.service.mapper;

import static com.data.datafusion.domain.ETLComponentAsserts.*;
import static com.data.datafusion.domain.ETLComponentTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ETLComponentMapperTest {

    private ETLComponentMapper eTLComponentMapper;

    @BeforeEach
    void setUp() {
        eTLComponentMapper = new ETLComponentMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getETLComponentSample1();
        var actual = eTLComponentMapper.toEntity(eTLComponentMapper.toDto(expected));
        assertETLComponentAllPropertiesEquals(expected, actual);
    }
}
