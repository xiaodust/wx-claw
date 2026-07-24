package com.dust.wxclawbackfront.tenancy;

public final class TenantContextHolder {

    private static final ThreadLocal<TenantContext> CONTEXT = new ThreadLocal<>();

    private TenantContextHolder() {
    }

    public static void set(TenantContext context) {
        if (context == null || context.tenantId() == null || context.tenantId().isBlank()) {
            throw new MissingTenantContextException("Tenant context must contain a tenantId");
        }
        CONTEXT.set(context);
    }

    public static TenantContext getNullable() {
        return CONTEXT.get();
    }

    public static TenantContext require() {
        TenantContext context = CONTEXT.get();
        if (context == null || context.tenantId() == null || context.tenantId().isBlank()) {
            throw new MissingTenantContextException("Tenant context is required");
        }
        return context;
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
