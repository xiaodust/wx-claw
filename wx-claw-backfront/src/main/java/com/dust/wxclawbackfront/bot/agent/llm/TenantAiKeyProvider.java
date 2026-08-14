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

    @Value("${spring.ai.openai.api-key:}")
    private String defaultChatKey;

    @Value("${wxclaw.ai.image.generate.api-key:${spring.ai.openai.api-key:}}")
    private String defaultImageKey;

    @Value("${wxclaw.ai.video-gen.dashscope.api-key:}")
    private String defaultVideoDashscopeKey;

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

    /** 图片生成（SiliconFlow）。 */
    public String imageKey() {
        return resolve(currentTenantId(), TenantAiConfig::getImageApiKey, defaultImageKey);
    }

    /**
     * 视频生成（火山方舟 Seedance）：与对话共用同一个 Ark API Key，
     * 无需用户单独配置。
     */
    public String videoKey() {
        return chatKey();
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
