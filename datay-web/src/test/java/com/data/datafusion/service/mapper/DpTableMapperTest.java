package com.data.datafusion.service.mapper;

import static com.data.datafusion.domain.DpTableAsserts.*;
import static com.data.datafusion.domain.DpTableTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DpTableMapperTest {

    private DpTableMapper dpTableMapper;

    @BeforeEach
    void setUp() {
        dpTableMapper = new DpTableMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getDpTableSample1();
        var actual = dpTableMapper.toEntity(dpTableMapper.toDto(expected));
        assertDpTableAllPropertiesEquals(expected, actual);
    }
}
