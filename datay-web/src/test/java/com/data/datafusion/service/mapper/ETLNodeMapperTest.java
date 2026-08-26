package com.data.datafusion.service.mapper;

import static com.data.datafusion.domain.ETLNodeAsserts.*;
import static com.data.datafusion.domain.ETLNodeTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ETLNodeMapperTest {

    private ETLNodeMapper eTLNodeMapper;

    @BeforeEach
    void setUp() {
        eTLNodeMapper = new ETLNodeMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getETLNodeSample1();
        var actual = eTLNodeMapper.toEntity(eTLNodeMapper.toDto(expected));
        assertETLNodeAllPropertiesEquals(expected, actual);
    }
}
