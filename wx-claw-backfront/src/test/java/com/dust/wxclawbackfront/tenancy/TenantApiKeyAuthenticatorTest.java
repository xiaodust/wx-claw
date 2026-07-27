package com.dust.wxclawbackfront.tenancy;

import com.dust.wxclawbackfront.tenancy.entity.Tenant;
import com.dust.wxclawbackfront.tenancy.entity.TenantApiCredential;
import com.dust.wxclawbackfront.tenancy.repository.TenantApiCredentialRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantRepository;
import com.dust.wxclawbackfront.tenancy.security.ApiSecretHasher;
import com.dust.wxclawbackfront.tenancy.security.TenantApiKeyAuthenticator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantApiKeyAuthenticatorTest {
    @Test
    void throttlesLastUsedDatabaseWritesAcrossRepeatedRequests() {
        TenantApiCredentialRepository credentials = mock(TenantApiCredentialRepository.class);
        TenantRepository tenants = mock(TenantRepository.class);
        ApiSecretHasher hasher = mock(ApiSecretHasher.class);
        TenantApiCredential credential = new TenantApiCredential();
        credential.setCredentialId("admin");
        credential.setTenantId("tenant-a");
        credential.setStatus("ACTIVE");
        credential.setScopes("admin:read");
        credential.setSecretHash("hash");
        Tenant tenant = new Tenant();
        tenant.setTenantId("tenant-a");
        tenant.setStatus("ACTIVE");
        when(credentials.findByCredentialId("admin")).thenReturn(Optional.of(credential));
        when(tenants.findByTenantId("tenant-a")).thenReturn(Optional.of(tenant));
        when(hasher.matches("secret", "hash")).thenReturn(true);
        TenantApiKeyAuthenticator authenticator = new TenantApiKeyAuthenticator(credentials, tenants, hasher);
        ReflectionTestUtils.setField(authenticator, "lastUsedWriteIntervalSeconds", 60L);
        ReflectionTestUtils.setField(authenticator, "authenticationCacheTtlSeconds", 30L);

        TenantContext first = authenticator.authenticate("admin.secret");
        TenantContext second = authenticator.authenticate("admin.secret");

        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first.requestId(), second.requestId());
        assertNull(authenticator.authenticate("admin.wrong"));

        verify(credentials, times(1)).save(credential);
        verify(credentials, times(1)).findByCredentialId("admin");
        verify(tenants, times(1)).findByTenantId("tenant-a");
        verify(hasher, times(1)).matches("secret", "hash");
    }
}
