package com.data.datafusion.service.mapper;

import static com.data.datafusion.domain.ETLTaskAsserts.*;
import static com.data.datafusion.domain.ETLTaskTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ETLTaskMapperTest {

    private ETLTaskMapper eTLTaskMapper;

    @BeforeEach
    void setUp() {
        eTLTaskMapper = new ETLTaskMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getETLTaskSample1();
        var actual = eTLTaskMapper.toEntity(eTLTaskMapper.toDto(expected));
        assertETLTaskAllPropertiesEquals(expected, actual);
    }
}
