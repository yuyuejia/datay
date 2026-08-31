package com.data.datafusion.mcp;

import com.data.datafusion.domain.User;
import com.data.datafusion.repository.UserRepository;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import tech.jhipster.security.RandomUtil;

/**
 * Service responsible for generating and validating the per-user MCP access tokens.
 *
 * <p>The token format is {@code mcp_<userId>_<random>}. Only the BCrypt hash of the token is
 * persisted on the {@link User} entity; the plain-text value is shown to the user only once.
 */
@Service
public class McpTokenService {

    private static final String TOKEN_PREFIX = "mcp_";

    private static final Pattern TOKEN_PATTERN = Pattern.compile("^mcp_(\\d+)_([A-Za-z0-9]+)$");

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    public McpTokenService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Generate a fresh plain-text token for the given user id.
     *
     * @param userId the user id embedded in the token for fast lookup during authentication.
     * @return the plain-text token.
     */
    public static String generateToken(Long userId) {
        return TOKEN_PREFIX + userId + "_" + RandomUtil.generateRandomAlphanumericString();
    }

    /**
     * Authenticate a plain-text MCP token.
     *
     * @param token the token presented by the client.
     * @return {@code true} if the token belongs to a user and matches the stored hash.
     */
    public boolean authenticate(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        Matcher matcher = TOKEN_PATTERN.matcher(token);
        if (!matcher.matches()) {
            return false;
        }
        Long userId = Long.valueOf(matcher.group(1));
        return userRepository.findById(userId).map(User::getMcpTokenHash).filter(hash -> hash != null && !hash.isBlank()).map(hash ->
            passwordEncoder.matches(token, hash)
        ).orElse(false);
    }
}
