package com.data.datafusion.domain;

import static com.data.datafusion.domain.ETLNodeTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ETLNodeTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ETLNode.class);
        ETLNode eTLNode1 = getETLNodeSample1();
        ETLNode eTLNode2 = new ETLNode();
        assertThat(eTLNode1).isNotEqualTo(eTLNode2);

        eTLNode2.setId(eTLNode1.getId());
        assertThat(eTLNode1).isEqualTo(eTLNode2);

        eTLNode2 = getETLNodeSample2();
        assertThat(eTLNode1).isNotEqualTo(eTLNode2);
    }
}
