package com.data.datafusion.domain;

import static com.data.datafusion.domain.DataSyncTableConfigTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class DataSyncTableConfigTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(DataSyncTableConfig.class);
        DataSyncTableConfig dataSyncTableConfig1 = getDataSyncTableConfigSample1();
        DataSyncTableConfig dataSyncTableConfig2 = new DataSyncTableConfig();
        assertThat(dataSyncTableConfig1).isNotEqualTo(dataSyncTableConfig2);

        dataSyncTableConfig2.setId(dataSyncTableConfig1.getId());
        assertThat(dataSyncTableConfig1).isEqualTo(dataSyncTableConfig2);

        dataSyncTableConfig2 = getDataSyncTableConfigSample2();
        assertThat(dataSyncTableConfig1).isNotEqualTo(dataSyncTableConfig2);
    }
}
