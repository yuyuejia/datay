package com.data.datafusion.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ETLEdgeDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(ETLEdgeDTO.class);
        ETLEdgeDTO eTLEdgeDTO1 = new ETLEdgeDTO();
        eTLEdgeDTO1.setId("1");
        ETLEdgeDTO eTLEdgeDTO2 = new ETLEdgeDTO();
        assertThat(eTLEdgeDTO1).isNotEqualTo(eTLEdgeDTO2);
        eTLEdgeDTO2.setId(eTLEdgeDTO1.getId());
        assertThat(eTLEdgeDTO1).isEqualTo(eTLEdgeDTO2);
        eTLEdgeDTO2.setId("2");
        assertThat(eTLEdgeDTO1).isNotEqualTo(eTLEdgeDTO2);
        eTLEdgeDTO1.setId(null);
        assertThat(eTLEdgeDTO1).isNotEqualTo(eTLEdgeDTO2);
    }
}
