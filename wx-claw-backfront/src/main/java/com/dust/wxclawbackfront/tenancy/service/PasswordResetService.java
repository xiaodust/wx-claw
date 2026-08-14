package com.dust.wxclawbackfront.tenancy.service;

import com.dust.wxclawbackfront.bot.agent.tools.mail.MailHandler;
import com.dust.wxclawbackfront.bot.agent.tools.mail.MailSendResult;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.entity.TenantAccount;
import com.dust.wxclawbackfront.tenancy.entity.TenantPasswordReset;
import com.dust.wxclawbackfront.tenancy.repository.TenantAccountRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantPasswordResetRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantSessionRepository;
import com.dust.wxclawbackfront.tenancy.security.ApiSecretHasher;
import com.dust.wxclawbackfront.tenancy.security.PublicAuthRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 密码重置自助找回：申请后向账号邮箱发送一次性重置链接，点击后设置新密码并吊销全部会话。
 *
 * <p>安全约束：重置 token 为 256 位随机数，只存 SHA-256 哈希，30 分钟过期且单次使用；
 * 账号不存在时同样返回成功提示，防止用户枚举；申请与重置接口都有独立限流；
 * 邮件不可用时默认把链接写入日志（开发环境兜底），生产建议配置 SMTP 并关闭该开关。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final Set<String> SYSTEM_ROLES = Set.of("TENANT_ADMIN");
    private static final Set<String> SYSTEM_SCOPES = Set.of(
            "userbot:read", "userbot:write", "conversation:read", "aiconfig:read", "aiconfig:write");

    private final TenantAccountRepository accountRepository;
    private final TenantPasswordResetRepository resetRepository;
    private final TenantSessionRepository sessionRepository;
    private final ApiSecretHasher secretHasher;
    private final PublicAuthRateLimiter rateLimiter;
    private final ObjectProvider<MailHandler> mailHandlerProvider;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${wxclaw.api.password-reset.token-ttl:PT30M}")
    private Duration tokenTtl;

    @Value("${wxclaw.api.password-reset.base-url:http://localhost:3001}")
    private String resetBaseUrl;

    @Value("${wxclaw.api.password-reset.log-link-when-mail-unavailable:true}")
    private boolean logLinkWhenMailUnavailable;

    /** 申请重置：只返回成功提示，账号不存在时不发送邮件也不暴露差异。 */
    public void requestReset(String usernameOrEmail, String clientIp) {
        rateLimiter.checkPasswordReset(usernameOrEmail, clientIp);

        TenantAccount account = resolveAccount(usernameOrEmail);
        if (account == null) {
            log.info("密码重置请求：账号不存在，不发送邮件（usernameOrEmail={}）",
                    mask(usernameOrEmail));
            return;
        }

        String rawToken = newResetToken();
        TenantPasswordReset reset = new TenantPasswordReset();
        reset.setTenantId(account.getTenantId());
        reset.setAccountId(account.getId());
        reset.setTokenHash(sha256Hex(rawToken));
        Duration ttl = tokenTtl == null || tokenTtl.isZero() || tokenTtl.isNegative()
                ? Duration.ofMinutes(30)
                : tokenTtl;
        reset.setExpiresAt(LocalDateTime.now().plus(ttl));
        withContext(account.getTenantId(), () -> {
            resetRepository.save(reset);
            return null;
        });

        String link = resetBaseUrl + "/reset-password?token=" + rawToken;
        boolean sent = sendResetEmail(account.getContactEmail(), link);
        if (!sent && logLinkWhenMailUnavailable) {
            // 开发环境兜底：邮件未配置/发送失败时把链接写入日志，方便本地联调。
            log.warn("重置邮件未发送，重置链接（仅开发环境使用，30 分钟内有效）: {}", link);
        }
    }

    /** 使用重置链接设置新密码，成功后吊销该账号全部会话。 */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        String password = newPassword == null ? "" : newPassword.trim();
        if (password.length() < 8 || password.length() > 128) {
            throw new TenantRegistrationException("VALIDATION_ERROR", "密码长度需为 8-128 位",
                    HttpStatus.BAD_REQUEST);
        }
        if (token == null || token.isBlank()) {
            throw new TenantRegistrationException("INVALID_TOKEN", "重置链接无效或已过期",
                    HttpStatus.BAD_REQUEST);
        }

        TenantPasswordReset reset = resetRepository.findByTokenHash(sha256Hex(token.trim())).orElse(null);
        if (reset == null || reset.getUsedAt() != null
                || reset.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TenantRegistrationException("INVALID_TOKEN", "重置链接无效或已过期",
                    HttpStatus.BAD_REQUEST);
        }

        TenantAccount account = accountRepository.findById(reset.getAccountId()).orElse(null);
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw new TenantRegistrationException("INVALID_TOKEN", "账号不可用，请重新申请重置",
                    HttpStatus.BAD_REQUEST);
        }

        TenantAccount finalAccount = account;
        withContext(account.getTenantId(), () -> {
            finalAccount.setPasswordHash(secretHasher.hash(password));
            accountRepository.save(finalAccount);
            reset.setUsedAt(LocalDateTime.now());
            resetRepository.save(reset);
            // 重置后吊销该账号所有会话，防止旧会话继续使用。
            long revoked = sessionRepository.deleteByAccountId(finalAccount.getId());
            log.info("密码已重置: accountId={}, 吊销会话={}", finalAccount.getId(), revoked);
            return null;
        });
    }

    private TenantAccount resolveAccount(String usernameOrEmail) {
        if (usernameOrEmail == null || usernameOrEmail.isBlank()) {
            return null;
        }
        String value = usernameOrEmail.trim();
        TenantAccount byUsername = accountRepository.findByUsername(value.toLowerCase()).orElse(null);
        if (byUsername != null) {
            return byUsername;
        }
        return value.contains("@")
                ? accountRepository.findByContactEmail(value.toLowerCase()).orElse(null)
                : null;
    }

    private boolean sendResetEmail(String email, String link) {
        if (email == null || email.isBlank()) {
            log.warn("账号未配置联系邮箱，无法发送重置邮件");
            return false;
        }
        MailHandler handler = mailHandlerProvider.getIfAvailable();
        if (handler == null) {
            log.warn("邮件功能未启用（wxclaw.mail.enabled=false），无法发送重置邮件");
            return false;
        }
        String content = "<p>你好：</p>"
                + "<p>我们收到了你的密码重置请求，请点击以下链接设置新密码（30 分钟内有效）：</p>"
                + "<p><a href=\"" + link + "\">" + link + "</a></p>"
                + "<p>如果这不是你的操作，请忽略本邮件，你的密码不会被修改。</p>";
        MailSendResult result = handler.send(email, "WX-Claw 密码重置", content, true);
        if (!result.isSuccess()) {
            log.warn("重置邮件发送失败: to={}, error={}", email, result.getErrorMsg());
        }
        return result.isSuccess();
    }

    private String newResetToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return "pwreset_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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

    private <T> T withContext(String tenantId, Supplier<T> action) {
        TenantContext previous = TenantContextHolder.getNullable();
        TenantContextHolder.set(new TenantContext(tenantId, "REST", null, "password-reset",
                null, SYSTEM_ROLES, SYSTEM_SCOPES, UUID.randomUUID().toString()));
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

    /** 脱敏展示：只保留前 2 位，避免把邮箱/用户名完整写入日志。 */
    private String mask(String value) {
        if (value == null || value.length() <= 2) {
            return "**";
        }
        return value.substring(0, 2) + "***";
    }

    @Scheduled(fixedDelayString = "${wxclaw.api.password-reset.cleanup-ms:1800000}")
    @Transactional
    public void cleanupExpiredResets() {
        long removed = resetRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        if (removed > 0) {
            log.info("清理过期密码重置令牌: {}", removed);
        }
    }
}
