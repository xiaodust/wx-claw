package com.dust.wxclawbackfront.user.service;

import com.dust.wxclawbackfront.bot.agent.llm.AiModelCatalog;
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
    private final AiModelCatalog modelCatalog;

    private static final Map<String, Capability> CAPABILITIES = Map.of(
            "chat", new Capability(
                    TenantAiConfig::getApiKey, TenantAiConfig::setApiKey,
                    TenantAiConfig::getChatModel, TenantAiConfig::setChatModel),
            "image", new Capability(
                    TenantAiConfig::getImageApiKey, TenantAiConfig::setImageApiKey,
                    TenantAiConfig::getImageModel, TenantAiConfig::setImageModel),
            "video", new Capability(
                    TenantAiConfig::getVideoApiKey, TenantAiConfig::setVideoApiKey,
                    TenantAiConfig::getVideoModel, TenantAiConfig::setVideoModel),
            "videoDashscope", new Capability(
                    TenantAiConfig::getVideoDashscopeApiKey, TenantAiConfig::setVideoDashscopeApiKey,
                    config -> null, null),
            "tts", new Capability(
                    TenantAiConfig::getTtsApiKey, TenantAiConfig::setTtsApiKey,
                    config -> null, null),
            "search", new Capability(
                    TenantAiConfig::getSearchApiKey, TenantAiConfig::setSearchApiKey,
                    config -> null, null));

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
    public UserDtos.AiConfigEntry saveModel(String capability, UserDtos.UpdateModelRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        Capability cap = requireCapability(capability);
        String tenantId = tenantId();
        TenantAiConfig config = configRepository.findById(tenantId).orElseGet(TenantAiConfig::new);
        config.setTenantId(tenantId);

        if ("chat".equals(capability)) {
            applyChatModel(config, request);
        } else if ("image".equals(capability)) {
            applyMediaModel(config, request,
                    modelCatalog::imageProvider, TenantAiConfig::setImageProvider, cap.modelSetter());
        } else if ("video".equals(capability)) {
            applyMediaModel(config, request,
                    modelCatalog::videoProvider, TenantAiConfig::setVideoProvider, cap.modelSetter());
        } else {
            if (request.model() == null || request.model().isBlank()) {
                throw new IllegalArgumentException("模型不能为空");
            }
            if (cap.modelSetter() == null) {
                throw new IllegalArgumentException("该能力不支持模型自定义: " + capability);
            }
            cap.modelSetter().accept(config, request.model().trim());
        }
        configRepository.save(config);
        if ("chat".equals(capability)) {
            chatClientFactory.evict(tenantId);
        }
        return entry(Optional.of(config), capability);
    }

    @Transactional
    public void clearModel(String capability) {
        Capability cap = requireCapability(capability);
        if (cap.modelSetter() == null) {
            throw new IllegalArgumentException("该能力不支持模型自定义: " + capability);
        }
        String tenantId = tenantId();
        Optional<TenantAiConfig> config = configRepository.findById(tenantId);
        if (config.isEmpty()) {
            return;
        }
        cap.modelSetter().accept(config.get(), null);
        if ("chat".equals(capability)) {
            config.get().setChatProvider(null);
            config.get().setChatBaseUrl(null);
            chatClientFactory.evict(tenantId);
        } else if ("image".equals(capability)) {
            config.get().setImageProvider(null);
        } else if ("video".equals(capability)) {
            config.get().setVideoProvider(null);
        }
        configRepository.save(config.get());
    }

    public AiModelCatalog.Catalog catalog() {
        return modelCatalog.catalog();
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
                providerId(config, capability),
                config.map(cap.modelGetter()).orElse(null));
    }

    private String providerId(Optional<TenantAiConfig> config, String capability) {
        return switch (capability) {
            case "chat" -> config.map(TenantAiConfig::getChatProvider).orElse("ark");
            case "image" -> config.map(TenantAiConfig::getImageProvider).orElse("siliconflow");
            case "video" -> config.map(TenantAiConfig::getVideoProvider).orElse("ark");
            case "videoDashscope" -> "dashscope";
            case "tts" -> "tts";
            case "search" -> "search";
            default -> "";
        };
    }

    private void applyMediaModel(TenantAiConfig config, UserDtos.UpdateModelRequest request,
                                 Function<String, AiModelCatalog.MediaProvider> providerLookup,
                                 BiConsumer<TenantAiConfig, String> providerSetter,
                                 BiConsumer<TenantAiConfig, String> modelSetter) {
        if (request.provider() != null && !request.provider().isBlank()) {
            String provider = request.provider().trim();
            if (providerLookup.apply(provider) == null) {
                throw new IllegalArgumentException("未知服务商: " + provider);
            }
            providerSetter.accept(config, provider);
        }
        if (request.model() != null && !request.model().isBlank()) {
            modelSetter.accept(config, request.model().trim());
        }
    }

    private void applyChatModel(TenantAiConfig config, UserDtos.UpdateModelRequest request) {
        String provider = request.provider() == null ? null : request.provider().trim();
        if (provider != null && !provider.isBlank()) {
            AiModelCatalog.ChatProvider chatProvider = modelCatalog.provider(provider);
            if (chatProvider == null) {
                throw new IllegalArgumentException("未知服务商: " + provider);
            }
            config.setChatProvider(provider);
            if ("custom".equals(provider)) {
                if (request.baseUrl() == null || request.baseUrl().isBlank()) {
                    throw new IllegalArgumentException("自定义服务商必须填写 baseUrl");
                }
                config.setChatBaseUrl(request.baseUrl().trim());
            } else {
                config.setChatBaseUrl(chatProvider.baseUrl());
            }
        }
        if (request.model() != null && !request.model().isBlank()) {
            config.setChatModel(request.model().trim());
        }
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

    private record Capability(Function<TenantAiConfig, String> getter,
                              BiConsumer<TenantAiConfig, String> setter,
                              Function<TenantAiConfig, String> modelGetter,
                              BiConsumer<TenantAiConfig, String> modelSetter) {
    }
}
