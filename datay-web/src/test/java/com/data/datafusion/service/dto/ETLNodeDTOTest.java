package com.data.datafusion.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ETLNodeDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(ETLNodeDTO.class);
        ETLNodeDTO eTLNodeDTO1 = new ETLNodeDTO();
        eTLNodeDTO1.setId(1L);
        ETLNodeDTO eTLNodeDTO2 = new ETLNodeDTO();
        assertThat(eTLNodeDTO1).isNotEqualTo(eTLNodeDTO2);
        eTLNodeDTO2.setId(eTLNodeDTO1.getId());
        assertThat(eTLNodeDTO1).isEqualTo(eTLNodeDTO2);
        eTLNodeDTO2.setId(2L);
        assertThat(eTLNodeDTO1).isNotEqualTo(eTLNodeDTO2);
        eTLNodeDTO1.setId(null);
        assertThat(eTLNodeDTO1).isNotEqualTo(eTLNodeDTO2);
    }
}
