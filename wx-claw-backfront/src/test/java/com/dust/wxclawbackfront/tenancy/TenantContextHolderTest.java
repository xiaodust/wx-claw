package com.dust.wxclawbackfront.tenancy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantContextHolderTest {

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void rejectsMissingContext() {
        assertThrows(MissingTenantContextException.class, TenantContextHolder::require);
    }

    @Test
    void decoratorCopiesAndClearsCompleteContext() {
        TenantContext context = new TenantContext("tenant-a", "ILINK", "bot-a", "user-a", "wx-a",
                Set.of("USER"), Set.of("message:read"), "request-a");
        TenantContextHolder.set(context);
        AtomicReference<TenantContext> observed = new AtomicReference<>();

        Runnable decorated = new TenantContextTaskDecorator().decorate(() -> observed.set(TenantContextHolder.require()));
        TenantContextHolder.clear();
        decorated.run();

        assertEquals(context, observed.get());
        assertNull(TenantContextHolder.getNullable());
    }
}
