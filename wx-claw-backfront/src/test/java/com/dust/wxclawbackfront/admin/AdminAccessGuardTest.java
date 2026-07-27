package com.dust.wxclawbackfront.admin;

import com.dust.wxclawbackfront.admin.security.AdminAccessGuard;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminAccessGuardTest {
    private final AdminAccessGuard guard = new AdminAccessGuard();

    @AfterEach
    void clear() {
        TenantContextHolder.clear();
    }

    @Test
    void tenantAdminCannotReadAnotherTenant() {
        TenantContextHolder.set(context("tenant-a", Set.of("admin:read")));

        assertEquals("tenant-a", guard.resolveTenant(null));
        assertThrows(SecurityException.class, () -> guard.resolveTenant("tenant-b"));
    }

    @Test
    void platformAdminCanQueryAllTenants() {
        TenantContextHolder.set(context("control", Set.of("platform:admin")));

        assertNull(guard.resolveTenant(null));
        assertEquals("tenant-b", guard.resolveTenant("tenant-b"));
    }

    private TenantContext context(String tenantId, Set<String> scopes) {
        return new TenantContext(tenantId, "REST", null, "admin", null,
                Set.of("API_CLIENT"), scopes, "request");
    }
}
