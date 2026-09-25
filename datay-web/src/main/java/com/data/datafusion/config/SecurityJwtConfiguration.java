package com.data.datafusion.config;

import static com.data.datafusion.security.SecurityUtils.AUTHORITIES_CLAIM;
import static com.data.datafusion.security.SecurityUtils.JWT_ALGORITHM;

import com.data.datafusion.management.SecurityMetersService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.util.Base64;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

@Configuration
public class SecurityJwtConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityJwtConfiguration.class);

    @Value("${jhipster.security.authentication.jwt.base64-secret}")
    private String jwtKey;

    @Bean
    public JwtDecoder jwtDecoder(SecurityMetersService metersService) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(getSecretKey()).macAlgorithm(JWT_ALGORITHM).build();
        return token -> {
            try {
                return jwtDecoder.decode(token);
            } catch (Exception e) {
                if (e.getMessage().contains("Invalid signature")) {
                    metersService.trackTokenInvalidSignature();
                } else if (e.getMessage().contains("Jwt expired at")) {
                    metersService.trackTokenExpired();
                } else if (
                    e.getMessage().contains("Invalid JWT serialization") ||
                    e.getMessage().contains("Malformed token") ||
                    e.getMessage().contains("Invalid unsecured/JWS/JWE")
                ) {
                    metersService.trackTokenMalformed();
                } else {
                    LOG.error("Unknown JWT error {}", e.getMessage());
                }
                throw e;
            }
        };
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(getSecretKey()));
    }

    /**
     * 将 JWT 的 {@code auth} 声明映射为 Spring Security 权限。
     * <p>
     * 资源服务器默认仅解析 {@code scope}/{@code scp}，会导致 {@code hasAuthority}/{@code @PreAuthorize}
     * 以及基于角色的数据权限（{@link com.data.datafusion.service.RoleDataScopeService}）无法生效，
     * 因此显式配置 {@code auth} 声明转换器，并兼容列表与空格分隔字符串两种格式。
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Object raw = jwt.getClaim(AUTHORITIES_CLAIM);
            List<String> authorities = new ArrayList<>();
            if (raw instanceof Collection<?> collection) {
                collection.forEach(value -> {
                    if (value != null && !String.valueOf(value).isBlank()) {
                        authorities.add(String.valueOf(value));
                    }
                });
            } else if (raw instanceof String text && !text.isBlank()) {
                Collections.addAll(authorities, text.trim().split("\\s+"));
            }
            return authorities.stream().map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList();
        });
        return converter;
    }

    private SecretKey getSecretKey() {
        byte[] keyBytes = Base64.from(jwtKey).decode();
        return new SecretKeySpec(keyBytes, 0, keyBytes.length, JWT_ALGORITHM.getName());
    }
}
