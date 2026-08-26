package com.data.datafusion.service.mapper;

import static com.data.datafusion.domain.ETLEdgeAsserts.*;
import static com.data.datafusion.domain.ETLEdgeTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ETLEdgeMapperTest {

    private ETLEdgeMapper eTLEdgeMapper;

    @BeforeEach
    void setUp() {
        eTLEdgeMapper = new ETLEdgeMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getETLEdgeSample1();
        var actual = eTLEdgeMapper.toEntity(eTLEdgeMapper.toDto(expected));
        assertETLEdgeAllPropertiesEquals(expected, actual);
    }
}
