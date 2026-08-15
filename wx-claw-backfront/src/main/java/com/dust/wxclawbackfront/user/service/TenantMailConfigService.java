package com.dust.wxclawbackfront.user.service;

import com.dust.wxclawbackfront.config.security.TenantAiKeyCipher;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.entity.TenantMailConfig;
import com.dust.wxclawbackfront.tenancy.repository.TenantMailConfigRepository;
import com.dust.wxclawbackfront.user.api.dto.UserDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TenantMailConfigService {

    private final TenantMailConfigRepository repository;
    private final TenantAiKeyCipher keyCipher;

    public UserDtos.MailConfig current() {
        Optional<TenantMailConfig> config = repository.findById(tenantId());
        return new UserDtos.MailConfig(
                config.map(TenantMailConfig::getSmtpHost).orElse(null),
                config.map(TenantMailConfig::getSmtpPort).orElse(0),
                config.map(TenantMailConfig::getUsername).orElse(null),
                config.map(TenantMailConfig::getFromAddress).orElse(null),
                config.map(TenantMailConfig::isEnabled).orElse(false),
                config.isPresent());
    }

    @Transactional
    public UserDtos.MailConfig save(UserDtos.SaveMailConfigRequest request) {
        validate(request);
        String id = tenantId();
        TenantMailConfig config = repository.findById(id).orElseGet(TenantMailConfig::new);
        config.setTenantId(id);
        config.setSmtpHost(request.smtpHost().trim());
        config.setSmtpPort(request.smtpPort());
        config.setUsername(request.username().trim());
        config.setPasswordCipher(keyCipher.encrypt(request.password().trim()));
        config.setFromAddress(request.fromAddress().trim());
        config.setEnabled(true);
        repository.save(config);
        return current();
    }

    @Transactional
    public void clear() {
        repository.deleteById(tenantId());
    }

    public TenantMailConfig loadForTenant(String tenantId) {
        return repository.findById(tenantId)
                .filter(TenantMailConfig::isEnabled)
                .orElse(null);
    }

    private String tenantId() {
        return TenantContextHolder.require().tenantId();
    }

    private void validate(UserDtos.SaveMailConfigRequest request) {
        if (request == null
                || request.smtpHost() == null || request.smtpHost().isBlank()
                || request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()
                || request.fromAddress() == null || request.fromAddress().isBlank()) {
            throw new IllegalArgumentException("SMTP 配置不完整");
        }
        if (request.smtpPort() < 1 || request.smtpPort() > 65535) {
            throw new IllegalArgumentException("SMTP 端口无效");
        }
    }
}
