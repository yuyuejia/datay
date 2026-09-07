package com.data.datafusion.web.rest;

import static com.data.datafusion.security.SecurityUtils.AUTHORITIES_CLAIM;
import static com.data.datafusion.security.SecurityUtils.JWT_ALGORITHM;
import static com.data.datafusion.security.SecurityUtils.TENANT_CODE_CLAIM;
import static com.data.datafusion.security.SecurityUtils.TENANT_ID_CLAIM;
import static com.data.datafusion.security.SecurityUtils.USER_ID_CLAIM;

import com.data.datafusion.domain.Tenant;
import com.data.datafusion.repository.TenantRepository;
import com.data.datafusion.security.DomainUserDetailsService.UserWithId;
import com.data.datafusion.security.SecurityUtils;
import com.data.datafusion.service.TenantService;
import com.data.datafusion.service.dto.TenantDTO;
import com.data.datafusion.web.rest.vm.LoginVM;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.*;

/**
 * Controller to authenticate users.
 */
@RestController
@RequestMapping("/api")
public class AuthenticateController {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticateController.class);

    private final JwtEncoder jwtEncoder;

    @Value("${jhipster.security.authentication.jwt.token-validity-in-seconds:0}")
    private long tokenValidityInSeconds;

    @Value("${jhipster.security.authentication.jwt.token-validity-in-seconds-for-remember-me:0}")
    private long tokenValidityInSecondsForRememberMe;

    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    private final TenantRepository tenantRepository;

    private final TenantService tenantService;

    public AuthenticateController(
        JwtEncoder jwtEncoder,
        AuthenticationManagerBuilder authenticationManagerBuilder,
        TenantRepository tenantRepository,
        TenantService tenantService
    ) {
        this.jwtEncoder = jwtEncoder;
        this.authenticationManagerBuilder = authenticationManagerBuilder;
        this.tenantRepository = tenantRepository;
        this.tenantService = tenantService;
    }

    @PostMapping("/authenticate")
    public ResponseEntity<JWTToken> authorize(@Valid @RequestBody LoginVM loginVM) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
            loginVM.getUsername(),
            loginVM.getPassword()
        );

        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = this.createToken(authentication, loginVM.isRememberMe(), null);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setBearerAuth(jwt);
        return new ResponseEntity<>(new JWTToken(jwt), httpHeaders, HttpStatus.OK);
    }

    @GetMapping("/authenticate")
    public ResponseEntity<Void> isAuthenticated(Principal principal) {
        LOG.debug("REST request to check if the current user is authenticated");
        return ResponseEntity.status(principal == null ? HttpStatus.UNAUTHORIZED : HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/tenants/my-tenants")
    public ResponseEntity<List<TenantDTO>> getMyTenants() {
        LOG.debug("REST request to get current user's tenants");
        return ResponseEntity.ok(tenantService.findAllByCurrentUser());
    }

    @PostMapping("/authenticate/switch-tenant")
    public ResponseEntity<JWTToken> switchTenant(@RequestBody SwitchTenantVM switchVM) {
        Long userId = SecurityUtils.getCurrentUserId().orElse(null);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<Tenant> userTenants = tenantRepository.findAllByUserId(userId);
        boolean belongs = userTenants.stream().anyMatch(t -> t.getId().equals(switchVM.tenantId));
        if (!belongs) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        tenantRepository.resetDefaultForUser(userId);
        tenantRepository.setDefaultTenantForUser(userId, switchVM.tenantId);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String jwt = this.createToken(authentication, true, switchVM.tenantId);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setBearerAuth(jwt);
        return new ResponseEntity<>(new JWTToken(jwt), httpHeaders, HttpStatus.OK);
    }

    public String createToken(Authentication authentication, boolean rememberMe, Long forceTenantId) {
        String authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.joining(" "));

        Instant now = Instant.now();
        Instant validity;
        if (rememberMe) {
            validity = now.plus(this.tokenValidityInSecondsForRememberMe, ChronoUnit.SECONDS);
        } else {
            validity = now.plus(this.tokenValidityInSeconds, ChronoUnit.SECONDS);
        }

        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
            .issuedAt(now)
            .expiresAt(validity)
            .subject(authentication.getName())
            .claim(AUTHORITIES_CLAIM, authorities);

        Long userId = null;
        if (authentication.getPrincipal() instanceof UserWithId user) {
            userId = user.getId();
        } else if (authentication.getPrincipal() instanceof ClaimAccessor claimAccessor) {
            Object raw = claimAccessor.getClaim(USER_ID_CLAIM);
            if (raw instanceof Number n) {
                userId = n.longValue();
            }
        }
        if (userId != null) {
            builder.claim(USER_ID_CLAIM, userId);
        }

        Long tenantIdForClaim = forceTenantId;
        if (tenantIdForClaim == null && userId != null) {
            tenantIdForClaim = tenantRepository.findDefaultTenantByUserId(userId).map(Tenant::getId).orElse(null);
        }
        if (tenantIdForClaim != null) {
            final Long finalTenantId = tenantIdForClaim;
            tenantRepository.findById(finalTenantId).ifPresent(tenant -> {
                builder.claim(TENANT_ID_CLAIM, tenant.getId());
                builder.claim(TENANT_CODE_CLAIM, tenant.getCode());
            });
        }

        JwsHeader jwsHeader = JwsHeader.with(JWT_ALGORITHM).build();
        return this.jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, builder.build())).getTokenValue();
    }

    static class SwitchTenantVM {

        public Long tenantId;
    }

    /**
     * Object to return as body in JWT Authentication.
     */
    static class JWTToken {

        private String idToken;

        JWTToken(String idToken) {
            this.idToken = idToken;
        }

        @JsonProperty("id_token")
        String getIdToken() {
            return idToken;
        }

        void setIdToken(String idToken) {
            this.idToken = idToken;
        }
    }
}