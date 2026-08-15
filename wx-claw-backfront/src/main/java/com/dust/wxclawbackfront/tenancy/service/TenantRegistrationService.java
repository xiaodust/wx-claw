package com.dust.wxclawbackfront.tenancy.service;

import com.dust.wxclawbackfront.config.security.PasswordPolicy;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.RegisterTenantRequest;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.RegisteredTenant;
import com.dust.wxclawbackfront.tenancy.entity.Tenant;
import com.dust.wxclawbackfront.tenancy.repository.TenantAccountRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantRepository;
import com.dust.wxclawbackfront.tenancy.security.PublicAuthRateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 租户自助注册：创建租户并签发首个控制台 API Key。
 *
 * <p>安全约束：租户编码与用户名全局唯一；注册不签发 API Key（控制台使用用户名密码登录），
 * 邀请码与邮箱验证码原子消费，任一步失败整体回滚。</p>
 */
@Slf4j
@Service
public class TenantRegistrationService {

    private static final Pattern TENANT_CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,31}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_-]{3,32}$");

    private final TenantRepository tenantRepository;
    private final TenantAccountRepository accountRepository;
    private final TenantAuthService authService;
    private final PublicAuthRateLimiter rateLimiter;
    private final InviteCodeService inviteCodeService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordPolicy passwordPolicy;
    private final SecureRandom secureRandom = new SecureRandom();
    private final boolean requireInvite;

    public TenantRegistrationService(TenantRepository tenantRepository,
                                     TenantAccountRepository accountRepository,
                                     TenantAuthService authService,
                                      PublicAuthRateLimiter rateLimiter,
                                      InviteCodeService inviteCodeService,
                                      EmailVerificationService emailVerificationService,
                                      PasswordPolicy passwordPolicy,
                                      @Value("${wxclaw.api.registration.require-invite:true}") boolean requireInvite) {
        this.tenantRepository = tenantRepository;
        this.accountRepository = accountRepository;
        this.authService = authService;
        this.rateLimiter = rateLimiter;
        this.inviteCodeService = inviteCodeService;
        this.emailVerificationService = emailVerificationService;
        this.passwordPolicy = passwordPolicy;
        this.requireInvite = requireInvite;
    }

    @Transactional
    public RegisteredTenant register(RegisterTenantRequest request, String clientIp) {
        String tenantName = normalizeName(request == null ? null : request.tenantName());
        if (tenantName == null) {
            throw new TenantRegistrationException("VALIDATION_ERROR", "租户名称不能为空", HttpStatus.BAD_REQUEST);
        }
        String tenantCode = resolveTenantCode(request == null ? null : request.tenantCode());
        String contactEmail = normalizeEmail(request == null ? null : request.contactEmail());
        if (contactEmail == null) {
            throw new TenantRegistrationException("VALIDATION_ERROR", "邮箱为必填项",
                    HttpStatus.BAD_REQUEST);
        }
        String username = resolveUsername(request == null ? null : request.username());
        if (username != null) {
            validatePassword(request == null ? null : request.password());
        }
        if (!emailVerificationService.verifyCode(contactEmail, "REGISTER",
                request == null ? null : request.emailCode())) {
            throw new TenantRegistrationException("EMAIL_CODE_INVALID",
                    "邮箱验证码无效或已过期，请重新获取", HttpStatus.BAD_REQUEST);
        }
        String inviteCode = request == null ? null : request.inviteCode();
        if (requireInvite && !inviteCodeService.consume(inviteCode)) {
            throw new TenantRegistrationException("INVALID_INVITE",
                    "邀请码无效、已过期或已用尽", HttpStatus.BAD_REQUEST);
        }

        rateLimiter.checkRegistration(clientIp, contactEmail);

        String tenantId = UUID.randomUUID().toString();
        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setTenantCode(tenantCode);
        tenant.setTenantName(tenantName);
        tenant.setStatus("ACTIVE");
        tenant.setPlanCode("FREE");
        tenantRepository.save(tenant);

        log.info("租户注册成功: tenantId={}, tenantCode={}", tenantId, tenantCode);
        // 注册成功即签发会话 token，前端可一键进入控制台。
        TenantAuthService.AccountIssue accountIssue = username == null
                ? null
                : authService.createAccountAndIssueSession(tenantId, username, request.password().trim(), contactEmail);
        return new RegisteredTenant(tenantId, tenantCode, tenantName, tenant.getStatus(),
                tenant.getCreatedAt(),
                accountIssue == null ? null : accountIssue.username(),
                accountIssue == null ? null : accountIssue.sessionToken(),
                accountIssue == null ? null : accountIssue.expiresAt());
    }

    private String normalizeName(String raw) {
        if (raw == null) {
            return null;
        }
        String name = raw.trim().replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) {
            return null;
        }
        if (name.length() > 50) {
            throw new TenantRegistrationException("VALIDATION_ERROR", "租户名称不能超过 50 个字符", HttpStatus.BAD_REQUEST);
        }
        return name;
    }

    private String resolveTenantCode(String raw) {
        String provided = raw == null ? "" : raw.trim().toLowerCase();
        if (!provided.isEmpty()) {
            if (!TENANT_CODE_PATTERN.matcher(provided).matches()) {
                throw new TenantRegistrationException("VALIDATION_ERROR",
                        "租户编码只能包含小写字母、数字和连字符，且以字母或数字开头（2-32 位）", HttpStatus.BAD_REQUEST);
            }
            if (tenantRepository.findByTenantCode(provided).isPresent()) {
                throw new TenantRegistrationException("CONFLICT", "租户编码已存在：" + provided, HttpStatus.CONFLICT);
            }
            return provided;
        }
        // 未提供编码时自动生成，避免对外暴露内部随机 tenantId。
        for (int i = 0; i < 5; i++) {
            String candidate = "t" + randomHex(6);
            if (tenantRepository.findByTenantCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new TenantRegistrationException("CONFLICT", "租户编码生成失败，请稍后重试", HttpStatus.CONFLICT);
    }

    private String normalizeEmail(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String email = raw.trim().toLowerCase();
        if (email.length() > 128 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw new TenantRegistrationException("VALIDATION_ERROR", "联系邮箱格式不正确", HttpStatus.BAD_REQUEST);
        }
        return email;
    }

    private String resolveUsername(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String username = raw.trim().toLowerCase();
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new TenantRegistrationException("VALIDATION_ERROR",
                    "用户名需为 3-32 位，仅支持小写字母、数字、下划线和连字符", HttpStatus.BAD_REQUEST);
        }
        if (accountRepository.existsByUsername(username)) {
            throw new TenantRegistrationException("CONFLICT", "用户名已被注册：" + username, HttpStatus.CONFLICT);
        }
        return username;
    }

    private void validatePassword(String password) {
        passwordPolicy.validate(password);
        if (password == null || password.trim().length() < 8 || password.trim().length() > 64) {
            throw new TenantRegistrationException("VALIDATION_ERROR",
                    "密码长度需为 8-128 位", HttpStatus.BAD_REQUEST);
        }
    }

    private String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        secureRandom.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }
}
