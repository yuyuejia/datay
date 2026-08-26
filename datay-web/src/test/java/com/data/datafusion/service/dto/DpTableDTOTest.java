package com.data.datafusion.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class DpTableDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(DpTableDTO.class);
        DpTableDTO dpTableDTO1 = new DpTableDTO();
        dpTableDTO1.setId(1L);
        DpTableDTO dpTableDTO2 = new DpTableDTO();
        assertThat(dpTableDTO1).isNotEqualTo(dpTableDTO2);
        dpTableDTO2.setId(dpTableDTO1.getId());
        assertThat(dpTableDTO1).isEqualTo(dpTableDTO2);
        dpTableDTO2.setId(2L);
        assertThat(dpTableDTO1).isNotEqualTo(dpTableDTO2);
        dpTableDTO1.setId(null);
        assertThat(dpTableDTO1).isNotEqualTo(dpTableDTO2);
    }
}
