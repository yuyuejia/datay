package com.data.datafusion.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class DataSyncTableConfigDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(DataSyncTableConfigDTO.class);
        DataSyncTableConfigDTO dataSyncTableConfigDTO1 = new DataSyncTableConfigDTO();
        dataSyncTableConfigDTO1.setId("1");
        DataSyncTableConfigDTO dataSyncTableConfigDTO2 = new DataSyncTableConfigDTO();
        assertThat(dataSyncTableConfigDTO1).isNotEqualTo(dataSyncTableConfigDTO2);
        dataSyncTableConfigDTO2.setId(dataSyncTableConfigDTO1.getId());
        assertThat(dataSyncTableConfigDTO1).isEqualTo(dataSyncTableConfigDTO2);
        dataSyncTableConfigDTO2.setId("2");
        assertThat(dataSyncTableConfigDTO1).isNotEqualTo(dataSyncTableConfigDTO2);
        dataSyncTableConfigDTO1.setId(null);
        assertThat(dataSyncTableConfigDTO1).isNotEqualTo(dataSyncTableConfigDTO2);
    }
}
