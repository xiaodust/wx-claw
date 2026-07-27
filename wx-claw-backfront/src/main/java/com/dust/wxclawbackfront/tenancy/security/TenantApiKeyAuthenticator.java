package com.dust.wxclawbackfront.tenancy.security;

import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.entity.Tenant;
import com.dust.wxclawbackfront.tenancy.entity.TenantApiCredential;
import com.dust.wxclawbackfront.tenancy.repository.TenantApiCredentialRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantApiKeyAuthenticator {
    private final TenantApiCredentialRepository credentialRepository;
    private final TenantRepository tenantRepository;
    private final ApiSecretHasher secretHasher;
    private final ConcurrentMap<String, Instant> lastUsedWrites = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, CachedAuthentication> authenticationCache = new ConcurrentHashMap<>();

    @Value("${wxclaw.api.last-used-write-interval-seconds:60}")
    private long lastUsedWriteIntervalSeconds;

    @Value("${wxclaw.api.authentication-cache-ttl-seconds:30}")
    private long authenticationCacheTtlSeconds;

    public TenantContext authenticate(String apiKey) {
        int separator = apiKey == null ? -1 : apiKey.indexOf('.');
        if (separator <= 0 || separator == apiKey.length() - 1) {
            return null;
        }
        String credentialId = apiKey.substring(0, separator);
        String secret = apiKey.substring(separator + 1);
        String fingerprint = fingerprint(apiKey);
        Instant now = Instant.now();
        CachedAuthentication cached = authenticationCache.get(credentialId);
        if (isFresh(cached, now)) {
            return cached.fingerprint().equals(fingerprint) ? toContext(credentialId, cached) : null;
        }
        cached = authenticationCache.compute(credentialId, (ignored, current) -> {
            if (isFresh(current, now)) {
                return current;
            }
            return loadAuthentication(credentialId, secret, fingerprint, now);
        });
        return cached != null && cached.fingerprint().equals(fingerprint) ? toContext(credentialId, cached) : null;
    }

    private CachedAuthentication loadAuthentication(String credentialId, String secret, String fingerprint, Instant now) {
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
        if (shouldUpdateLastUsed(credentialId)) {
            credential.setLastUsedAt(LocalDateTime.now());
            credentialRepository.save(credential);
        }
        Set<String> scopes = Arrays.stream(credential.getScopes().split("[,\\s]+"))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        return new CachedAuthentication(fingerprint, tenant.getTenantId(), scopes,
                now.plusSeconds(Math.max(1, authenticationCacheTtlSeconds)));
    }

    private TenantContext toContext(String credentialId, CachedAuthentication cached) {
        return new TenantContext(cached.tenantId(), "REST", null, "api:" + credentialId, null,
                Set.of("API_CLIENT"), cached.scopes(), UUID.randomUUID().toString());
    }

    private boolean isFresh(CachedAuthentication cached, Instant now) {
        return cached != null && cached.validUntil().isAfter(now);
    }

    private String fingerprint(String apiKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(apiKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private boolean shouldUpdateLastUsed(String credentialId) {
        Instant now = Instant.now();
        Instant threshold = now.minusSeconds(Math.max(1, lastUsedWriteIntervalSeconds));
        while (true) {
            Instant previous = lastUsedWrites.putIfAbsent(credentialId, now);
            if (previous == null) {
                return true;
            }
            if (!previous.isBefore(threshold)) {
                return false;
            }
            if (lastUsedWrites.replace(credentialId, previous, now)) {
                return true;
            }
        }
    }

    private record CachedAuthentication(String fingerprint, String tenantId, Set<String> scopes, Instant validUntil) {
    }
}
