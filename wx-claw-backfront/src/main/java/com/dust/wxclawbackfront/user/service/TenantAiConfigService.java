package com.dust.wxclawbackfront.user.service;

import com.dust.wxclawbackfront.bot.agent.llm.chat.TenantChatClientFactory;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.entity.TenantAiConfig;
import com.dust.wxclawbackfront.tenancy.repository.TenantAiConfigRepository;
import com.dust.wxclawbackfront.user.api.dto.UserDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

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

    private static final Map<String, Capability> CAPABILITIES = Map.of(
            "chat", new Capability("文本对话/理解（火山方舟 OpenAI 兼容）",
                    TenantAiConfig::getApiKey, TenantAiConfig::setApiKey),
            "image", new Capability("图片生成（SiliconFlow）",
                    TenantAiConfig::getImageApiKey, TenantAiConfig::setImageApiKey),
            "video", new Capability("视频生成（火山方舟 Seedance）",
                    TenantAiConfig::getVideoApiKey, TenantAiConfig::setVideoApiKey),
            "videoDashscope", new Capability("视频生成（阿里云通义万相 DashScope）",
                    TenantAiConfig::getVideoDashscopeApiKey, TenantAiConfig::setVideoDashscopeApiKey),
            "tts", new Capability("语音合成（火山引擎 TTS）",
                    TenantAiConfig::getTtsApiKey, TenantAiConfig::setTtsApiKey),
            "search", new Capability("联网搜索（博查）",
                    TenantAiConfig::getSearchApiKey, TenantAiConfig::setSearchApiKey));

    public UserDtos.AiConfigs current() {
        Optional<TenantAiConfig> config = configRepository.findById(tenantId());
        return new UserDtos.AiConfigs(
                entry(config, "chat"),
                entry(config, "image"),
                entry(config, "video"),
                entry(config, "videoDashscope"),
                entry(config, "tts"),
                entry(config, "search"));
    }

    @Transactional
    public UserDtos.AiConfigEntry save(String capability, String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("API Key 不能为空");
        }
        Capability cap = requireCapability(capability);
        String tenantId = tenantId();
        TenantAiConfig config = configRepository.findById(tenantId).orElseGet(TenantAiConfig::new);
        config.setTenantId(tenantId);
        cap.setter().accept(config, apiKey.trim());
        configRepository.save(config);
        if ("chat".equals(capability)) {
            chatClientFactory.evict(tenantId);
        }
        return entry(Optional.of(config), capability);
    }

    @Transactional
    public void clear(String capability) {
        Capability cap = requireCapability(capability);
        String tenantId = tenantId();
        Optional<TenantAiConfig> config = configRepository.findById(tenantId);
        if (config.isEmpty()) {
            return;
        }
        cap.setter().accept(config.get(), null);
        configRepository.save(config.get());
        if ("chat".equals(capability)) {
            chatClientFactory.evict(tenantId);
        }
    }

    private UserDtos.AiConfigEntry entry(Optional<TenantAiConfig> config, String capability) {
        Capability cap = CAPABILITIES.get(capability);
        String apiKey = config.map(cap.getter()).orElse(null);
        boolean configured = apiKey != null && !apiKey.isBlank();
        return new UserDtos.AiConfigEntry(
                configured,
                configured ? mask(apiKey) : null,
                cap.provider());
    }

    private Capability requireCapability(String capability) {
        Capability cap = CAPABILITIES.get(capability);
        if (cap == null) {
            throw new IllegalArgumentException("未知能力: " + capability);
        }
        return cap;
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

    private record Capability(String provider,
                              Function<TenantAiConfig, String> getter,
                              BiConsumer<TenantAiConfig, String> setter) {
    }
}
