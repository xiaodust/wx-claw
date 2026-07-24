package com.dust.wxclawbackfront.tenancy;

import org.springframework.stereotype.Component;

@Component
public class TenantAccessGuard {
    public void requireScope(String scope) {
        TenantContext context = TenantContextHolder.require();
        if (!context.scopes().contains(scope) && !context.scopes().contains("*")) {
            throw new SecurityException("Missing required scope: " + scope);
        }
    }
}
