package com.data.datafusion.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class JobDependDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(JobDependDTO.class);
        JobDependDTO jobDependDTO1 = new JobDependDTO();
        jobDependDTO1.setId(1L);
        JobDependDTO jobDependDTO2 = new JobDependDTO();
        assertThat(jobDependDTO1).isNotEqualTo(jobDependDTO2);
        jobDependDTO2.setId(jobDependDTO1.getId());
        assertThat(jobDependDTO1).isEqualTo(jobDependDTO2);
        jobDependDTO2.setId(2L);
        assertThat(jobDependDTO1).isNotEqualTo(jobDependDTO2);
        jobDependDTO1.setId(null);
        assertThat(jobDependDTO1).isNotEqualTo(jobDependDTO2);
    }
}
