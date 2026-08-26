package com.data.datafusion.service.mapper;

import static com.data.datafusion.domain.DataSyncTableConfigAsserts.*;
import static com.data.datafusion.domain.DataSyncTableConfigTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataSyncTableConfigMapperTest {

    private DataSyncTableConfigMapper dataSyncTableConfigMapper;

    @BeforeEach
    void setUp() {
        dataSyncTableConfigMapper = new DataSyncTableConfigMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getDataSyncTableConfigSample1();
        var actual = dataSyncTableConfigMapper.toEntity(dataSyncTableConfigMapper.toDto(expected));
        assertDataSyncTableConfigAllPropertiesEquals(expected, actual);
    }
}
