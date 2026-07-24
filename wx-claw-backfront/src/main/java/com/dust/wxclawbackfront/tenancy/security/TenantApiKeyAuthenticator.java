package com.dust.wxclawbackfront.tenancy.security;

import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.entity.Tenant;
import com.dust.wxclawbackfront.tenancy.entity.TenantApiCredential;
import com.dust.wxclawbackfront.tenancy.repository.TenantApiCredentialRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantApiKeyAuthenticator {
    private final TenantApiCredentialRepository credentialRepository;
    private final TenantRepository tenantRepository;
    private final ApiSecretHasher secretHasher;

    @Transactional
    public TenantContext authenticate(String apiKey) {
        int separator = apiKey == null ? -1 : apiKey.indexOf('.');
        if (separator <= 0 || separator == apiKey.length() - 1) {
            return null;
        }
        String credentialId = apiKey.substring(0, separator);
        String secret = apiKey.substring(separator + 1);
        TenantApiCredential credential = credentialRepository.findByCredentialId(credentialId).orElse(null);
        if (credential == null || !"ACTIVE".equals(credential.getStatus())
                || credential.getExpiresAt() != null && credential.getExpiresAt().isBefore(LocalDateTime.now())
                || !secretHasher.matches(secret, credential.getSecretHash())) {
            return null;
        }
        Tenant tenant = tenantRepository.findByTenantId(credential.getTenantId()).orElse(null);
        if (tenant == null || !"ACTIVE".equals(tenant.getStatus())) {
            return null;
        }
        credential.setLastUsedAt(LocalDateTime.now());
        credentialRepository.save(credential);
        Set<String> scopes = Arrays.stream(credential.getScopes().split("[,\\s]+"))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        return new TenantContext(tenant.getTenantId(), "REST", null, "api:" + credentialId, null,
                Set.of("API_CLIENT"), scopes, UUID.randomUUID().toString());
    }
}
