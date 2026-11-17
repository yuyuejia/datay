package com.data.datafusion.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.User;
import com.data.datafusion.service.UserAccessTokenService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@link McpTokenService} 委托鉴权的单元测试。
 */
class McpTokenServiceTest {

    private UserAccessTokenService userAccessTokenService;

    private McpTokenService service;

    @BeforeEach
    void setUp() {
        userAccessTokenService = mock(UserAccessTokenService.class);
        service = new McpTokenService(userAccessTokenService);
    }

    @Test
    void shouldAuthenticateThroughAccessTokenService() {
        User user = new User();
        user.setId(1L);
        when(userAccessTokenService.authenticateUser("mcp_1_abc")).thenReturn(Optional.of(user));

        assertThat(service.authenticate("mcp_1_abc")).isTrue();
        assertThat(service.authenticateUser("mcp_1_abc")).contains(user);
    }

    @Test
    void shouldRejectInvalidToken() {
        when(userAccessTokenService.authenticateUser("bad")).thenReturn(Optional.empty());

        assertThat(service.authenticate("bad")).isFalse();
        assertThat(service.authenticateUser("bad")).isEmpty();
    }
}
