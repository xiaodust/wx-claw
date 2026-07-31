package com.dust.wxclawbackfront.tenancy;

import org.springframework.stereotype.Component;

/** 对当前租户上下文执行 Scope 权限检查的轻量守卫。 */
@Component
public class TenantAccessGuard {
    /** 支持精确 Scope 和超级权限 {@code *}。 */
    public void requireScope(String scope) {
        TenantContext context = TenantContextHolder.require();
        if (!context.scopes().contains(scope) && !context.scopes().contains("*")) {
            throw new SecurityException("Missing required scope: " + scope);
        }
    }
}
