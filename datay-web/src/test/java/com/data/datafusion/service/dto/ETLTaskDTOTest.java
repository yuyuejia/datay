package com.data.datafusion.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ETLTaskDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(ETLTaskDTO.class);
        ETLTaskDTO eTLTaskDTO1 = new ETLTaskDTO();
        eTLTaskDTO1.setId("1");
        ETLTaskDTO eTLTaskDTO2 = new ETLTaskDTO();
        assertThat(eTLTaskDTO1).isNotEqualTo(eTLTaskDTO2);
        eTLTaskDTO2.setId(eTLTaskDTO1.getId());
        assertThat(eTLTaskDTO1).isEqualTo(eTLTaskDTO2);
        eTLTaskDTO2.setId("2");
        assertThat(eTLTaskDTO1).isNotEqualTo(eTLTaskDTO2);
        eTLTaskDTO1.setId(null);
        assertThat(eTLTaskDTO1).isNotEqualTo(eTLTaskDTO2);
    }
}
