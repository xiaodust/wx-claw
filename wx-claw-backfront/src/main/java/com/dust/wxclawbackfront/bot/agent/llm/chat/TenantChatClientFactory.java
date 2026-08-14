package com.dust.wxclawbackfront.bot.agent.llm.chat;

import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.entity.TenantAiConfig;
import com.dust.wxclawbackfront.tenancy.repository.TenantAiConfigRepository;
import com.openai.client.OpenAIClient;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 按租户解析 {@link ChatClient}：用户配置了自己的 API Key 时使用独立 client（缓存），
 * 否则回退到后端默认 client。
 *
 * <p>Key 更新后调用 {@link #evict(String)} 清除缓存，使新 Key 立即生效。</p>
 */
@Slf4j
@Service
public class TenantChatClientFactory {

    private final ChatClient defaultClient;
    private final TenantAiConfigRepository configRepository;
    private final ConcurrentMap<String, ChatClient> tenantClients = new ConcurrentHashMap<>();

    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final int maxRetries;

    public TenantChatClientFactory(ChatClient.Builder chatClientBuilder,
                                   TenantAiConfigRepository configRepository,
                                   @Value("${spring.ai.openai.base-url:https://ark.cn-beijing.volces.com/api/v3}") String baseUrl,
                                   @Value("${spring.ai.openai.chat.model:}") String model,
                                   @Value("${wxclaw.ai.chat.timeout:PT25S}") Duration timeout,
                                   @Value("${spring.ai.openai.max-retries:2}") int maxRetries) {
        this.defaultClient = chatClientBuilder.build();
        this.configRepository = configRepository;
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeout = timeout == null ? Duration.ofSeconds(25) : timeout;
        this.maxRetries = maxRetries;
    }

    /**
     * 当前租户（来自 {@link TenantContextHolder}）对应的 ChatClient。
     */
    public ChatClient currentClient() {
        return clientFor(TenantContextHolder.require().tenantId());
    }

    public ChatClient clientFor(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return defaultClient;
        }
        Optional<TenantAiConfig> config = configRepository.findById(tenantId);
        String apiKey = config.map(TenantAiConfig::getApiKey)
                .map(String::trim)
                .filter(key -> !key.isEmpty())
                .orElse(null);
        if (apiKey == null) {
            return defaultClient;
        }
        return tenantClients.computeIfAbsent(tenantId, id -> {
            log.info("为租户 {} 构建独立 LLM client", id);
            return buildClient(apiKey);
        });
    }

    /**
     * 清除指定租户的 client 缓存，配置变更后调用。
     */
    public void evict(String tenantId) {
        if (tenantId != null) {
            tenantClients.remove(tenantId);
        }
    }

    protected ChatClient buildClient(String apiKey) {
        OpenAIClient openAiClient = OpenAiSetup.setupSyncClient(
                baseUrl, apiKey, null, null, null, null,
                false, false, model, timeout, maxRetries,
                null, null, ObservationRegistry.NOOP, null, List.of());
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiClient(openAiClient)
                .options(OpenAiChatOptions.builder().model(model).build())
                .build();
        return ChatClient.builder(chatModel).build();
    }
}
