package com.data.datafusion.mcp;

import com.data.datafusion.domain.User;
import com.data.datafusion.service.UserAccessTokenService;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Service responsible for validating per-user access tokens used by MCP and data-service endpoints.
 *
 * <p>一个用户可以拥有多个命名令牌；令牌格式仍为 {@code mcp_<userId>_<random>}。具体校验与
 * 最近使用时间更新由 {@link UserAccessTokenService} 完成，以便账号设置页统一管理。</p>
 */
@Service
public class McpTokenService {

    private final UserAccessTokenService userAccessTokenService;

    public McpTokenService(UserAccessTokenService userAccessTokenService) {
        this.userAccessTokenService = userAccessTokenService;
    }

    /**
     * Authenticate a plain-text access token.
     *
     * @param token the token presented by the client.
     * @return {@code true} if the token is valid and enabled.
     */
    public boolean authenticate(String token) {
        return userAccessTokenService.authenticateUser(token).isPresent();
    }

    /**
     * Authenticate a plain-text access token and resolve its owner.
     *
     * @param token the token presented by the client.
     * @return the authenticated user, empty if the token is missing, malformed, disabled or invalid.
     */
    public Optional<User> authenticateUser(String token) {
        return userAccessTokenService.authenticateUser(token);
    }
}
