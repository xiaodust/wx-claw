package com.dust.wxclawbackfront.tenancy.service;

import com.dust.wxclawbackfront.bot.agent.tools.mail.MailHandler;
import com.dust.wxclawbackfront.bot.agent.tools.mail.MailSendResult;
import com.dust.wxclawbackfront.tenancy.entity.TenantEmailVerification;
import com.dust.wxclawbackfront.tenancy.repository.TenantEmailVerificationRepository;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 邮箱验证码：注册前发送验证码到邮箱，校验通过才允许创建租户；
 * 密码找回沿用同一邮箱路径（账号必须持有已验证邮箱）。
 *
 * <p>安全约束：验证码为 6 位随机数，只存 SHA-256 哈希，10 分钟有效且单次使用；
 * 发送按 邮箱+用途 与 IP 双重限流；邮件不可用时把验证码写入日志（开发兜底）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final List<String> PURPOSES = List.of("REGISTER", "RESET", "SETUP");

    private final TenantEmailVerificationRepository verificationRepository;
    private final PublicAuthRateLimiter rateLimiter;
    private final ObjectProvider<MailHandler> mailHandlerProvider;

    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${wxclaw.api.email-code.ttl:PT10M}")
    private Duration codeTtl;

    @Value("${wxclaw.api.email-code.log-code-when-mail-unavailable:true}")
    private boolean logCodeWhenMailUnavailable;

    /** 开发联调开关：无论邮件是否发出都打印验证码。 */
    @Value("${wxclaw.api.email-code.log-code-always:false}")
    private boolean logCodeAlways;

    @Transactional
    public void sendCode(String email, String purpose, String clientIp) {
        String normalized = normalizeEmail(email);
        String upperPurpose = normalizePurpose(purpose);
        if (normalized == null) {
            throw new TenantRegistrationException("VALIDATION_ERROR", "邮箱格式不正确",
                    HttpStatus.BAD_REQUEST);
        }
        rateLimiter.checkEmailCode(normalized, upperPurpose, clientIp);

        // 使同一邮箱+用途的旧验证码立即失效，保证任何时刻只有一个有效码。
        List<TenantEmailVerification> previous = verificationRepository
                .findByEmailAndPurposeOrderByCreatedAtDesc(normalized, upperPurpose);
        LocalDateTime now = LocalDateTime.now();
        previous.stream()
                .filter(v -> v.getUsedAt() == null)
                .forEach(v -> verificationRepository.markUsed(v.getId(), now));

        String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
        TenantEmailVerification verification = new TenantEmailVerification();
        verification.setEmail(normalized);
        verification.setPurpose(upperPurpose);
        verification.setCodeHash(sha256Hex(code));
        Duration ttl = codeTtl == null || codeTtl.isZero() || codeTtl.isNegative()
                ? Duration.ofMinutes(10)
                : codeTtl;
        verification.setExpiresAt(now.plus(ttl));
        verificationRepository.save(verification);

        boolean sent = sendEmail(normalized, upperPurpose, code, ttl.toMinutes());
        if (logCodeAlways || (!sent && logCodeWhenMailUnavailable)) {
            // 开发环境兜底：邮件不可用时把验证码写入日志，方便本地联调。
            log.warn("邮箱验证码（仅开发环境使用，{} 分钟内有效）: email={}, code={}",
                    ttl.toMinutes(), normalized, code);
        }
    }

    /** 校验并消费验证码；成功返回 true（单次使用）。 */
    @Transactional
    public boolean verifyCode(String email, String purpose, String code) {
        if (email == null || email.isBlank() || code == null || code.isBlank()) {
            return false;
        }
        String normalized = email.trim().toLowerCase();
        TenantEmailVerification latest = verificationRepository
                .findByEmailAndPurposeOrderByCreatedAtDesc(normalized, purpose).stream().findFirst().orElse(null);
        if (latest == null || latest.getUsedAt() != null
                || latest.getExpiresAt().isBefore(LocalDateTime.now())
                || !sha256Hex(code.trim()).equals(latest.getCodeHash())) {
            return false;
        }
        return verificationRepository.markUsed(latest.getId(), LocalDateTime.now()) == 1;
    }

    private boolean sendEmail(String email, String purpose, String code, long minutes) {
        MailHandler handler = mailHandlerProvider.getIfAvailable();
        if (handler == null) {
            log.warn("邮件功能未启用（wxclaw.mail.enabled=false），无法发送验证码");
            return false;
        }
        String subject = "REGISTER".equals(purpose) ? "WX-Claw 注册邮箱验证码" : "WX-Claw 邮箱验证码";
        String content = "<p>你好：</p>"
                + "<p>你的邮箱验证码是：</p>"
                + "<p style=\"font-size:24px;letter-spacing:4px;font-weight:700;\">" + code + "</p>"
                + "<p>" + minutes + " 分钟内有效。如果这不是你的操作，请忽略本邮件。</p>";
        MailSendResult result = handler.send(email, subject, content, true);
        if (!result.isSuccess()) {
            log.warn("验证码邮件发送失败: to={}, error={}", email, result.getErrorMsg());
        }
        return result.isSuccess();
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String value = email.trim().toLowerCase();
        return value.length() <= 128 && EMAIL_PATTERN.matcher(value).matches() ? value : null;
    }

    private String normalizePurpose(String purpose) {
        String value = purpose == null ? "" : purpose.trim().toUpperCase();
        return PURPOSES.contains(value) ? value : "REGISTER";
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

    @Scheduled(fixedDelayString = "${wxclaw.api.email-code.cleanup-ms:1800000}")
    public void cleanupExpiredCodes() {
        long removed = verificationRepository.deleteByExpiresAtBefore(LocalDateTime.now());
        if (removed > 0) {
            log.info("清理过期邮箱验证码: {}", removed);
        }
    }
}
