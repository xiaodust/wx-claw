package com.dust.wxclawbackfront.bot.agent.llm;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 各能力可选的模型目录（与用户 API Key 的服务商对应）。
 *
 * <p>聊天能力按服务商（ark/openai/deepseek/zhipu/custom）提供 baseUrl 与模型列表，
 * 用户选择哪个服务商，模型下拉就只显示该服务商的模型，保证"模型与 Key 对上"；
 * 图片/视频为固定服务商，直接提供该服务商支持的模型列表。</p>
 */
@Component
public class AiModelCatalog {

    public record ChatProvider(String id, String name, String baseUrl, List<String> models) {
    }

    public record Catalog(List<ChatProvider> chatProviders,
                          List<String> imageModels,
                          List<String> videoModels) {
    }

    private static final List<ChatProvider> CHAT_PROVIDERS = List.of(
            new ChatProvider("ark", "火山方舟", "https://ark.cn-beijing.volces.com/api/v3",
                    List.of("doubao-seed-2-1-turbo-260628",
                            "doubao-seed-2-1-pro-260628",
                            "doubao-seed-2-0-mini-260615",
                            "doubao-seed-1-6-250615",
                            "doubao-1-5-pro-32k-250115")),
            new ChatProvider("openai", "OpenAI", "https://api.openai.com/v1",
                    List.of("gpt-4o", "gpt-4o-mini", "gpt-4.1", "gpt-4.1-mini", "o3-mini")),
            new ChatProvider("deepseek", "DeepSeek", "https://api.deepseek.com/v1",
                    List.of("deepseek-chat", "deepseek-reasoner")),
            new ChatProvider("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4",
                    List.of("glm-4-plus", "glm-4-air", "glm-4-flash", "glm-4-long")),
            new ChatProvider("custom", "自定义（OpenAI 兼容）", "",
                    List.of()));

    private static final List<String> IMAGE_MODELS = List.of(
            "Kwai-Kolors/Kolors",
            "Kwai-Kolors/Kolors-2509",
            "black-forest-labs/FLUX.1-schnell",
            "black-forest-labs/FLUX.1-dev",
            "stabilityai/stable-diffusion-3-5-large");

    private static final List<String> VIDEO_MODELS = List.of(
            "doubao-seedance-2-0-mini-260615",
            "doubao-seedance-1-5-pro-251215",
            "doubao-seedance-1-5-pro-250528");

    public ChatProvider provider(String id) {
        if (id == null) {
            return null;
        }
        return CHAT_PROVIDERS.stream()
                .filter(p -> p.id().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    /**
     * 服务商对应 baseUrl；custom 或未匹配时返回 fallback。
     */
    public String baseUrlFor(String providerId, String fallback) {
        ChatProvider provider = provider(providerId);
        if (provider == null || provider.baseUrl().isBlank()) {
            return fallback;
        }
        return provider.baseUrl();
    }

    public Catalog catalog() {
        return new Catalog(CHAT_PROVIDERS, IMAGE_MODELS, VIDEO_MODELS);
    }
}
