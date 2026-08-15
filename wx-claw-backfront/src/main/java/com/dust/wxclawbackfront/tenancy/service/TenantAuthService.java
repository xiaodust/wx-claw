package com.dust.wxclawbackfront.tenancy.service;

import com.dust.wxclawbackfront.config.security.PasswordPolicy;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.AuthResult;
import com.dust.wxclawbackfront.tenancy.entity.Tenant;
import com.dust.wxclawbackfront.tenancy.entity.TenantAccount;
import com.dust.wxclawbackfront.tenancy.entity.TenantSession;
import com.dust.wxclawbackfront.tenancy.repository.TenantAccountRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantSessionRepository;
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
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * 控制台账号认证：注册时创建账号并签发会话，登录时校验密码并签发会话，
 * 每个请求通过 {@link #authenticateSession(String)} 恢复租户上下文。
 *
 * <p>安全约束：密码与 API Key 同样使用 PBKDF2 哈希落库；会话 token 为 256 位随机数，
 * 数据库只保存其 SHA-256 哈希；用户名不存在与密码错误返回同一提示，并执行一次
 * 伪哈希比对以抹平时序差异；登录接口有独立限流。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantAuthService {

    private static final String SESSION_PREFIX = "sess_";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_-]{3,32}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Set<String> CONSOLE_ROLES = Set.of("TENANT_ADMIN");
    private static final Set<String> CONSOLE_SCOPES = Set.of(
            "userbot:read", "userbot:write", "conversation:read", "aiconfig:read", "aiconfig:write",
            "account:read", "account:write");

    private final TenantAccountRepository accountRepository;
    private final TenantSessionRepository sessionRepository;
    private final TenantRepository tenantRepository;
    private final ApiSecretHasher secretHasher;
    private final PublicAuthRateLimiter rateLimiter;
    private final EmailVerificationService emailVerificationService;
    private final PasswordPolicy passwordPolicy;

    private final SecureRandom secureRandom = new SecureRandom();
    private final ConcurrentMap<Long, Instant> lastUsedWrites = new ConcurrentHashMap<>();

    @Value("${wxclaw.api.session.ttl:PT168H}")
    private Duration sessionTtl;

    @Value("${wxclaw.api.session.last-used-write-interval-seconds:60}")
    private long lastUsedWriteIntervalSeconds;

    /** 用于未知用户名时执行等价耗时的伪哈希校验，避免用户枚举（时序侧信道）。 */
    private final String dummyPasswordHash = new ApiSecretHasher().hash("dummy-password");

    public AuthResult login(String username, String password, String clientIp) {
        rateLimiter.checkLogin(username, clientIp);

        TenantAccount account = username == null || username.isBlank()
                ? null
                : accountRepository.findByUsername(username.trim().toLowerCase()).orElse(null);
        boolean passwordOk = password != null && secretHasher.matches(password,
                account == null ? dummyPasswordHash : account.getPasswordHash());
        boolean usable = account != null && "ACTIVE".equals(account.getStatus()) && passwordOk;
        if (!usable) {
            throw new TenantRegistrationException("INVALID_CREDENTIALS", "用户名或密码错误",
                    HttpStatus.UNAUTHORIZED);
        }

        Tenant tenant = tenantRepository.findByTenantId(account.getTenantId()).orElse(null);
        if (tenant == null || !"ACTIVE".equals(tenant.getStatus())) {
            throw new TenantRegistrationException("INVALID_CREDENTIALS", "账号不可用",
                    HttpStatus.UNAUTHORIZED);
        }

        String rawToken = newSessionToken();
        TenantSession session = saveSession(tenant.getTenantId(), account, rawToken);
        account.setLastLoginAt(LocalDateTime.now());
        accountRepository.save(account);
        log.info("控制台登录成功: tenantId={}, username={}", tenant.getTenantId(), account.getUsername());
        return new AuthResult(rawToken, session.getExpiresAt(),
                tenant.getTenantId(), tenant.getTenantCode(), tenant.getTenantName());
    }

    /** 注册时创建账号并直接签发会话（注册成功即可一键进入控制台）。 */
    public AccountIssue createAccountAndIssueSession(String tenantId, String username, String password,
                                                     String contactEmail) {
        String rawToken = newSessionToken();
        return withContext(tenantId, () -> {
            TenantAccount account = new TenantAccount();
            account.setUsername(username);
            account.setPasswordHash(secretHasher.hash(password));
            account.setContactEmail(contactEmail);
            account.setStatus("ACTIVE");
            TenantAccount saved = accountRepository.save(account);
            TenantSession session = saveSession(tenantId, saved, rawToken);
            return new AccountIssue(saved.getUsername(), rawToken, session.getExpiresAt());
        });
    }

    /** 校验会话 token，恢复租户上下文；无效返回 null（由过滤器统一回 401）。 */
    public TenantContext authenticateSession(String token) {
        if (token == null || !token.startsWith(SESSION_PREFIX)) {
            return null;
        }
        TenantSession session = sessionRepository.findByTokenHash(sha256Hex(token)).orElse(null);
        if (session == null || session.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        TenantAccount account = accountRepository.findById(session.getAccountId()).orElse(null);
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            return null;
        }
        Tenant tenant = tenantRepository.findByTenantId(session.getTenantId()).orElse(null);
        if (tenant == null || !"ACTIVE".equals(tenant.getStatus())) {
            return null;
        }
        if (shouldUpdateLastUsed(session.getId())) {
            session.setLastUsedAt(LocalDateTime.now());
            sessionRepository.save(session);
        }
        return new TenantContext(tenant.getTenantId(), "REST", null, "account:" + account.getUsername(),
                null, CONSOLE_ROLES, CONSOLE_SCOPES, UUID.randomUUID().toString());
    }

    /** 登录状态下修改密码：校验旧密码后更新哈希，并吊销该账号全部会话。 */
    @Transactional
    public void changePassword(String oldPassword, String newPassword) {
        TenantContext context = TenantContextHolder.require();
        String username = accountUsername(context);
        TenantAccount account = username == null
                ? null
                : accountRepository.findByUsername(username).orElse(null);
        boolean passwordOk = account != null && oldPassword != null
                && secretHasher.matches(oldPassword, account.getPasswordHash());
        if (account == null || !"ACTIVE".equals(account.getStatus()) || !passwordOk) {
            throw new TenantRegistrationException("INVALID_CREDENTIALS", "当前密码不正确",
                    HttpStatus.UNAUTHORIZED);
        }
        String password = newPassword == null ? "" : newPassword.trim();
        passwordPolicy.validate(password);
        if (password.length() < 8 || password.length() > 64) {
            throw new TenantRegistrationException("VALIDATION_ERROR", "新密码长度需为 8-128 位",
                    HttpStatus.BAD_REQUEST);
        }
        TenantAccount finalAccount = account;
        withContext(account.getTenantId(), () -> {
            finalAccount.setPasswordHash(secretHasher.hash(password));
            accountRepository.save(finalAccount);
            long revoked = sessionRepository.deleteByAccountId(finalAccount.getId());
            log.info("修改密码成功: accountId={}, 吊销会话={}", finalAccount.getId(), revoked);
            return null;
        });
    }

    private String accountUsername(TenantContext context) {
        String internalUserId = context == null ? null : context.internalUserId();
        if (internalUserId == null || !internalUserId.startsWith("account:")) {
            return null;
        }
        return internalUserId.substring("account:".length());
    }

    /** 当前租户的控制台账号（可能为 null：仅用 API Key 登录、尚未完善账号）。 */
    public TenantAccount consoleAccount() {
        String username = accountUsername(TenantContextHolder.getNullable());
        return username == null ? null : accountRepository.findByUsername(username).orElse(null);
    }

    /** 为尚无账号的租户完善控制台账号：用户名 + 已验证邮箱 + 密码。 */
    public AccountIssue setupAccount(String username, String password, String contactEmail, String emailCode) {
        TenantContext context = TenantContextHolder.require();
        String tenantId = context.tenantId();
        if (accountRepository.findByTenantId(tenantId).isPresent()) {
            throw new TenantRegistrationException("CONFLICT", "当前租户已配置控制台账号",
                    HttpStatus.CONFLICT);
        }
        String normalizedUsername = normalizeUsername(username);
        String normalizedEmail = normalizeEmail(contactEmail);
        String pwd = password == null ? "" : password.trim();
        passwordPolicy.validate(pwd);
        if (pwd.length() < 8 || pwd.length() > 64) {
            throw new TenantRegistrationException("VALIDATION_ERROR", "密码长度需为 8-128 位",
                    HttpStatus.BAD_REQUEST);
        }
        if (!emailVerificationService.verifyCode(normalizedEmail, "SETUP", emailCode)) {
            throw new TenantRegistrationException("EMAIL_CODE_INVALID", "邮箱验证码无效或已过期，请重新获取",
                    HttpStatus.BAD_REQUEST);
        }
        return createAccountAndIssueSession(tenantId, normalizedUsername, pwd, normalizedEmail);
    }

    private String normalizeUsername(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new TenantRegistrationException("VALIDATION_ERROR", "用户名不能为空",
                    HttpStatus.BAD_REQUEST);
        }
        String username = raw.trim().toLowerCase();
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new TenantRegistrationException("VALIDATION_ERROR",
                    "用户名需为 3-32 位，仅支持小写字母、数字、下划线和连字符", HttpStatus.BAD_REQUEST);
        }
        if (accountRepository.existsByUsername(username)) {
            throw new TenantRegistrationException("CONFLICT", "用户名已被注册：" + username,
                    HttpStatus.CONFLICT);
        }
        return username;
    }

    private String normalizeEmail(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new TenantRegistrationException("VALIDATION_ERROR", "邮箱不能为空",
                    HttpStatus.BAD_REQUEST);
        }
        String email = raw.trim().toLowerCase();
        if (email.length() > 128 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new TenantRegistrationException("VALIDATION_ERROR", "邮箱格式不正确",
                    HttpStatus.BAD_REQUEST);
        }
        return email;
    }

    private TenantSession saveSession(String tenantId, TenantAccount account, String rawToken) {
        TenantSession session = new TenantSession();
        session.setTenantId(tenantId);
        session.setAccountId(account.getId());
        session.setTokenHash(sha256Hex(rawToken));
        Duration ttl = sessionTtl == null || sessionTtl.isZero() || sessionTtl.isNegative()
                ? Duration.ofHours(168)
                : sessionTtl;
        session.setExpiresAt(LocalDateTime.now().plus(ttl));
        return withContext(tenantId, () -> sessionRepository.save(session));
    }

    private <T> T withContext(String tenantId, Supplier<T> action) {
        TenantContext previous = TenantContextHolder.getNullable();
        TenantContextHolder.set(new TenantContext(tenantId, "REST", null, "auth",
                null, CONSOLE_ROLES, CONSOLE_SCOPES, UUID.randomUUID().toString()));
        try {
            return action.get();
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
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
            log.info("清理过期会话: {}", removed);
        }
        Instant cutoff = Instant.now().minusSeconds(Math.max(60, lastUsedWriteIntervalSeconds * 2));
        lastUsedWrites.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }

    public record AccountIssue(String username, String sessionToken, LocalDateTime expiresAt) {
    }
}
