package com.data.datafusion.security;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Utility class for Spring Security.
 */
public final class SecurityUtils {

    public static final MacAlgorithm JWT_ALGORITHM = MacAlgorithm.HS512;

    public static final String AUTHORITIES_CLAIM = "auth";

    public static final String USER_ID_CLAIM = "userId";

    public static final String TENANT_ID_CLAIM = "tenantId";

    public static final String TENANT_CODE_CLAIM = "tenantCode";

    private SecurityUtils() {}

    /**
     * Get the login of the current user.
     *
     * @return the login of the current user.
     */
    public static Optional<String> getCurrentUserLogin() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        return Optional.ofNullable(extractPrincipal(securityContext.getAuthentication()));
    }

    private static String extractPrincipal(Authentication authentication) {
        if (authentication == null) {
            return null;
        } else if (authentication.getPrincipal() instanceof UserDetails springSecurityUser) {
            return springSecurityUser.getUsername();
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        } else if (authentication.getPrincipal() instanceof String s) {
            return s;
        }
        return null;
    }

    /**
     * Get the JWT of the current user.
     *
     * @return the JWT of the current user.
     */
    public static Optional<String> getCurrentUserJWT() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        return Optional.ofNullable(securityContext.getAuthentication())
            .filter(authentication -> authentication.getCredentials() instanceof String)
            .map(authentication -> (String) authentication.getCredentials());
    }

    /**
     * Get the Id of the current user.
     *
     * @return the Id of the current user.
     */
    public static Optional<Long> getCurrentUserId() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        return Optional.ofNullable(securityContext.getAuthentication())
            .filter(authentication -> authentication.getPrincipal() instanceof ClaimAccessor)
            .map(authentication -> (ClaimAccessor) authentication.getPrincipal())
            .map(principal -> principal.getClaim(USER_ID_CLAIM));
    }

    /**
     * Get the tenant id from current authentication (JWT claim).
     * Falls back to TenantContext (for scenarios where JWT wasn't the entry point).
     *
     * @return the current tenant id.
     */
    public static Optional<Long> getCurrentTenantId() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        Optional<Long> fromJwt = Optional.ofNullable(securityContext.getAuthentication())
            .filter(authentication -> authentication.getPrincipal() instanceof ClaimAccessor)
            .map(authentication -> (ClaimAccessor) authentication.getPrincipal())
            .map(principal -> principal.getClaim(TENANT_ID_CLAIM));
        if (fromJwt.isPresent()) {
            return fromJwt;
        }
        return Optional.ofNullable(TenantContext.getTenantId());
    }

    /**
     * Get the tenant code from current authentication.
     *
     * @return the current tenant code.
     */
    public static Optional<String> getCurrentTenantCode() {
        SecurityContext securityContext = SecurityContextHolder.getContext();
        return Optional.ofNullable(securityContext.getAuthentication())
            .filter(authentication -> authentication.getPrincipal() instanceof ClaimAccessor)
            .map(authentication -> (ClaimAccessor) authentication.getPrincipal())
            .map(principal -> principal.getClaim(TENANT_CODE_CLAIM));
    }

    /**
     * Check if a user is authenticated.
     *
     * @return true if the user is authenticated, false otherwise.
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && getAuthorities(authentication).noneMatch(AuthoritiesConstants.ANONYMOUS::equals);
    }

    /**
     * Checks if the current user has any of the authorities.
     *
     * @param authorities the authorities to check.
     * @return true if the current user has any of the authorities, false otherwise.
     */
    public static boolean hasCurrentUserAnyOfAuthorities(String... authorities) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return (
            authentication != null && getAuthorities(authentication).anyMatch(authority -> Arrays.asList(authorities).contains(authority))
        );
    }

    /**
     * Checks if the current user has none of the authorities.
     *
     * @param authorities the authorities to check.
     * @return true if the current user has none of the authorities, false otherwise.
     */
    public static boolean hasCurrentUserNoneOfAuthorities(String... authorities) {
        return !hasCurrentUserAnyOfAuthorities(authorities);
    }

    /**
     * Checks if the current user has a specific authority.
     *
     * @param authority the authority to check.
     * @return true if the current user has the authority, false otherwise.
     */
    public static boolean hasCurrentUserThisAuthority(String authority) {
        return hasCurrentUserAnyOfAuthorities(authority);
    }

    private static Stream<String> getAuthorities(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority);
    }
}