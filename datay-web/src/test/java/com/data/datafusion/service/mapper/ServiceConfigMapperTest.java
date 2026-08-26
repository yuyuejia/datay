package com.data.datafusion.service.mapper;

import static com.data.datafusion.domain.ServiceConfigAsserts.*;
import static com.data.datafusion.domain.ServiceConfigTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceConfigMapperTest {

    private ServiceConfigMapper serviceConfigMapper;

    @BeforeEach
    void setUp() {
        serviceConfigMapper = new ServiceConfigMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getServiceConfigSample1();
        var actual = serviceConfigMapper.toEntity(serviceConfigMapper.toDto(expected));
        assertServiceConfigAllPropertiesEquals(expected, actual);
    }
}
