package com.data.datafusion.domain;

import static com.data.datafusion.domain.JobDependTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class JobDependTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(JobDepend.class);
        JobDepend jobDepend1 = getJobDependSample1();
        JobDepend jobDepend2 = new JobDepend();
        assertThat(jobDepend1).isNotEqualTo(jobDepend2);

        jobDepend2.setId(jobDepend1.getId());
        assertThat(jobDepend1).isEqualTo(jobDepend2);

        jobDepend2 = getJobDependSample2();
        assertThat(jobDepend1).isNotEqualTo(jobDepend2);
    }
}
