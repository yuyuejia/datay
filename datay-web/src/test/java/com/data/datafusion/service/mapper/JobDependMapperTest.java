package com.data.datafusion.service.mapper;

import static com.data.datafusion.domain.JobDependAsserts.*;
import static com.data.datafusion.domain.JobDependTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobDependMapperTest {

    private JobDependMapper jobDependMapper;

    @BeforeEach
    void setUp() {
        jobDependMapper = new JobDependMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getJobDependSample1();
        var actual = jobDependMapper.toEntity(jobDependMapper.toDto(expected));
        assertJobDependAllPropertiesEquals(expected, actual);
    }
}
