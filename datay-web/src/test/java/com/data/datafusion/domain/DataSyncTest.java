package com.data.datafusion.domain;

import static com.data.datafusion.domain.DataSyncTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class DataSyncTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(DataSync.class);
        DataSync dataSync1 = getDataSyncSample1();
        DataSync dataSync2 = new DataSync();
        assertThat(dataSync1).isNotEqualTo(dataSync2);

        dataSync2.setId(dataSync1.getId());
        assertThat(dataSync1).isEqualTo(dataSync2);

        dataSync2 = getDataSyncSample2();
        assertThat(dataSync1).isNotEqualTo(dataSync2);
    }
}
