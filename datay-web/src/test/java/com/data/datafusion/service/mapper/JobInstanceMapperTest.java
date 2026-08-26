package com.data.datafusion.service.mapper;

import static com.data.datafusion.domain.JobInstanceAsserts.*;
import static com.data.datafusion.domain.JobInstanceTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobInstanceMapperTest {

    private JobInstanceMapper jobInstanceMapper;

    @BeforeEach
    void setUp() {
        jobInstanceMapper = new JobInstanceMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getJobInstanceSample1();
        var actual = jobInstanceMapper.toEntity(jobInstanceMapper.toDto(expected));
        assertJobInstanceAllPropertiesEquals(expected, actual);
    }
}
