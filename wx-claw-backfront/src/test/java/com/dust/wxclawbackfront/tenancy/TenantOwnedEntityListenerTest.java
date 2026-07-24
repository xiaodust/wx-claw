package com.dust.wxclawbackfront.tenancy;

import com.dust.wxclawbackfront.bot.dao.entity.UserProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantOwnedEntityListenerTest {

    private final TenantOwnedEntityListener listener = new TenantOwnedEntityListener();

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void assignsCurrentTenantBeforePersist() {
        TenantContextHolder.set(context("tenant-a"));
        UserProfile profile = new UserProfile();

        listener.assignTenant(profile);

        assertEquals("tenant-a", profile.getTenantId());
    }

    @Test
    void rejectsEntityOwnedByAnotherTenant() {
        TenantContextHolder.set(context("tenant-a"));
        UserProfile profile = new UserProfile();
        profile.setTenantId("tenant-b");

        assertThrows(SecurityException.class, () -> listener.assignTenant(profile));
    }

    private TenantContext context(String tenantId) {
        return new TenantContext(tenantId, "TEST", null, "tester", null,
                Set.of("TENANT_ADMIN"), Set.of("*"), "test-request");
    }
}
