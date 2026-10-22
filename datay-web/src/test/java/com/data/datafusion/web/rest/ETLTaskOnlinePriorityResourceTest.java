package com.data.datafusion.web.rest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.data.datafusion.repository.ETLTaskRepository;
import com.data.datafusion.service.ETLTaskService;
import com.data.datafusion.service.JobInstanceService;
import com.data.datafusion.service.dto.ETLTaskDTO;
import com.data.datafusion.service.etl.ETLTaskTransferService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ETLTaskResource.class, properties = "jhipster.clientApp.name=datafusionApp")
@AutoConfigureMockMvc(addFilters = false)
class ETLTaskOnlinePriorityResourceTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ETLTaskService eTLTaskService;

    @MockitoBean
    private ETLTaskRepository eTLTaskRepository;

    @MockitoBean
    private JobInstanceService jobInstanceService;

    @MockitoBean
    private ETLTaskTransferService eTLTaskTransferService;

    @Test
    void onlinePassesRequestedPriorityToService() throws Exception {
        when(eTLTaskService.online("etl-1", null, null, null, 3)).thenReturn(new ETLTaskDTO());

        mvc.perform(post("/api/etl-tasks/etl-1/online").param("priority", "3")).andExpect(status().isOk());

        verify(eTLTaskService).online("etl-1", null, null, null, 3);
    }

    @Test
    void onlineKeepsExistingPriorityWhenRequestOmitsIt() throws Exception {
        when(eTLTaskService.online("etl-1", null, null, null, null)).thenReturn(new ETLTaskDTO());

        mvc.perform(post("/api/etl-tasks/etl-1/online")).andExpect(status().isOk());

        verify(eTLTaskService).online("etl-1", null, null, null, null);
    }

    @Test
    void onlineRejectsPriorityOutsideSupportedRange() throws Exception {
        mvc.perform(post("/api/etl-tasks/etl-1/online").param("priority", "0")).andExpect(status().isBadRequest());

        verify(eTLTaskService, never()).online(anyString(), any(), any(), any(), any());
    }
}
