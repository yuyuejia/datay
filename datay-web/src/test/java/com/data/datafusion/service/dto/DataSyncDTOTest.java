package com.data.datafusion.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class DataSyncDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(DataSyncDTO.class);
        DataSyncDTO dataSyncDTO1 = new DataSyncDTO();
        dataSyncDTO1.setId("1");
        DataSyncDTO dataSyncDTO2 = new DataSyncDTO();
        assertThat(dataSyncDTO1).isNotEqualTo(dataSyncDTO2);
        dataSyncDTO2.setId(dataSyncDTO1.getId());
        assertThat(dataSyncDTO1).isEqualTo(dataSyncDTO2);
        dataSyncDTO2.setId("2");
        assertThat(dataSyncDTO1).isNotEqualTo(dataSyncDTO2);
        dataSyncDTO1.setId(null);
        assertThat(dataSyncDTO1).isNotEqualTo(dataSyncDTO2);
    }
}
