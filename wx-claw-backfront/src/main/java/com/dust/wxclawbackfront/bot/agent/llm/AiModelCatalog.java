package com.dust.wxclawbackfront.bot.agent.llm;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 各能力可选的模型目录（与用户 API Key 的服务商对应）。
 *
 * <p>聊天能力按服务商（ark/openai/deepseek/zhipu/custom）提供 baseUrl 与模型列表，
 * 图片/视频同样按服务商分组（siliconflow/ark/openai，视频另含 dashscope），
 * 用户选择哪个服务商，模型下拉就只显示该服务商的模型，保证"模型与 Key 对上"。</p>
 */
@Component
public class AiModelCatalog {

    public record ModelOption(String name, boolean free) {
    }

    public record ChatProvider(String id, String name, String baseUrl, List<ModelOption> models) {
    }

    public record MediaProvider(String id, String name, String baseUrl, List<ModelOption> models) {
    }

    public record Catalog(List<ChatProvider> chatProviders,
                          List<MediaProvider> imageProviders,
                          List<MediaProvider> videoProviders) {
    }

    private static final List<ChatProvider> CHAT_PROVIDERS = List.of(
            new ChatProvider("ark", "火山方舟", "https://ark.cn-beijing.volces.com/api/v3",
                    List.of(option("doubao-seed-2-1-turbo-260628"),
                            option("doubao-seed-2-1-pro-260628"),
                            option("doubao-seed-2-0-mini-260615"),
                            option("doubao-seed-1-6-250615"),
                            option("doubao-1-5-pro-32k-250115"))),
            new ChatProvider("openai", "OpenAI", "https://api.openai.com/v1",
                    List.of(option("gpt-5.4"), option("gpt-5.4-mini"), option("gpt-5.4-nano"),
                            option("gpt-5.2"), option("gpt-5"), option("gpt-5-mini"), option("gpt-5-nano"),
                            option("gpt-4o"), option("gpt-4o-mini"),
                            option("gpt-4.1"), option("gpt-4.1-mini"), option("o3-mini"))),
            new ChatProvider("deepseek", "DeepSeek", "https://api.deepseek.com/v1",
                    List.of(option("deepseek-v4-pro"), option("deepseek-v4-flash"),
                            option("deepseek-chat"), option("deepseek-reasoner"))),
            new ChatProvider("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4",
                    List.of(option("glm-4.6"), option("glm-4.5"), option("glm-4.5-air"), option("glm-4.5-flash"),
                            option("glm-4-plus"), option("glm-4-air"),
                            option("glm-4-flash"), option("glm-4-long"))),
            new ChatProvider("custom", "自定义（OpenAI 兼容）", "",
                    List.of()));

    private static final List<MediaProvider> IMAGE_PROVIDERS = List.of(
            new MediaProvider("siliconflow", "SiliconFlow", "https://api.siliconflow.cn/v1",
                    List.of(new ModelOption("Kwai-Kolors/Kolors", true),
                            new ModelOption("Kwai-Kolors/Kolors-2509", false),
                            new ModelOption("black-forest-labs/FLUX.1-schnell", false),
                            new ModelOption("black-forest-labs/FLUX.1-dev", false),
                            new ModelOption("stabilityai/stable-diffusion-3-5-large", false))),
            new MediaProvider("ark", "火山方舟", "https://ark.cn-beijing.volces.com/api/v3",
                    List.of(option("doubao-seedream-5-0-260128"),
                            option("doubao-seedream-4-5-251128"),
                            option("doubao-seedream-4-0-250828"),
                            option("doubao-seedream-3-0-t2i-250415"))),
            new MediaProvider("openai", "OpenAI", "https://api.openai.com/v1",
                    List.of(option("gpt-image-1"),
                            option("gpt-image-1-mini"),
                            option("dall-e-3"))));

    private static final List<MediaProvider> VIDEO_PROVIDERS = List.of(
            new MediaProvider("ark", "火山方舟 Seedance", "https://ark.cn-beijing.volces.com/api/v3",
                    List.of(option("doubao-seedance-2-0-mini-260615"),
                            option("doubao-seedance-1-5-pro-251215"),
                            option("doubao-seedance-1-5-pro-250528"))),
            new MediaProvider("openai", "OpenAI Sora", "https://api.openai.com/v1",
                    List.of(option("sora-2"),
                            option("sora-2-pro"))),
            new MediaProvider("dashscope", "阿里云通义万相", "https://dashscope.aliyuncs.com",
                    List.of(option("wan2.7-t2v-2026-06-12"),
                            option("wan2.7-i2v-2026-04-25"))));

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

    /** 图片服务商 baseUrl；未匹配时返回 fallback。 */
    public String imageBaseUrl(String providerId, String fallback) {
        return mediaBaseUrl(IMAGE_PROVIDERS, providerId, fallback);
    }

    /** 视频服务商 baseUrl；未匹配时返回 fallback。 */
    public String videoBaseUrl(String providerId, String fallback) {
        return mediaBaseUrl(VIDEO_PROVIDERS, providerId, fallback);
    }

    /** 校验并返回图片服务商；未知返回 null。 */
    public MediaProvider imageProvider(String id) {
        return mediaProvider(IMAGE_PROVIDERS, id);
    }

    /** 校验并返回视频服务商；未知返回 null。 */
    public MediaProvider videoProvider(String id) {
        return mediaProvider(VIDEO_PROVIDERS, id);
    }

    public Catalog catalog() {
        return new Catalog(CHAT_PROVIDERS, IMAGE_PROVIDERS, VIDEO_PROVIDERS);
    }

    private static ModelOption option(String name) {
        return new ModelOption(name, false);
    }

    private static String mediaBaseUrl(List<MediaProvider> providers, String id, String fallback) {
        MediaProvider provider = mediaProvider(providers, id);
        if (provider == null || provider.baseUrl().isBlank()) {
            return fallback;
        }
        return provider.baseUrl();
    }

    private static MediaProvider mediaProvider(List<MediaProvider> providers, String id) {
        if (id == null) {
            return null;
        }
        return providers.stream()
                .filter(p -> p.id().equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }
}
