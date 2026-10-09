package com.data.datafusion.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ETLComponentDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(ETLComponentDTO.class);
        ETLComponentDTO eTLComponentDTO1 = new ETLComponentDTO();
        eTLComponentDTO1.setId("1");
        ETLComponentDTO eTLComponentDTO2 = new ETLComponentDTO();
        assertThat(eTLComponentDTO1).isNotEqualTo(eTLComponentDTO2);
        eTLComponentDTO2.setId(eTLComponentDTO1.getId());
        assertThat(eTLComponentDTO1).isEqualTo(eTLComponentDTO2);
        eTLComponentDTO2.setId("2");
        assertThat(eTLComponentDTO1).isNotEqualTo(eTLComponentDTO2);
        eTLComponentDTO1.setId(null);
        assertThat(eTLComponentDTO1).isNotEqualTo(eTLComponentDTO2);
    }
}
