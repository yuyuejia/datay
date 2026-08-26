package com.data.datafusion.domain;

import static com.data.datafusion.domain.JobInstanceTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class JobInstanceTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(JobInstance.class);
        JobInstance jobInstance1 = getJobInstanceSample1();
        JobInstance jobInstance2 = new JobInstance();
        assertThat(jobInstance1).isNotEqualTo(jobInstance2);

        jobInstance2.setId(jobInstance1.getId());
        assertThat(jobInstance1).isEqualTo(jobInstance2);

        jobInstance2 = getJobInstanceSample2();
        assertThat(jobInstance1).isNotEqualTo(jobInstance2);
    }
}
