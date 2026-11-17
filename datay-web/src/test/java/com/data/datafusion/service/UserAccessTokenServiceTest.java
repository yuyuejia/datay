package com.data.datafusion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.data.datafusion.domain.User;
import com.data.datafusion.domain.UserAccessToken;
import com.data.datafusion.repository.UserAccessTokenRepository;
import com.data.datafusion.repository.UserRepository;
import com.data.datafusion.service.dto.CreatedUserAccessTokenDTO;
import com.data.datafusion.service.dto.UserAccessTokenDTO;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@link UserAccessTokenService} 的单元测试。
 */
class UserAccessTokenServiceTest {

    private UserRepository userRepository;

    private UserAccessTokenRepository tokenRepository;

    private PasswordEncoder passwordEncoder;

    private UserAccessTokenService service;

    private User user;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        tokenRepository = mock(UserAccessTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new UserAccessTokenService(userRepository, tokenRepository, passwordEncoder);

        user = new User();
        user.setId(1L);
        user.setLogin("user");
        when(userRepository.findOneByLogin("user")).thenReturn(Optional.of(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user", "x"));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldCreateTokenAndOnlyExposePlainTextOnce() {
        when(tokenRepository.countByUserId(1L)).thenReturn(0L);
        when(tokenRepository.existsByUserIdAndNameIgnoreCase(1L, "Claude Desktop")).thenReturn(false);
        when(tokenRepository.existsByLookupHash(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("bcrypt-hash");
        when(tokenRepository.saveAndFlush(any(UserAccessToken.class))).thenAnswer(invocation -> {
            UserAccessToken token = invocation.getArgument(0);
            token.setId("token-1");
            return token;
        });

        CreatedUserAccessTokenDTO result = service.createCurrentUserToken(" Claude Desktop ", " 本地客户端 ");

        assertThat(result.getToken()).startsWith("mcp_1_");
        assertThat(result.getAccessToken().getName()).isEqualTo("Claude Desktop");
        assertThat(result.getAccessToken().getDescription()).isEqualTo("本地客户端");
        assertThat(result.getAccessToken().getStatus()).isEqualTo(UserAccessToken.STATUS_ENABLED);
        assertThat(result.getAccessToken().getTokenHint()).hasSize(4);
        assertThat(result.getAccessToken().getId()).isEqualTo("token-1");

        verify(tokenRepository).saveAndFlush(any(UserAccessToken.class));
        verify(passwordEncoder).encode(result.getToken());
    }

    @Test
    void shouldRejectDuplicateName() {
        when(tokenRepository.countByUserId(1L)).thenReturn(0L);
        when(tokenRepository.existsByUserIdAndNameIgnoreCase(1L, "Claude")).thenReturn(true);

        assertThatThrownBy(() -> service.createCurrentUserToken("Claude", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("名称已存在");
        verify(tokenRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectWhenTokenLimitReached() {
        when(tokenRepository.countByUserId(1L)).thenReturn((long) UserAccessTokenService.MAX_TOKENS_PER_USER);

        assertThatThrownBy(() -> service.createCurrentUserToken("Claude", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("最多只能创建");
        verify(tokenRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldListCurrentUserTokens() {
        UserAccessToken token = token("token-1", "Claude", UserAccessToken.STATUS_ENABLED);
        when(tokenRepository.findByUserIdOrderByCreateTimeDesc(1L)).thenReturn(List.of(token));

        List<UserAccessTokenDTO> result = service.listCurrentUserTokens();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Claude");
    }

    @Test
    void shouldUpdateTokenMetadataAndStatus() {
        UserAccessToken token = token("token-1", "Claude", UserAccessToken.STATUS_ENABLED);
        when(tokenRepository.findByIdAndUserId("token-1", 1L)).thenReturn(Optional.of(token));
        when(tokenRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(1L, "Cursor", "token-1")).thenReturn(false);
        when(tokenRepository.saveAndFlush(any(UserAccessToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<UserAccessTokenDTO> result = service.updateCurrentUserToken("token-1", "Cursor", "开发环境", "DISABLED");

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Cursor");
        assertThat(result.get().getDescription()).isEqualTo("开发环境");
        assertThat(result.get().getStatus()).isEqualTo(UserAccessToken.STATUS_DISABLED);
    }

    @Test
    void shouldNotUpdateAnotherUsersToken() {
        when(tokenRepository.findByIdAndUserId("token-1", 1L)).thenReturn(Optional.empty());

        assertThat(service.updateCurrentUserToken("token-1", "Cursor", null, null)).isEmpty();
    }

    @Test
    void shouldDeleteOwnToken() {
        UserAccessToken token = token("token-1", "Claude", UserAccessToken.STATUS_ENABLED);
        when(tokenRepository.findByIdAndUserId("token-1", 1L)).thenReturn(Optional.of(token));

        assertThat(service.deleteCurrentUserToken("token-1")).isTrue();
        verify(tokenRepository).delete(token);
    }

    @Test
    void shouldNotDeleteAnotherUsersToken() {
        when(tokenRepository.findByIdAndUserId("token-1", 1L)).thenReturn(Optional.empty());

        assertThat(service.deleteCurrentUserToken("token-1")).isFalse();
        verify(tokenRepository, never()).delete(any());
    }

    @Test
    void shouldRotateTokenAndClearLastUsedTime() {
        UserAccessToken token = token("token-1", "Claude", UserAccessToken.STATUS_ENABLED);
        token.setLastUsedTime(ZonedDateTime.now());
        when(tokenRepository.findByIdAndUserId("token-1", 1L)).thenReturn(Optional.of(token));
        when(tokenRepository.existsByLookupHash(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("new-bcrypt-hash");
        when(tokenRepository.saveAndFlush(any(UserAccessToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<CreatedUserAccessTokenDTO> result = service.rotateCurrentUserToken("token-1");

        assertThat(result).isPresent();
        assertThat(result.get().getToken()).startsWith("mcp_1_");
        assertThat(result.get().getAccessToken().getLastUsedTime()).isNull();
        assertThat(token.getTokenHash()).isEqualTo("new-bcrypt-hash");
    }

    @Test
    void shouldAuthenticateEnabledToken() {
        UserAccessToken token = token("token-1", "Claude", UserAccessToken.STATUS_ENABLED);
        when(tokenRepository.findByLookupHashAndStatus(anyString(), eq(UserAccessToken.STATUS_ENABLED))).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("mcp_1_abc123", "hash")).thenReturn(true);

        Optional<User> result = service.authenticateUser("mcp_1_abc123");

        assertThat(result).contains(user);
        verify(tokenRepository).updateLastUsedTime(eq("token-1"), any(ZonedDateTime.class));
    }

    @Test
    void shouldRejectTokenWhenPasswordDoesNotMatch() {
        UserAccessToken token = token("token-1", "Claude", UserAccessToken.STATUS_ENABLED);
        when(tokenRepository.findByLookupHashAndStatus(anyString(), eq(UserAccessToken.STATUS_ENABLED))).thenReturn(Optional.of(token));
        when(passwordEncoder.matches("mcp_1_abc123", "hash")).thenReturn(false);

        assertThat(service.authenticateUser("mcp_1_abc123")).isEmpty();
        verify(tokenRepository, never()).updateLastUsedTime(anyString(), any());
    }

    @Test
    void shouldRejectTokenWhenRecordMissingOrUserMissing() {
        assertThat(service.authenticateUser("mcp_1_abc123")).isEmpty();
        assertThat(service.authenticateUser("not-a-token")).isEmpty();
        assertThat(service.authenticateUser("")).isEmpty();
    }

    @Test
    void shouldReportWhetherUserHasTokens() {
        when(tokenRepository.countByUserId(1L)).thenReturn(2L);

        assertThat(service.hasAnyToken()).isTrue();
    }

    private static UserAccessToken token(String id, String name, String status) {
        UserAccessToken token = new UserAccessToken();
        token.setId(id);
        token.setUserId(1L);
        token.setName(name);
        token.setLookupHash("lookup");
        token.setTokenHash("hash");
        token.setStatus(status);
        token.setLastUsedTime(null);
        token.setCreateTime(ZonedDateTime.now());
        return token;
    }
}
