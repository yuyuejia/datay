package com.data.datafusion.config;

import static org.springframework.security.config.Customizer.withDefaults;
import static org.springframework.security.web.util.matcher.AntPathRequestMatcher.antMatcher;

import com.data.datafusion.security.*;
import com.data.datafusion.web.filter.SpaWebFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import tech.jhipster.config.JHipsterConstants;
import tech.jhipster.config.JHipsterProperties;

@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfiguration {

    private final Environment env;

    private final JHipsterProperties jHipsterProperties;

    private final TenantFilter tenantFilter;

    /**
     * 允许通过 iframe 嵌入本站的父页面来源。默认 {@code *} 表示允许所有来源。
     * 生产环境可通过 {@code datay.embed.allowed-origins} 收紧为具体域名。
     */
    @Value("${datay.embed.allowed-origins:*}")
    private String embedAllowedOrigins;

    public SecurityConfiguration(Environment env, JHipsterProperties jHipsterProperties, TenantFilter tenantFilter) {
        this.env = env;
        this.jHipsterProperties = jHipsterProperties;
        this.tenantFilter = tenantFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Dedicated security filter chain for the MCP endpoint.
     *
     * <p>The MCP server authenticates clients with a per-user token (validated at the transport
     * layer via {@code Authorization: Bearer <mcp-token>}). That token is not a JWT, so this chain
     * must not apply the OAuth2 resource-server (JWT) filter, otherwise it would reject the MCP
     * token as a malformed JWT before it ever reaches the MCP servlet.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain mcpFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/mcp", "/mcp/**")
            .cors(withDefaults())
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(FrameOptionsConfig::sameOrigin))
            .authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    /**
     * Dedicated security filter chain for the public data-service API.
     *
     * <p>The data-service endpoints ({@code /open-api/**}) are invoked by third-party systems and
     * are authenticated with the per-user MCP token (validated inside the controller/service via
     * {@link com.data.datafusion.mcp.McpTokenService}), not with a JWT. Therefore this chain must
     * bypass the OAuth2 resource-server filter applied to {@code /api/**}.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain openApiFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/open-api/**")
            .cors(withDefaults())
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(FrameOptionsConfig::sameOrigin))
            .authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, MvcRequestMatcher.Builder mvc) throws Exception {
        http
            .cors(withDefaults())
            .csrf(csrf -> csrf.disable())
            .addFilterAfter(new SpaWebFilter(), BasicAuthenticationFilter.class)
            .headers(headers ->
                headers
                    .contentSecurityPolicy(csp ->
                        csp.policyDirectives(
                            withFrameAncestors(jHipsterProperties.getSecurity().getContentSecurityPolicy(), embedAllowedOrigins)
                        )
                    )
                    // 允许被 iframe 嵌入：现代浏览器以 CSP frame-ancestors 为准，
                    // 去掉 X-Frame-Options 才能支持跨域父页面。
                    .frameOptions(FrameOptionsConfig::disable)
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .permissionsPolicyHeader(permissions ->
                        permissions.policy(
                            "camera=(), fullscreen=(self), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), sync-xhr=()"
                        )
                    )
            )
            .authorizeHttpRequests(authz ->
                // prettier-ignore
                authz
                    .requestMatchers(mvc.pattern("/index.html"), mvc.pattern("/dashboard.html"), mvc.pattern("/embed-demo.html"), mvc.pattern("/*.js"), mvc.pattern("/*.txt"), mvc.pattern("/*.json"), mvc.pattern("/*.map"), mvc.pattern("/*.css")).permitAll()
                    // 静态图片（含 svg/png 等）需放行，否则未认证时图标请求会被拦截而显示 404
                    .requestMatchers(mvc.pattern("/*.ico"), mvc.pattern("/*.png"), mvc.pattern("/content/images/**"), mvc.pattern("/*.svg"), mvc.pattern("/*.webapp")).permitAll()
                    .requestMatchers(mvc.pattern("/assets/**")).permitAll()
                    .requestMatchers(mvc.pattern("/swagger-ui/**")).permitAll()
                    .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/authenticate")).permitAll()
                    .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/authenticate")).permitAll()
                    .requestMatchers(mvc.pattern("/api/register")).permitAll()
                    .requestMatchers(mvc.pattern("/api/activate")).permitAll()
                    .requestMatchers(mvc.pattern("/api/account/reset-password/init")).permitAll()
                    .requestMatchers(mvc.pattern("/api/account/reset-password/finish")).permitAll()
                    .requestMatchers(mvc.pattern("/api/admin/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern("/api/**")).authenticated()
                    .requestMatchers(mvc.pattern("/v3/api-docs/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern("/management/health")).permitAll()
                    .requestMatchers(mvc.pattern("/management/health/**")).permitAll()
                    .requestMatchers(mvc.pattern("/management/info")).permitAll()
                    .requestMatchers(mvc.pattern("/management/prometheus")).permitAll()
                    .requestMatchers(mvc.pattern("/management/**")).hasAuthority(AuthoritiesConstants.ADMIN)
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions ->
                exceptions
                    .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                    .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
            .addFilterAfter(tenantFilter, org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter.class);
        if (env.acceptsProfiles(Profiles.of(JHipsterConstants.SPRING_PROFILE_DEVELOPMENT))) {
            http.authorizeHttpRequests(authz -> authz.requestMatchers(antMatcher("/h2-console/**")).permitAll());
        }
        return http.build();
    }

    /**
     * 在现有 CSP 后追加 {@code frame-ancestors}，用于控制哪些父页面可以通过 iframe 嵌入本站。
     *
     * <p>如果 CSP 中已经声明了 {@code frame-ancestors}，则保持原样，避免重复指令。
     */
    static String withFrameAncestors(String contentSecurityPolicy, String allowedOrigins) {
        String base = contentSecurityPolicy == null ? "" : contentSecurityPolicy.trim();
        if (base.contains("frame-ancestors")) {
            return base;
        }
        String origins = allowedOrigins == null || allowedOrigins.isBlank() ? "*" : allowedOrigins.trim();
        String directive = "frame-ancestors " + origins;
        return base.isEmpty() ? directive : base + "; " + directive;
    }

    @Bean
    MvcRequestMatcher.Builder mvc(HandlerMappingIntrospector introspector) {
        return new MvcRequestMatcher.Builder(introspector);
    }
}