package com.data.datafusion.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class TenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof ClaimAccessor claimAccessor) {
                Optional<Long> tenantId = Optional.ofNullable(claimAccessor.getClaim(SecurityUtils.TENANT_ID_CLAIM));
                Optional<String> tenantCode = Optional.ofNullable(claimAccessor.getClaim(SecurityUtils.TENANT_CODE_CLAIM));
                tenantId.ifPresent(TenantContext::setTenantId);
                tenantCode.ifPresent(TenantContext::setTenantCode);
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}