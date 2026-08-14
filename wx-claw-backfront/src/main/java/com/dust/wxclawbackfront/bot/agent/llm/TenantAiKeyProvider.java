package com.dust.wxclawbackfront.bot.agent.llm;

import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.entity.TenantAiConfig;
import com.dust.wxclawbackfront.tenancy.repository.TenantAiConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.function.Function;

/**
 * 按租户解析各 AI 能力的 API Key：用户配置优先，未配置回退后端默认值。
 *
 * <p>所有能力均支持"用户 Key 覆盖默认 Key"：调用方（图片/视频/TTS/搜索等）在每次调用时
 * 通过本服务取 Key，因此配置修改后无需重启即可生效。</p>
 */
@Service
@RequiredArgsConstructor
public class TenantAiKeyProvider {

    private final TenantAiConfigRepository configRepository;
    private final AiModelCatalog modelCatalog;

    @Value("${spring.ai.openai.api-key:}")
    private String defaultChatKey;

    @Value("${spring.ai.openai.base-url:https://ark.cn-beijing.volces.com/api/v3}")
    private String defaultChatBaseUrl;

    @Value("${spring.ai.openai.chat.model:}")
    private String defaultChatModel;

    @Value("${wxclaw.ai.image.generate.api-key:${spring.ai.openai.api-key:}}")
    private String defaultImageKey;

    @Value("${wxclaw.ai.image.generate.provider:siliconflow}")
    private String defaultImageProvider;

    @Value("${wxclaw.ai.image.generate.model:Kwai-Kolors/Kolors}")
    private String defaultImageModel;

    @Value("${wxclaw.ai.video-gen.dashscope.api-key:}")
    private String defaultVideoDashscopeKey;

    @Value("${wxclaw.ai.video-gen.ark.model:doubao-seedance-2-0-mini-260615}")
    private String defaultVideoModel;

    @Value("${wxclaw.ai.video-gen.ark.api-key:${spring.ai.openai.api-key:}}")
    private String defaultVideoKey;

    @Value("${wxclaw.ai.video-gen.provider:ark}")
    private String defaultVideoProvider;

    @Value("${wxclaw.ai.video-gen.openai.api-key:}")
    private String defaultOpenaiVideoKey;

    @Value("${wxclaw.ai.video-gen.openai.model:sora-2}")
    private String defaultOpenaiVideoModel;

    @Value("${wxclaw.ai.video-gen.openai.base-url:https://api.openai.com/v1}")
    private String defaultOpenaiVideoBaseUrl;

    @Value("${wxclaw.ai.tts.api-key:}")
    private String defaultTtsKey;

    @Value("${wxclaw.ai.web-search.bocha.api-key:}")
    private String defaultSearchKey;

    /** 文本对话/理解（火山方舟 OpenAI 兼容）。 */
    public String chatKey() {
        return chatKeyFor(currentTenantId());
    }

    public String chatKeyFor(String tenantId) {
        return resolve(tenantId, TenantAiConfig::getApiKey, defaultChatKey);
    }

    /** 聊天服务商（默认 ark）。 */
    public String chatProvider() {
        return chatProviderFor(currentTenantId());
    }

    public String chatProviderFor(String tenantId) {
        return resolve(tenantId, TenantAiConfig::getChatProvider, "ark");
    }

    /** 聊天服务商对应 baseUrl（custom 时取用户填写的地址）。 */
    public String chatBaseUrl() {
        return chatBaseUrlFor(currentTenantId());
    }

    public String chatBaseUrlFor(String tenantId) {
        String tenantBase = resolve(tenantId, TenantAiConfig::getChatBaseUrl, null);
        if (tenantBase != null && !tenantBase.isBlank()) {
            return tenantBase;
        }
        return modelCatalog.baseUrlFor(chatProviderFor(tenantId), defaultChatBaseUrl);
    }

    /** 聊天模型。 */
    public String chatModel() {
        return chatModelFor(currentTenantId());
    }

    public String chatModelFor(String tenantId) {
        return resolve(tenantId, TenantAiConfig::getChatModel, defaultChatModel);
    }

    /** 图片生成 Key（按用户所选服务商 siliconflow/ark/openai 使用）。 */
    public String imageKey() {
        return resolve(currentTenantId(), TenantAiConfig::getImageApiKey, defaultImageKey);
    }

    /** 图片生成服务商。 */
    public String imageProvider() {
        return imageProviderFor(currentTenantId());
    }

