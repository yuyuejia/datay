package com.data.datafusion.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * {@link SecurityJwtConfiguration#jwtAuthenticationConverter()} 的单元测试。
 */
class SecurityJwtConfigurationTest {

    private final JwtAuthenticationConverter converter = new SecurityJwtConfiguration().jwtAuthenticationConverter();

    @Test
    void shouldMapAuthClaimFromList() {
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "HS512")
            .claim("auth", List.of("ROLE_ADMIN", "ROLE_USER"))
            .build();

        assertThat(converter.convert(jwt).getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactlyInAnyOrder(
            "ROLE_ADMIN",
            "ROLE_USER"
        );
    }

    @Test
    void shouldMapAuthClaimFromSpaceDelimitedString() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS512").claim("auth", "ROLE_ADMIN ROLE_REGION").build();

        assertThat(converter.convert(jwt).getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactlyInAnyOrder(
            "ROLE_ADMIN",
            "ROLE_REGION"
        );
    }

    @Test
    void shouldReturnEmptyAuthoritiesWhenClaimMissing() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS512").claim("sub", "admin").build();

        assertThat(converter.convert(jwt).getAuthorities()).isEmpty();
    }
}
