package com.data.datafusion.web.rest;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.data.datafusion.repository.JobDependRepository;
import com.data.datafusion.service.JobDependService;
import com.data.datafusion.service.dto.JobDependDTO;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = JobDependResource.class, properties = "jhipster.clientApp.name=datafusionApp")
@AutoConfigureMockMvc(addFilters = false)
class JobDependByChildResourceTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JobDependService jobDependService;

    @MockitoBean
    private JobDependRepository jobDependRepository;

    @Test
    void getDependenciesByChildJobCode() throws Exception {
        JobDependDTO dto = new JobDependDTO();
        dto.setParentJobCode("parent-1");
        dto.setChildJobCode("child-1");
        dto.setLastInterval(60000L);
        when(jobDependService.findDtosByChildJobCode("child-1")).thenReturn(List.of(dto));

        mvc
            .perform(get("/api/job-depends/by-child/child-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].parentJobCode").value("parent-1"))
            .andExpect(jsonPath("$[0].childJobCode").value("child-1"))
            .andExpect(jsonPath("$[0].lastInterval").value(60000));
    }
}