    public String imageProviderFor(String tenantId) {
        return resolve(tenantId, TenantAiConfig::getImageProvider, defaultImageProvider);
    }

    /** 图片生成服务商对应 baseUrl。 */
    public String imageBaseUrlFor(String providerId) {
        return modelCatalog.imageBaseUrl(providerId, "https://api.siliconflow.cn/v1");
    }

    /** 图片生成模型（按用户所选服务商）。 */
    public String imageModel() {
        return resolve(currentTenantId(), TenantAiConfig::getImageModel, defaultImageModel);
    }

    /** 视频生成服务商：租户配置优先，未配置回退后端默认（ark）。 */
    public String videoProvider() {
        return videoProviderFor(currentTenantId());
    }

    public String videoProviderFor(String tenantId) {
        return resolve(tenantId, TenantAiConfig::getVideoProvider, defaultVideoProvider);
    }

    /** 视频生成服务商对应 baseUrl。 */
    public String videoBaseUrlFor(String providerId) {
        if ("openai".equalsIgnoreCase(providerId)) {
            return defaultOpenaiVideoBaseUrl;
        }
        return modelCatalog.videoBaseUrl(providerId, defaultChatBaseUrl);
    }

    /**
     * 视频生成 Key（按服务商取对应 Key 字段）：
     * <ol>
     *   <li>用户配置的对应字段 Key 优先（ark/openai 用 video_api_key，dashscope 用 video_dashscope_api_key）；</li>
     *   <li>服务商为 openai 时回退后端默认 OpenAI 视频 Key；</li>
     *   <li>服务商为 dashscope 时回退后端默认 DashScope Key；</li>
     *   <li>服务商为 ark 且对话服务商为 ark 时，复用对话 Ark Key；</li>
     *   <li>否则回退后端默认 Ark Key。</li>
     * </ol>
     */
    public String videoKey() {
        String provider = videoProvider();
        String tenantKey;
        if ("dashscope".equalsIgnoreCase(provider)) {
            tenantKey = resolve(currentTenantId(), TenantAiConfig::getVideoDashscopeApiKey, null);
        } else {
            tenantKey = resolve(currentTenantId(), TenantAiConfig::getVideoApiKey, null);
        }
        if (tenantKey != null && !tenantKey.isBlank()) {
            return tenantKey;
        }
        if ("openai".equalsIgnoreCase(provider)) {
            return defaultOpenaiVideoKey;
        }
        if ("dashscope".equalsIgnoreCase(provider)) {
            return defaultVideoDashscopeKey;
        }
        if ("ark".equalsIgnoreCase(provider) && "ark".equalsIgnoreCase(chatProvider())) {
            return chatKey();
        }
        return defaultVideoKey;
    }

    /** 仅返回租户自定义的视频模型；未配置返回 null。 */
    public String tenantVideoModel() {
        return resolve(currentTenantId(), TenantAiConfig::getVideoModel, null);
    }

    /** 视频生成模型：租户配置优先；openai 服务商回退 sora，否则回退 Seedance。 */
    public String videoModel() {
        String tenantModel = resolve(currentTenantId(), TenantAiConfig::getVideoModel, null);
        if (tenantModel != null && !tenantModel.isBlank()) {
            return tenantModel;
        }
        return "openai".equalsIgnoreCase(videoProvider()) ? defaultOpenaiVideoModel : defaultVideoModel;
    }

    /** 视频生成（阿里云通义万相 DashScope）。 */
    public String videoDashscopeKey() {
        return resolve(currentTenantId(), TenantAiConfig::getVideoDashscopeApiKey, defaultVideoDashscopeKey);
    }

    /** 语音合成（火山引擎 TTS）。 */
    public String ttsKey() {
        return resolve(currentTenantId(), TenantAiConfig::getTtsApiKey, defaultTtsKey);
    }

    /** 联网搜索（博查）。 */
    public String searchKey() {
        return resolve(currentTenantId(), TenantAiConfig::getSearchApiKey, defaultSearchKey);
    }

    private String resolve(String tenantId, Function<TenantAiConfig, String> getter, String fallback) {
        if (tenantId == null || tenantId.isBlank()) {
            return fallback;
        }
        return configRepository.findById(tenantId)
                .map(getter)
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .orElse(fallback);
    }

    private String currentTenantId() {
        TenantContext context = TenantContextHolder.getNullable();
        return context == null ? null : context.tenantId();
    }
}
