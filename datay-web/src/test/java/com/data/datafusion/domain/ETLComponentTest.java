package com.data.datafusion.domain;

import static com.data.datafusion.domain.ETLComponentTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ETLComponentTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ETLComponent.class);
        ETLComponent eTLComponent1 = getETLComponentSample1();
        ETLComponent eTLComponent2 = new ETLComponent();
        assertThat(eTLComponent1).isNotEqualTo(eTLComponent2);

        eTLComponent2.setId(eTLComponent1.getId());
        assertThat(eTLComponent1).isEqualTo(eTLComponent2);

        eTLComponent2 = getETLComponentSample2();
        assertThat(eTLComponent1).isNotEqualTo(eTLComponent2);
    }
}
