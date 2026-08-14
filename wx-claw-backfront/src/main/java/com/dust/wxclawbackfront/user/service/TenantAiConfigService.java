package com.dust.wxclawbackfront.user.service;

import com.dust.wxclawbackfront.bot.agent.llm.chat.TenantChatClientFactory;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.entity.TenantAiConfig;
import com.dust.wxclawbackfront.tenancy.repository.TenantAiConfigRepository;
import com.dust.wxclawbackfront.user.api.dto.UserDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 用户自己的 LLM API Key 配置：保存/查询（脱敏）/清除。
 *
 * <p>保存或清除后立即驱逐 {@link TenantChatClientFactory} 中该租户的 client 缓存，
 * 使新 Key 对后续请求生效。</p>
 */
@Service
@RequiredArgsConstructor
public class TenantAiConfigService {

    private final TenantAiConfigRepository configRepository;
    private final TenantChatClientFactory chatClientFactory;

    @Value("${spring.ai.openai.base-url:https://ark.cn-beijing.volces.com/api/v3}")
    private String baseUrl;

    public UserDtos.AiConfig current() {
        String tenantId = tenantId();
        Optional<TenantAiConfig> config = configRepository.findById(tenantId);
        String apiKey = config.map(TenantAiConfig::getApiKey).orElse(null);
        boolean configured = apiKey != null && !apiKey.isBlank();
        return new UserDtos.AiConfig(
                configured,
                configured ? mask(apiKey) : null,
                baseUrl,
                config.map(TenantAiConfig::getUpdatedAt).orElse(null));
    }

    @Transactional
    public UserDtos.AiConfig save(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        String tenantId = tenantId();
        TenantAiConfig config = configRepository.findById(tenantId).orElseGet(TenantAiConfig::new);
        config.setTenantId(tenantId);
        config.setApiKey(apiKey.trim());
        configRepository.save(config);
        chatClientFactory.evict(tenantId);
        return current();
    }

    @Transactional
    public void clear() {
        String tenantId = tenantId();
        configRepository.deleteById(tenantId);
        chatClientFactory.evict(tenantId);
    }

    private String tenantId() {
        return TenantContextHolder.require().tenantId();
    }

    private String mask(String key) {
        if (key == null || key.isBlank()) {
            return "***";
        }
        String trimmed = key.trim();
        if (trimmed.length() <= 8) {
            return "***";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }
}
