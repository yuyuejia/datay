package com.data.datafusion.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.data.datafusion.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ServiceConfigDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(ServiceConfigDTO.class);
        ServiceConfigDTO serviceConfigDTO1 = new ServiceConfigDTO();
        serviceConfigDTO1.setId(1L);
        ServiceConfigDTO serviceConfigDTO2 = new ServiceConfigDTO();
        assertThat(serviceConfigDTO1).isNotEqualTo(serviceConfigDTO2);
        serviceConfigDTO2.setId(serviceConfigDTO1.getId());
        assertThat(serviceConfigDTO1).isEqualTo(serviceConfigDTO2);
        serviceConfigDTO2.setId(2L);
        assertThat(serviceConfigDTO1).isNotEqualTo(serviceConfigDTO2);
        serviceConfigDTO1.setId(null);
        assertThat(serviceConfigDTO1).isNotEqualTo(serviceConfigDTO2);
    }
}
