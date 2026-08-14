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

    public record ModelOption(String name, boolean free) {
    }

    public record ChatProvider(String id, String name, String baseUrl, List<ModelOption> models) {
    }

    public record Catalog(List<ChatProvider> chatProviders,
                          List<ModelOption> imageModels,
                          List<ModelOption> videoModels) {
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

    private static final List<ModelOption> IMAGE_MODELS = List.of(
            new ModelOption("Kwai-Kolors/Kolors", true),
            new ModelOption("Kwai-Kolors/Kolors-2509", false),
            new ModelOption("black-forest-labs/FLUX.1-schnell", false),
            new ModelOption("black-forest-labs/FLUX.1-dev", false),
            new ModelOption("stabilityai/stable-diffusion-3-5-large", false));

    private static final List<ModelOption> VIDEO_MODELS = List.of(
            option("doubao-seedance-2-0-mini-260615"),
            option("doubao-seedance-1-5-pro-251215"),
            option("doubao-seedance-1-5-pro-250528"));

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

    private static ModelOption option(String name) {
        return new ModelOption(name, false);
    }
}
