package com.dust.wxclawbackfront.tenancy;

import com.dust.wxclawbackfront.tenancy.entity.Tenant;
import com.dust.wxclawbackfront.tenancy.entity.AdminAccount;
import com.dust.wxclawbackfront.tenancy.entity.TenantApiCredential;
import com.dust.wxclawbackfront.tenancy.entity.TenantInviteCode;
import com.dust.wxclawbackfront.tenancy.repository.AdminAccountRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantApiCredentialRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantBotRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantInviteCodeRepository;
import com.dust.wxclawbackfront.tenancy.repository.TenantRepository;
import com.dust.wxclawbackfront.tenancy.entity.TenantBot;
import com.dust.wxclawbackfront.tenancy.security.ApiSecretHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.security.SecureRandom;

/**
 * 在应用启动早期准备默认租户、配置文件中的 iLink Bot 和首个 API 凭据。
 *
 * <p>初始化过程是幂等的：数据库中已存在的租户、Bot 或凭据不会重复创建。
 * 创建租户私有实体前会显式建立 SYSTEM 上下文，以复用正常的实体租户校验机制。</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Order(0)
public class TenantBootstrapInitializer implements ApplicationRunner {
    private final SecureRandom secureRandom = new SecureRandom();

    private final TenantRepository tenantRepository;
    private final TenantApiCredentialRepository credentialRepository;
    private final TenantBotRepository tenantBotRepository;
    private final TenantInviteCodeRepository inviteCodeRepository;
    private final AdminAccountRepository adminAccountRepository;
    private final ApiSecretHasher secretHasher;

    @Value("${wxclaw.tenancy.default-tenant-id:default}")
    private String defaultTenantId;

    @Value("${wxclaw.api.bootstrap-credential-id:default}")
    private String credentialId;

    @Value("${wxclaw.api.bootstrap-key:}")
    private String bootstrapKey;

    @Value("${wxclaw.api.registration.invite-codes:}")
    private List<String> bootstrapInviteCodes;

    @Value("${wxclaw.api.admin.bootstrap-username:admin}")
    private String adminUsername;

    @Value("${wxclaw.api.admin.bootstrap-password:}")
    private String adminPassword;

    @Value("${wxclaw.ilink.bot-ids:${wxclaw.ilink.default-bot-id:default}}")
    private List<String> botIds;

    @Value("${wxclaw.security.log-generated-bootstrap-secrets:false}")
    private boolean logGeneratedBootstrapSecrets;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 租户是上下文的根，必须先创建，再处理其下属 Bot 和凭据。
        Tenant tenant = tenantRepository.findByTenantId(defaultTenantId).orElseGet(() -> {
            Tenant created = new Tenant();
            created.setTenantId(defaultTenantId);
            created.setTenantCode(defaultTenantId);
            created.setTenantName("Default Tenant");
            created.setStatus("ACTIVE");
            return tenantRepository.save(created);
        });
        // 后续实体继承 TenantOwnedEntity，需要有效上下文才能持久化。
        TenantContextHolder.set(new TenantContext(tenant.getTenantId(), "SYSTEM", null, "bootstrap", null,
                Collections.singleton("TENANT_ADMIN"), Collections.singleton("*"), "bootstrap"));
        try {
            List<TenantBot> activeBots = tenantBotRepository.findByChannelAndStatus("ILINK", "ACTIVE");
            for (String configuredBotId : botIds) {
                String botId = configuredBotId == null ? "" : configuredBotId.trim();
                if (botId.isEmpty() || activeBots.stream().anyMatch(bot ->
                        bot.getTenantId().equals(tenant.getTenantId()) && botId.equals(bot.getBotId()))) continue;
                TenantBot bot = new TenantBot();
                bot.setChannel("ILINK");
                bot.setBotId(botId);
                bot.setDisplayName("ILink Bot " + botId);
                tenantBotRepository.save(bot);
            }
            if (credentialRepository.findByCredentialId(credentialId).isEmpty()) {
                TenantApiCredential credential = new TenantApiCredential();
                credential.setCredentialId(credentialId);
                credential.setName("Bootstrap credential");
                String secret;
                if (bootstrapKey != null && !bootstrapKey.isBlank()) {
                    secret = bootstrapKey.trim();
                } else {
                    // 未配置管理 Key 时自动生成：只在首次启动打印一次原始 Key，数据库只存哈希。
                    byte[] bytes = new byte[24];
                    secureRandom.nextBytes(bytes);
                    secret = "wxclaw-" + HexFormat.of().formatHex(bytes);
                    if (logGeneratedBootstrapSecrets) {
                    log.warn("未配置 API_BOOTSTRAP_KEY，已自动生成管理 API Key（仅本次展示，请立即保存）: {}.{}",
                            credentialId, secret);
                    } else {
                        log.warn("未配置 API_BOOTSTRAP_KEY，已自动生成但不打印明文。请设置 API_BOOTSTRAP_KEY 后重启；本地开发可临时开启 wxclaw.security.log-generated-bootstrap-secrets=true");
                    }
                }
                credential.setSecretHash(secretHasher.hash(secret));
                credential.setScopes("*");
                credentialRepository.save(credential);
            }
            // 配置文件里的邀请码在首次启动时落库（幂等），用于初期发号。
            if (bootstrapInviteCodes != null) {
                for (String raw : bootstrapInviteCodes) {
                    String code = raw == null ? "" : raw.trim().toUpperCase();
                    if (code.isEmpty() || inviteCodeRepository.findByCode(code).isPresent()) {
                        continue;
                    }
                    TenantInviteCode invite = new TenantInviteCode();
                    invite.setCode(code);
                    invite.setStatus("ACTIVE");
                    invite.setRemark("bootstrap");
                    invite.setCreatedBy("bootstrap");
                    inviteCodeRepository.save(invite);
                }
            }
            // 平台管理员账号：首次启动创建；未配置 ADMIN_PASSWORD 时自动生成并打印一次。
            if (!adminAccountRepository.existsByUsername(adminUsername.trim().toLowerCase())) {
                String password = adminPassword;
                if (password == null || password.isBlank()) {
                    byte[] bytes = new byte[12];
                    secureRandom.nextBytes(bytes);
                    password = HexFormat.of().formatHex(bytes);
                    if (logGeneratedBootstrapSecrets) {
                    log.warn("未配置 ADMIN_PASSWORD，已自动生成管理端初始密码（仅本次展示，请立即修改）: username={}, password={}",
                            adminUsername.trim().toLowerCase(), password);
                    } else {
                        log.warn("未配置 ADMIN_PASSWORD，已自动生成但不打印明文。请设置 ADMIN_PASSWORD 后重启；本地开发可临时开启 wxclaw.security.log-generated-bootstrap-secrets=true");
                    }
                }
                AdminAccount account = new AdminAccount();
                account.setUsername(adminUsername.trim().toLowerCase());
                account.setPasswordHash(secretHasher.hash(password));
                account.setRole("SUPER_ADMIN");
                account.setStatus("ACTIVE");
                adminAccountRepository.save(account);
            }
        } finally {
            // ApplicationRunner 运行在线程池外也必须清理，避免污染后续启动逻辑。
            TenantContextHolder.clear();
        }
    }
}
