package com.data.datafusion.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.DataApi;
import com.data.datafusion.service.DataApiExecuteService;
import com.data.datafusion.service.DataApiService;
import com.data.datafusion.service.dto.DataApiDTO;
import com.data.datafusion.service.dto.DataApiDebugRequestDTO;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * {@link DataApiResource} 调试接口单元测试。
 */
class DataApiResourceTest {

    private DataApiExecuteService dataApiExecuteService;

    private DataApiResource resource;

    @BeforeEach
    void setUp() {
        dataApiExecuteService = mock(DataApiExecuteService.class);
        resource = new DataApiResource(mock(DataApiService.class), dataApiExecuteService);
    }

    @Test
    void shouldReturnDebugResult() {
        DataApiDTO dto = new DataApiDTO();
        dto.setSourceType(DataApi.SOURCE_TYPE_API);
        DataApiDebugRequestDTO request = new DataApiDebugRequestDTO();
        request.setDataApi(dto);
        request.setParams(Map.of("status", "PAID"));
        when(dataApiExecuteService.debugInvoke(any(), any())).thenReturn(Map.of("code", 0, "message", "success"));

        ResponseEntity<Map<String, Object>> response = resource.debugDataApi(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("code", 0);
        verify(dataApiExecuteService).debugInvoke(dto, request.getParams());
    }

    @Test
    void shouldRejectMissingDataApiConfiguration() {
        DataApiDebugRequestDTO request = new DataApiDebugRequestDTO();

        assertThatThrownBy(() -> resource.debugDataApi(request)).isInstanceOf(BadRequestAlertException.class);
    }
}
