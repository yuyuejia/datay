package com.data.datafusion.service;

import com.data.datafusion.domain.User;
import com.data.datafusion.domain.UserAccessToken;
import com.data.datafusion.repository.UserAccessTokenRepository;
import com.data.datafusion.repository.UserRepository;
import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.service.dto.CreatedUserAccessTokenDTO;
import com.data.datafusion.service.dto.UserAccessTokenDTO;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.jhipster.security.RandomUtil;

/**
 * 用户访问令牌的创建、管理、鉴权与使用记录。
 *
 * <p>一个用户可以拥有多个命名令牌，用于不同 MCP 客户端或业务场景。令牌沿用
 * {@code mcp_<userId>_<random>} 格式；数据库同时保存 SHA-256 查找哈希与 BCrypt 校验哈希，
 * 明文令牌仅在创建或重新生成时返回一次。</p>
 */
@Service
@Transactional
public class UserAccessTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(UserAccessTokenService.class);

    /** 每个用户最多可创建的访问令牌数量。 */
    public static final int MAX_TOKENS_PER_USER = 20;

    private static final String TOKEN_PREFIX = "mcp_";

    private static final Pattern TOKEN_PATTERN = Pattern.compile("^mcp_(\\d+)_([A-Za-z0-9]+)$");

    private static final int TOKEN_HINT_LENGTH = 4;

    private static final int MAX_NAME_LENGTH = 50;

    private static final int MAX_DESCRIPTION_LENGTH = 255;

    private static final int MAX_TOKEN_GENERATE_ATTEMPTS = 5;

    /** 最近使用时间最多每 5 分钟更新一次，避免每次调用都写库。 */
    private static final int LAST_USED_UPDATE_INTERVAL_MINUTES = 5;

    private final UserRepository userRepository;

    private final UserAccessTokenRepository userAccessTokenRepository;

    private final PasswordEncoder passwordEncoder;

    public UserAccessTokenService(
        UserRepository userRepository,
        UserAccessTokenRepository userAccessTokenRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.userAccessTokenRepository = userAccessTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 列出当前用户的全部访问令牌（不含明文和哈希）。
     */
    @Transactional(readOnly = true)
    public List<UserAccessTokenDTO> listCurrentUserTokens() {
        return userAccessTokenRepository
            .findByUserIdOrderByCreateTimeDesc(currentUserId())
            .stream()
            .map(UserAccessTokenDTO::from)
            .toList();
    }

    /**
     * 为当前用户创建一个新的访问令牌。
     *
     * @param name        令牌名称/使用场景。
     * @param description 用途说明，允许为空。
     * @return 明文令牌与令牌元数据，明文仅返回一次。
     */
    public CreatedUserAccessTokenDTO createCurrentUserToken(String name, String description) {
        User user = currentUser();
        String normalizedName = normalizeName(name);
        String normalizedDescription = normalizeDescription(description);
        if (userAccessTokenRepository.countByUserId(user.getId()) >= MAX_TOKENS_PER_USER) {
            throw new IllegalArgumentException("每个用户最多只能创建 " + MAX_TOKENS_PER_USER + " 个访问令牌");
        }
        if (userAccessTokenRepository.existsByUserIdAndNameIgnoreCase(user.getId(), normalizedName)) {
            throw new IllegalArgumentException("访问令牌名称已存在");
        }

        TokenMaterial material = generateUniqueToken(user.getId());
        UserAccessToken token = new UserAccessToken();
        token.setUserId(user.getId());
        token.setName(normalizedName);
        token.setDescription(normalizedDescription);
        applyTokenMaterial(token, material);
        token.setStatus(UserAccessToken.STATUS_ENABLED);
        ZonedDateTime now = ZonedDateTime.now();
        token.setCreateTime(now);
        token.setUpdateTime(now);

        try {
            token = userAccessTokenRepository.saveAndFlush(token);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("访问令牌名称已存在或生成失败，请重试");
        }
        LOG.debug("Created access token {} for user {}", token.getId(), user.getLogin());
        return new CreatedUserAccessTokenDTO(material.plainToken(), UserAccessTokenDTO.from(token));
    }

    /**
     * 修改当前用户指定令牌的名称、描述或状态。字段为 {@code null} 表示不修改。
     */
    public Optional<UserAccessTokenDTO> updateCurrentUserToken(String id, String name, String description, String status) {
        Long userId = currentUserId();
        String normalizedName = name == null ? null : normalizeName(name);
        String normalizedDescription = description == null ? null : normalizeDescription(description);
        String normalizedStatus = status == null ? null : normalizeStatus(status);

        return userAccessTokenRepository
            .findByIdAndUserId(id, userId)
            .map(token -> {
                if (normalizedName != null && !normalizedName.equalsIgnoreCase(token.getName())) {
                    if (userAccessTokenRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, normalizedName, id)) {
                        throw new IllegalArgumentException("访问令牌名称已存在");
                    }
                    token.setName(normalizedName);
                }
                if (description != null) {
                    token.setDescription(normalizedDescription);
                }
                if (normalizedStatus != null) {
                    token.setStatus(normalizedStatus);
                }
                token.setUpdateTime(ZonedDateTime.now());
                try {
                    return UserAccessTokenDTO.from(userAccessTokenRepository.saveAndFlush(token));
                } catch (DataIntegrityViolationException e) {
                    throw new IllegalArgumentException("访问令牌名称已存在");
                }
            });
    }

    /**
     * 删除当前用户的指定令牌，删除后立即失效。
     *
     * @return 是否删除成功。
     */
    public boolean deleteCurrentUserToken(String id) {
        Long userId = currentUserId();
        return userAccessTokenRepository
            .findByIdAndUserId(id, userId)
            .map(token -> {
                userAccessTokenRepository.delete(token);
                LOG.debug("Deleted access token {} for user {}", token.getId(), userId);
                return true;
            })
            .orElse(false);
    }

    /**
     * 重新生成当前用户指定令牌的随机值，名称和描述保留，旧令牌立即失效。
     */
    public Optional<CreatedUserAccessTokenDTO> rotateCurrentUserToken(String id) {
        Long userId = currentUserId();
        return userAccessTokenRepository
            .findByIdAndUserId(id, userId)
            .map(token -> {
                TokenMaterial material = generateUniqueToken(userId);
                applyTokenMaterial(token, material);
                token.setLastUsedTime(null);
                token.setUpdateTime(ZonedDateTime.now());
                UserAccessToken saved = userAccessTokenRepository.saveAndFlush(token);
                LOG.debug("Rotated access token {} for user {}", saved.getId(), userId);
                return new CreatedUserAccessTokenDTO(material.plainToken(), UserAccessTokenDTO.from(saved));
            });
    }

    /**
     * 当前用户是否至少拥有一个访问令牌。
     */
    @Transactional(readOnly = true)
    public boolean hasAnyToken() {
        return userAccessTokenRepository.countByUserId(currentUserId()) > 0;
    }

    /**
     * 使用明文令牌鉴权并返回所属用户。
     *
     * <p>升级后旧 {@code jhi_user.mcp_token_hash} 已清空，不再回退到旧令牌；只有新表中的
     * 启用令牌可以通过鉴权。</p>
     */
    @Transactional
    public Optional<User> authenticateUser(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = TOKEN_PATTERN.matcher(plainToken);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        Long userId = Long.valueOf(matcher.group(1));
        String lookupHash = sha256Hex(plainToken);
        Optional<UserAccessToken> matched = userAccessTokenRepository
            .findByLookupHashAndStatus(lookupHash, UserAccessToken.STATUS_ENABLED)
            .filter(token -> userId.equals(token.getUserId()))
            .filter(token -> passwordEncoder.matches(plainToken, token.getTokenHash()));
        if (matched.isEmpty()) {
            return Optional.empty();
        }
        UserAccessToken token = matched.get();
        Optional<User> user = userRepository.findById(token.getUserId());
        if (user.isEmpty()) {
            return Optional.empty();
        }
        touchLastUsed(token);
        return user;
    }

    private void touchLastUsed(UserAccessToken token) {
        ZonedDateTime now = ZonedDateTime.now();
        if (
            token.getLastUsedTime() != null &&
            token.getLastUsedTime().isAfter(now.minusMinutes(LAST_USED_UPDATE_INTERVAL_MINUTES))
        ) {
            return;
        }
        userAccessTokenRepository.updateLastUsedTime(token.getId(), now);
    }

    private User currentUser() {
        return userRepository
            .findById(currentUserId())
            .orElseThrow(() -> new UsernameNotFoundException("当前用户不存在"));
    }

    private Long currentUserId() {
        return SecurityUtils
            .getCurrentUserLogin()
            .flatMap(userRepository::findOneByLogin)
            .map(User::getId)
            .orElseThrow(() -> new UsernameNotFoundException("当前用户不存在"));
    }

    private TokenMaterial generateUniqueToken(Long userId) {
        for (int attempt = 0; attempt < MAX_TOKEN_GENERATE_ATTEMPTS; attempt++) {
            String plainToken = generateToken(userId);
            String lookupHash = sha256Hex(plainToken);
            if (!userAccessTokenRepository.existsByLookupHash(lookupHash)) {
                return new TokenMaterial(plainToken, lookupHash, passwordEncoder.encode(plainToken), tokenHint(plainToken));
            }
        }
        throw new IllegalStateException("生成访问令牌失败，请重试");
    }

    private static void applyTokenMaterial(UserAccessToken token, TokenMaterial material) {
        token.setLookupHash(material.lookupHash());
        token.setTokenHash(material.tokenHash());
        token.setTokenHint(material.tokenHint());
    }

    private static String generateToken(Long userId) {
        return TOKEN_PREFIX + userId + "_" + RandomUtil.generateRandomAlphanumericString();
    }

    private static String tokenHint(String token) {
        String secret = token.substring(token.lastIndexOf('_') + 1);
        return secret.length() <= TOKEN_HINT_LENGTH ? secret : secret.substring(secret.length() - TOKEN_HINT_LENGTH);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }

    private static String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("请输入访问令牌名称");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("访问令牌名称不能超过 " + MAX_NAME_LENGTH + " 个字符");
        }
        return trimmed;
    }

    private static String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("访问令牌描述不能超过 " + MAX_DESCRIPTION_LENGTH + " 个字符");
        }
        return trimmed;
    }

    private static String normalizeStatus(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (UserAccessToken.STATUS_ENABLED.equals(normalized) || UserAccessToken.STATUS_DISABLED.equals(normalized)) {
            return normalized;
        }
        throw new IllegalArgumentException("访问令牌状态必须是 ENABLED 或 DISABLED");
    }

    private record TokenMaterial(String plainToken, String lookupHash, String tokenHash, String tokenHint) {}
}
