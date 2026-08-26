package com.data.datafusion.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class JobInstanceDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(JobInstanceDTO.class);
        JobInstanceDTO jobInstanceDTO1 = new JobInstanceDTO();
        jobInstanceDTO1.setId(1L);
        JobInstanceDTO jobInstanceDTO2 = new JobInstanceDTO();
        assertThat(jobInstanceDTO1).isNotEqualTo(jobInstanceDTO2);
        jobInstanceDTO2.setId(jobInstanceDTO1.getId());
        assertThat(jobInstanceDTO1).isEqualTo(jobInstanceDTO2);
        jobInstanceDTO2.setId(2L);
        assertThat(jobInstanceDTO1).isNotEqualTo(jobInstanceDTO2);
        jobInstanceDTO1.setId(null);
        assertThat(jobInstanceDTO1).isNotEqualTo(jobInstanceDTO2);
    }
}
