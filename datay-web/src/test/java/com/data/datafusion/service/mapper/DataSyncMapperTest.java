package com.data.datafusion.service.mapper;

import static com.data.datafusion.domain.DataSyncAsserts.*;
import static com.data.datafusion.domain.DataSyncTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataSyncMapperTest {

    private DataSyncMapper dataSyncMapper;

    @BeforeEach
    void setUp() {
        dataSyncMapper = new DataSyncMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getDataSyncSample1();
        var actual = dataSyncMapper.toEntity(dataSyncMapper.toDto(expected));
        assertDataSyncAllPropertiesEquals(expected, actual);
    }
}
