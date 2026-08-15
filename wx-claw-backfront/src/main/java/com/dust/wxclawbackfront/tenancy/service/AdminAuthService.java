package com.dust.wxclawbackfront.tenancy.service;

import com.dust.wxclawbackfront.config.security.PasswordPolicy;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.AdminLoginResult;
import com.dust.wxclawbackfront.tenancy.entity.AdminAccount;
import com.dust.wxclawbackfront.tenancy.entity.AdminSession;
import com.dust.wxclawbackfront.tenancy.repository.AdminAccountRepository;
import com.dust.wxclawbackfront.tenancy.repository.AdminSessionRepository;
import com.dust.wxclawbackfront.tenancy.security.ApiSecretHasher;
import com.dust.wxclawbackfront.tenancy.security.PublicAuthRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 平台管理员认证：账号密码登录签发管理会话，请求经 {@link #authenticateSession(String)}
 * 恢复为带 {@code *} 权限的平台上下文（tenantId 使用 platform 哨兵值，管理接口按 Scope 鉴权）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminAuthService {

    private static final String SESSION_PREFIX = "asess_";
    private static final String PLATFORM_TENANT = "platform";
    private static final Set<String> SUPER_ADMIN_SCOPES = Set.of("*");
    private static final Set<String> ADMIN_ROLES = Set.of("PLATFORM_ADMIN");

    private final AdminAccountRepository accountRepository;
    private final AdminSessionRepository sessionRepository;
    private final ApiSecretHasher secretHasher;
    private final PublicAuthRateLimiter rateLimiter;
    private final PasswordPolicy passwordPolicy;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentMap<Long, Instant> lastUsedWrites = new ConcurrentHashMap<>();

    @Value("${wxclaw.api.session.ttl:PT168H}")
    private Duration sessionTtl;

    @Value("${wxclaw.api.session.last-used-write-interval-seconds:60}")
    private long lastUsedWriteIntervalSeconds;

    /** 未知用户名时执行等价耗时的伪哈希比对，防用户枚举。 */
    private final String dummyPasswordHash = new ApiSecretHasher().hash("dummy-admin-password");

    public AdminLoginResult login(String username, String password, String clientIp) {
        rateLimiter.checkAdminLogin(username, clientIp);

        AdminAccount account = username == null || username.isBlank()
                ? null
                : accountRepository.findByUsername(username.trim().toLowerCase()).orElse(null);
        boolean passwordOk = password != null && secretHasher.matches(password,
                account == null ? dummyPasswordHash : account.getPasswordHash());
        if (account == null || !"ACTIVE".equals(account.getStatus()) || !passwordOk) {
            throw new TenantRegistrationException("INVALID_CREDENTIALS", "用户名或密码错误",
                    HttpStatus.UNAUTHORIZED);
        }

        String rawToken = newSessionToken();
        AdminSession session = new AdminSession();
        session.setAdminAccountId(account.getId());
        session.setTokenHash(sha256Hex(rawToken));
        Duration ttl = sessionTtl == null || sessionTtl.isZero() || sessionTtl.isNegative()
                ? Duration.ofHours(168)
                : sessionTtl;
        session.setExpiresAt(LocalDateTime.now().plus(ttl));
        sessionRepository.save(session);

        account.setLastLoginAt(LocalDateTime.now());
        accountRepository.save(account);
        log.info("管理端登录成功: username={}", account.getUsername());
        return new AdminLoginResult(rawToken, session.getExpiresAt(), account.getUsername(), account.getRole());
    }

    /** 校验管理会话 token，恢复平台管理员上下文；无效返回 null。 */
    public TenantContext authenticateSession(String token) {
        if (token == null || !token.startsWith(SESSION_PREFIX)) {
            return null;
        }
        AdminSession session = sessionRepository.findByTokenHash(sha256Hex(token)).orElse(null);
        if (session == null || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        AdminAccount account = accountRepository.findById(session.getAdminAccountId()).orElse(null);
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            return null;
        }
        if (shouldUpdateLastUsed(session.getId())) {
            session.setLastUsedAt(LocalDateTime.now());
            sessionRepository.save(session);
        }
        return new TenantContext(PLATFORM_TENANT, "ADMIN", null, "admin:" + account.getUsername(),
                null, ADMIN_ROLES, SUPER_ADMIN_SCOPES, UUID.randomUUID().toString());
    }

    /** 管理员修改密码：校验旧密码后更新哈希，并吊销该管理员全部会话。 */
    @Transactional
    public void changePassword(String oldPassword, String newPassword) {
        TenantContext context = TenantContextHolder.require();
        String username = adminUsername(context);
        AdminAccount account = username == null
                ? null
                : accountRepository.findByUsername(username).orElse(null);
        boolean passwordOk = account != null && oldPassword != null
                && secretHasher.matches(oldPassword, account.getPasswordHash());
        if (account == null || !"ACTIVE".equals(account.getStatus()) || !passwordOk) {
            throw new TenantRegistrationException(
                    username == null ? "VALIDATION_ERROR" : "INVALID_CREDENTIALS",
                    username == null ? "请使用管理员账号登录后修改密码" : "当前密码不正确",
                    username == null ? HttpStatus.BAD_REQUEST : HttpStatus.UNAUTHORIZED);
        }
        String password = newPassword == null ? "" : newPassword.trim();
        passwordPolicy.validate(password);
        if (password.length() < 8 || password.length() > 64) {
            throw new TenantRegistrationException("VALIDATION_ERROR", "新密码长度需为 8-128 位",
                    HttpStatus.BAD_REQUEST);
        }
        account.setPasswordHash(secretHasher.hash(password));
        accountRepository.save(account);
        long revoked = sessionRepository.deleteByAdminAccountId(account.getId());
        log.info("管理员修改密码成功: accountId={}, 吊销会话={}", account.getId(), revoked);
    }

    private String adminUsername(TenantContext context) {
        String internalUserId = context == null ? null : context.internalUserId();
        if (internalUserId == null || !internalUserId.startsWith("admin:")) {
            return null;
        }
        return internalUserId.substring("admin:".length());
    }

    private String newSessionToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return SESSION_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private boolean shouldUpdateLastUsed(Long sessionId) {
        Instant now = Instant.now();
        Instant threshold = now.minusSeconds(Math.max(1, lastUsedWriteIntervalSeconds));
        while (true) {
            Instant previous = lastUsedWrites.putIfAbsent(sessionId, now);
            if (previous == null) {
                return true;
            }
            if (!previous.isBefore(threshold)) {
                return false;
            }
            if (lastUsedWrites.replace(sessionId, previous, now)) {
                return true;
            }
        }
    }

    @Scheduled(fixedDelayString = "${wxclaw.api.session.cleanup-ms:3600000}")
    @Transactional
    public void cleanupExpiredSessions() {
        long removed = sessionRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        if (removed > 0) {
            log.info("清理过期管理端会话: {}", removed);
        }
        Instant cutoff = Instant.now().minusSeconds(Math.max(60, lastUsedWriteIntervalSeconds * 2));
        lastUsedWrites.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }
}
