package com.data.datafusion.domain;

import static com.data.datafusion.domain.ServiceConfigTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ServiceConfigTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(ServiceConfig.class);
        ServiceConfig serviceConfig1 = getServiceConfigSample1();
        ServiceConfig serviceConfig2 = new ServiceConfig();
        assertThat(serviceConfig1).isNotEqualTo(serviceConfig2);

        serviceConfig2.setId(serviceConfig1.getId());
        assertThat(serviceConfig1).isEqualTo(serviceConfig2);

        serviceConfig2 = getServiceConfigSample2();
        assertThat(serviceConfig1).isNotEqualTo(serviceConfig2);
    }
}
