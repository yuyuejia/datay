package com.data.datafusion.config;

import com.data.datafusion.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 40)
public class TenantFilterAspect {

    private static final Logger LOG = LoggerFactory.getLogger(TenantFilterAspect.class);

    private static final String NO_TENANT_DUMMY = "-1";

    @PersistenceContext
    private EntityManager entityManager;

    @Around(
        "execution(* com.data.datafusion.service..*.*(..)) || " +
        "execution(* com.data.datafusion.repository..*.*(..))"
    )
    public Object enableTenantFilter(ProceedingJoinPoint joinPoint) throws Throwable {
        if (hasSkipTenantFilterAnnotation(joinPoint)) {
            LOG.trace("@SkipTenantFilter detected, skipping tenant filter for {}", joinPoint.getSignature());
            return joinPoint.proceed();
        }

        Long tenantId = TenantContext.getTenantId();
        String targetClassName = joinPoint.getTarget().getClass().getName();
        boolean isTenantManagement =
            targetClassName.contains("TenantRepository") || targetClassName.contains("TenantService");

        if (tenantId == null) {
            if (isTenantManagement) {
                LOG.trace("TenantContext is null, skipping filter for tenant management: {}", joinPoint.getSignature());
                return joinPoint.proceed();
            }
            LOG.debug("TenantContext is null, applying dummy tenant filter to block data access for {}", joinPoint.getSignature());
            return applyFilter(joinPoint, NO_TENANT_DUMMY);
        }

        if (isTenantManagement) {
            LOG.trace("Skipping tenant filter for tenant management: {}", joinPoint.getSignature());
            return joinPoint.proceed();
        }

        return applyFilter(joinPoint, String.valueOf(tenantId));
    }

    private boolean hasSkipTenantFilterAnnotation(ProceedingJoinPoint joinPoint) {
        if (!(joinPoint.getSignature() instanceof MethodSignature methodSignature)) {
            return false;
        }
        Method method = methodSignature.getMethod();
        if (method.isAnnotationPresent(SkipTenantFilter.class)) {
            return true;
        }
        return method.getDeclaringClass().isAnnotationPresent(SkipTenantFilter.class);
    }

    private Object applyFilter(ProceedingJoinPoint joinPoint, String tenantIdParam) throws Throwable {
        boolean filterEnabledHere = false;
        try {
            Session session = entityManager.unwrap(Session.class);
            Filter existing = session.getEnabledFilter("tenantFilter");
            if (existing == null) {
                session.enableFilter("tenantFilter").setParameter("tenantId", tenantIdParam);
                filterEnabledHere = true;
                LOG.debug("Enabled tenantFilter tenantId={} for {}", tenantIdParam, joinPoint.getSignature());
            }
        } catch (Exception e) {
            LOG.trace("Session not available yet for {}, skipping filter enable", joinPoint.getSignature());
        }

        try {
            return joinPoint.proceed();
        } finally {
            if (filterEnabledHere) {
                try {
                    Session session = entityManager.unwrap(Session.class);
                    session.disableFilter("tenantFilter");
                } catch (Exception e) {
                    LOG.trace("Could not disable tenant filter: {}", e.getMessage());
                }
            }
        }
    }
}