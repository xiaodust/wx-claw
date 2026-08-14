package com.dust.wxclawbackfront.tenancy.service;

import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.RegisterTenantRequest;
import com.dust.wxclawbackfront.tenancy.api.PublicTenantDtos.RegisteredTenant;
import com.dust.wxclawbackfront.tenancy.entity.Tenant;
import com.dust.wxclawbackfront.tenancy.entity.TenantApiCredential;
import com.dust.wxclawbackfront.tenancy.repository.TenantApiCredentialRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantRepository;
import com.dust.wxclawbackfront.tenancy.security.ApiSecretHasher;
import com.dust.wxclawbackfront.tenancy.security.RegistrationRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 租户自助注册：创建租户并签发首个控制台 API Key。
 *
 * <p>安全约束：租户编码必须全局唯一；API Key 的 secret 部分只以 PBKDF2 哈希落库，
 * 原始 Key 仅在响应中返回一次，禁止写入日志；写租户私有实体前建立独立租户上下文，
 * 结束后恢复或清理，避免污染请求线程。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantRegistrationService {

    private static final Pattern TENANT_CODE_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,31}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final String CONSOLE_SCOPES =
            "userbot:read,userbot:write,conversation:read,aiconfig:read,aiconfig:write";

    private final TenantRepository tenantRepository;
    private final TenantApiCredentialRepository credentialRepository;
    private final ApiSecretHasher secretHasher;
    private final RegistrationRateLimiter rateLimiter;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public RegisteredTenant register(RegisterTenantRequest request, String clientIp) {
        String tenantName = normalizeName(request == null ? null : request.tenantName());
        if (tenantName == null) {
            throw new TenantRegistrationException("VALIDATION_ERROR", "租户名称不能为空", HttpStatus.BAD_REQUEST);
        }
        String tenantCode = resolveTenantCode(request == null ? null : request.tenantCode());
        String contactEmail = normalizeEmail(request == null ? null : request.contactEmail());

        rateLimiter.check(clientIp, contactEmail);

        String tenantId = UUID.randomUUID().toString();
        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setTenantCode(tenantCode);
        tenant.setTenantName(tenantName);
        tenant.setStatus("ACTIVE");
        tenant.setPlanCode("FREE");
        tenantRepository.save(tenant);

        // 与启动引导一致：创建租户私有实体前建立租户上下文，结束后恢复原上下文。
        TenantContext previous = TenantContextHolder.getNullable();
        TenantContextHolder.set(new TenantContext(tenantId, "REST", null, "api:register", null,
                Set.of("TENANT_ADMIN"), Set.of("*"), UUID.randomUUID().toString()));
        try {
            String secret = randomHex(32);
            TenantApiCredential credential = new TenantApiCredential();
            credential.setCredentialId(uniqueCredentialId());
            credential.setName("控制台 API Key");
            credential.setSecretHash(secretHasher.hash(secret));
            credential.setScopes(CONSOLE_SCOPES);
            credential.setStatus("ACTIVE");
            credentialRepository.save(credential);
            log.info("租户注册成功: tenantId={}, tenantCode={}, credentialId={}", tenantId, tenantCode, credential.getCredentialId());
            return new RegisteredTenant(tenantId, tenantCode, tenantName, tenant.getStatus(),
                    tenant.getCreatedAt(), credential.getCredentialId(),
                    credential.getCredentialId() + "." + secret, CONSOLE_SCOPES);
        } finally {
            if (previous == null) {
                TenantContextHolder.clear();
            } else {
                TenantContextHolder.set(previous);
            }
        }
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

    private String uniqueCredentialId() {
        for (int i = 0; i < 5; i++) {
            String candidate = "tk_" + randomHex(8);
            if (credentialRepository.findByCredentialId(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new TenantRegistrationException("CONFLICT", "凭据创建失败，请稍后重试", HttpStatus.CONFLICT);
    }

    private String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        secureRandom.nextBytes(buffer);
        return HexFormat.of().formatHex(buffer);
    }
}
