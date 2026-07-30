package com.dust.wxclawbackfront.admin.security;

import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AdminAccessGuard {
    public TenantContext requireRead() {
        TenantContext context = TenantContextHolder.require();
        if (!context.scopes().contains("admin:read")
                && !context.scopes().contains("platform:admin")
                && !context.scopes().contains("*")) {
            throw new SecurityException("Missing required scope: admin:read");
        }
        return context;
    }

    public boolean isPlatformAdmin() {
        TenantContext context = requireRead();
        return context.scopes().contains("platform:admin") || context.scopes().contains("*");
    }

    public TenantContext requireWrite() {
        TenantContext context = TenantContextHolder.require();
        if (!context.scopes().contains("admin:write")
                && !context.scopes().contains("platform:admin")
                && !context.scopes().contains("*")) {
            throw new SecurityException("Missing required scope: admin:write");
        }
        return context;
    }

    public String resolveWriteTenant(String requestedTenantId) {
        TenantContext context = requireWrite();
        String requested = normalize(requestedTenantId);
        if (context.scopes().contains("platform:admin") || context.scopes().contains("*")) {
            return requested == null ? context.tenantId() : requested;
        }
        if (requested != null && !requested.equals(context.tenantId())) {
            throw new SecurityException("Cannot write another tenant");
        }
        return context.tenantId();
    }

    public String resolveTenant(String requestedTenantId) {
        TenantContext context = requireRead();
        if (isPlatformAdmin()) {
            return normalize(requestedTenantId);
        }
        String requested = normalize(requestedTenantId);
        if (requested != null && !requested.equals(context.tenantId())) {
            throw new SecurityException("Cannot access another tenant");
        }
        return context.tenantId();
    }

    public String resolveOwnedTenant(String requestedTenantId) {
        TenantContext context = requireRead();
        String requested = normalize(requestedTenantId);
        if (context.scopes().contains("platform:admin") || context.scopes().contains("*")) {
            return requested == null ? context.tenantId() : requested;
        }
        if (requested != null && !requested.equals(context.tenantId())) {
            throw new SecurityException("Cannot access another tenant");
        }
        return context.tenantId();
    }

    public void ensureTenant(String tenantId) {
        TenantContext context = requireRead();
        if (!isPlatformAdmin() && !context.tenantId().equals(tenantId)) {
            throw new SecurityException("Cannot access another tenant");
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
