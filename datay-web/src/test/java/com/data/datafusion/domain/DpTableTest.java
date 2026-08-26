package com.data.datafusion.domain;

import static com.data.datafusion.domain.DpTableTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class DpTableTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(DpTable.class);
        DpTable dpTable1 = getDpTableSample1();
        DpTable dpTable2 = new DpTable();
        assertThat(dpTable1).isNotEqualTo(dpTable2);

        dpTable2.setId(dpTable1.getId());
        assertThat(dpTable1).isEqualTo(dpTable2);

        dpTable2 = getDpTableSample2();
        assertThat(dpTable1).isNotEqualTo(dpTable2);
    }
}
