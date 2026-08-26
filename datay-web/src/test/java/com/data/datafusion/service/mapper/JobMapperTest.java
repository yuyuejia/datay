package com.data.datafusion.service.mapper;

import static com.data.datafusion.domain.JobAsserts.*;
import static com.data.datafusion.domain.JobTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobMapperTest {

    private JobMapper jobMapper;

    @BeforeEach
    void setUp() {
        jobMapper = new JobMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getJobSample1();
        var actual = jobMapper.toEntity(jobMapper.toDto(expected));
        assertJobAllPropertiesEquals(expected, actual);
    }
}
