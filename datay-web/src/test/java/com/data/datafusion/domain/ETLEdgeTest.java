package com.data.datafusion.domain;

import static com.data.datafusion.domain.ETLEdgeTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ETLEdgeTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ETLEdge.class);
        ETLEdge eTLEdge1 = getETLEdgeSample1();
        ETLEdge eTLEdge2 = new ETLEdge();
        assertThat(eTLEdge1).isNotEqualTo(eTLEdge2);

        eTLEdge2.setId(eTLEdge1.getId());
        assertThat(eTLEdge1).isEqualTo(eTLEdge2);

        eTLEdge2 = getETLEdgeSample2();
        assertThat(eTLEdge1).isNotEqualTo(eTLEdge2);
    }
}
