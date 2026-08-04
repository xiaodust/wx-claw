package com.dust.wxclawbackfront.tenancy.security;

import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.entity.Tenant;
import com.dust.wxclawbackfront.tenancy.entity.TenantApiCredential;
import com.dust.wxclawbackfront.tenancy.repository.TenantApiCredentialRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
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

/**
 * 将请求头中的 API Key 认证为 {@link TenantContext}。
 *
 * <p>Key 格式为 {@code credentialId.secret}。credentialId 用于定位凭据，secret 仅参与
 * PBKDF2 校验，绝不写入日志或缓存；短期缓存保存完整 Key 的 SHA-256 指纹、租户和 Scope，
 * 用于减少高频请求对数据库及 PBKDF2 的压力。</p>
 */
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
        // 只按第一个点分隔，允许 secret 本身包含点号。
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
            // 同一 credentialId 提交不同 secret 时不能复用已有认证结果。
            return cached.fingerprint().equals(fingerprint) ? toContext(credentialId, cached) : null;
        }
        // compute 保证同一 credentialId 缓存失效时只有一个线程访问数据库并执行 PBKDF2。
        cached = authenticationCache.compute(credentialId, (ignored, current) -> {
            if (isFresh(current, now)) {
                return current;
            }
            return loadAuthentication(credentialId, secret, fingerprint, now);
        });
        return cached != null && cached.fingerprint().equals(fingerprint) ? toContext(credentialId, cached) : null;
    }

    private CachedAuthentication loadAuthentication(String credentialId, String secret, String fingerprint, Instant now) {
        // 凭据和租户必须同时有效；停用任意一方都会拒绝认证。
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
            // 限频更新 last_used_at，避免每个 API 请求都产生数据库写入。
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
        // 每次请求生成独立 requestId，缓存只复用认证结论，不复用请求级上下文对象。
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

    /**
     * 定时清理认证缓存与 last_used 写入标记，防止停用/删除的凭证条目永久驻留。
     */
    @Scheduled(fixedDelayString = "${wxclaw.api.authentication-cache-cleanup-ms:600000}")
    public void cleanupCaches() {
        Instant now = Instant.now();
        authenticationCache.entrySet().removeIf(entry -> !entry.getValue().validUntil().isAfter(now));
        long maxAgeSeconds = Math.max(60, lastUsedWriteIntervalSeconds * 2);
        Instant lastUsedCutoff = now.minusSeconds(maxAgeSeconds);
        lastUsedWrites.entrySet().removeIf(entry -> entry.getValue().isBefore(lastUsedCutoff));
    }

    private record CachedAuthentication(String fingerprint, String tenantId, Set<String> scopes, Instant validUntil) {
    }
}
