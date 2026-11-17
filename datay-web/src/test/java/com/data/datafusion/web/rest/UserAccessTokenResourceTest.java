package com.data.datafusion.web.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.service.UserAccessTokenService;
import com.data.datafusion.service.dto.CreateUserAccessTokenRequestDTO;
import com.data.datafusion.service.dto.CreatedUserAccessTokenDTO;
import com.data.datafusion.service.dto.UpdateUserAccessTokenRequestDTO;
import com.data.datafusion.service.dto.UserAccessTokenDTO;
import com.data.datafusion.web.rest.errors.BadRequestAlertException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * {@link UserAccessTokenResource} 的单元测试。
 */
class UserAccessTokenResourceTest {

    private UserAccessTokenService service;

    private UserAccessTokenResource resource;

    @BeforeEach
    void setUp() {
        service = mock(UserAccessTokenService.class);
        resource = new UserAccessTokenResource(service);
    }

    @Test
    void shouldListAccessTokens() {
        UserAccessTokenDTO dto = new UserAccessTokenDTO();
        dto.setId("1");
        dto.setName("Claude");
        when(service.listCurrentUserTokens()).thenReturn(List.of(dto));

        List<UserAccessTokenDTO> result = resource.listAccessTokens();

        assertThat(result).containsExactly(dto);
    }

    @Test
    void shouldCreateAccessToken() {
        CreateUserAccessTokenRequestDTO request = new CreateUserAccessTokenRequestDTO();
        request.setName("Claude");
        UserAccessTokenDTO dto = new UserAccessTokenDTO();
        dto.setId("1");
        when(service.createCurrentUserToken("Claude", null)).thenReturn(new CreatedUserAccessTokenDTO("mcp_1_abc", dto));

        ResponseEntity<CreatedUserAccessTokenDTO> response = resource.createAccessToken(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().getToken()).isEqualTo("mcp_1_abc");
    }

    @Test
    void shouldTranslateValidationError() {
        CreateUserAccessTokenRequestDTO request = new CreateUserAccessTokenRequestDTO();
        when(service.createCurrentUserToken(any(), any())).thenThrow(new IllegalArgumentException("请输入访问令牌名称"));

        assertThatThrownBy(() -> resource.createAccessToken(request)).isInstanceOf(BadRequestAlertException.class);
    }

    @Test
    void shouldUpdateAccessToken() {
        UpdateUserAccessTokenRequestDTO request = new UpdateUserAccessTokenRequestDTO();
        request.setStatus("DISABLED");
        UserAccessTokenDTO dto = new UserAccessTokenDTO();
        dto.setId("1");
        dto.setStatus("DISABLED");
        when(service.updateCurrentUserToken("1", null, null, "DISABLED")).thenReturn(Optional.of(dto));

        ResponseEntity<UserAccessTokenDTO> response = resource.updateAccessToken("1", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingMissingToken() {
        UpdateUserAccessTokenRequestDTO request = new UpdateUserAccessTokenRequestDTO();
        when(service.updateCurrentUserToken("1", null, null, null)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resource.updateAccessToken("1", request))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void shouldRotateAccessToken() {
        UserAccessTokenDTO dto = new UserAccessTokenDTO();
        dto.setId("1");
        when(service.rotateCurrentUserToken("1")).thenReturn(Optional.of(new CreatedUserAccessTokenDTO("mcp_1_new", dto)));

        ResponseEntity<CreatedUserAccessTokenDTO> response = resource.rotateAccessToken("1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getToken()).isEqualTo("mcp_1_new");
    }

    @Test
    void shouldReturnNotFoundWhenRotatingMissingToken() {
        when(service.rotateCurrentUserToken("1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resource.rotateAccessToken("1"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void shouldDeleteAccessToken() {
        when(service.deleteCurrentUserToken("1")).thenReturn(true);

        ResponseEntity<Void> response = resource.deleteAccessToken("1");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void shouldReturnNotFoundWhenDeletingMissingToken() {
        when(service.deleteCurrentUserToken("1")).thenReturn(false);

        assertThatThrownBy(() -> resource.deleteAccessToken("1"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
}
