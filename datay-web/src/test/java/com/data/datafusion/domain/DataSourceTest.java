package com.data.datafusion.domain;

import static com.data.datafusion.domain.DataSourceTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class DataSourceTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(DataSource.class);
        DataSource dataSource1 = getDataSourceSample1();
        DataSource dataSource2 = new DataSource();
        assertThat(dataSource1).isNotEqualTo(dataSource2);

        dataSource2.setId(dataSource1.getId());
        assertThat(dataSource1).isEqualTo(dataSource2);

        dataSource2 = getDataSourceSample2();
        assertThat(dataSource1).isNotEqualTo(dataSource2);
    }
}
