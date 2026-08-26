package com.data.datafusion.domain;

import static com.data.datafusion.domain.ETLTaskTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ETLTaskTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ETLTask.class);
        ETLTask eTLTask1 = getETLTaskSample1();
        ETLTask eTLTask2 = new ETLTask();
        assertThat(eTLTask1).isNotEqualTo(eTLTask2);

        eTLTask2.setId(eTLTask1.getId());
        assertThat(eTLTask1).isEqualTo(eTLTask2);

        eTLTask2 = getETLTaskSample2();
        assertThat(eTLTask1).isNotEqualTo(eTLTask2);
    }
}
