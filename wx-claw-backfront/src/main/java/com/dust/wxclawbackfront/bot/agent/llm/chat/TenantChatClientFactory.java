package com.dust.wxclawbackfront.bot.agent.llm.chat;

import com.dust.wxclawbackfront.bot.agent.llm.TenantAiKeyProvider;
import com.dust.wxclawbackfront.exception.WxClawException;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.openai.client.OpenAIClient;
import com.openai.client.OpenAIClientAsync;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 按租户解析 {@link ChatClient}：租户必须配置自己的 API Key，否则拒绝调用。
 *
 * <p>Key 更新后调用 {@link #evict(String)} 清除缓存，使新 Key 立即生效。</p>
 */
@Slf4j
@Service
public class TenantChatClientFactory {

    private final TenantAiKeyProvider keyProvider;
    private final ConcurrentMap<String, ChatClient> tenantClients = new ConcurrentHashMap<>();

    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final int maxRetries;

    public TenantChatClientFactory(TenantAiKeyProvider keyProvider,
                                   @Value("${spring.ai.openai.base-url:https://ark.cn-beijing.volces.com/api/v3}") String baseUrl,
                                   @Value("${spring.ai.openai.chat.model:}") String model,
                                   @Value("${wxclaw.ai.chat.timeout:PT25S}") Duration timeout,
                                   @Value("${spring.ai.openai.max-retries:2}") int maxRetries) {
        this.keyProvider = keyProvider;
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
        String apiKey = keyProvider.chatKeyFor(tenantId);
        if (apiKey == null || apiKey.isBlank()) {
            throw new WxClawException("AI_CONFIG_MISSING",
                    "对话功能未配置 API Key，请在用户控制台「设置」页配置对话 API Key 后重试");
        }
        return tenantClients.computeIfAbsent(tenantId, id -> {
            log.info("为租户 {} 构建独立 LLM client", id);
            return buildClient(apiKey, keyProvider.chatBaseUrlFor(id), keyProvider.chatModelFor(id));
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

    protected ChatClient buildClient(String apiKey, String baseUrl, String model) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("未配置 LLM API Key：请在后端 application.yml 配置 spring.ai.openai.api-key，或让用户在设置页配置对话 API Key");
        }
        OpenAIClient openAiClient = OpenAiSetup.setupSyncClient(
                baseUrl, apiKey, null, null, null, null,
                false, false, model, timeout, maxRetries,
                null, null, ObservationRegistry.NOOP, null, List.of());
        // OpenAiChatModel 构建时若未显式提供 async client，会退回用 options.apiKey（此处为 null）重建并抛"缺少凭据"，
        // 因此同步/异步 client 必须同时传入
        OpenAIClientAsync openAiClientAsync = OpenAiSetup.setupAsyncClient(
                baseUrl, apiKey, null, null, null, null,
                false, false, model, timeout, maxRetries,
                null, null, ObservationRegistry.NOOP, null, List.of());
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiClient(openAiClient)
                .openAiClientAsync(openAiClientAsync)
                .options(OpenAiChatOptions.builder().model(model).build())
                .build();
        return ChatClient.builder(chatModel).build();
    }
}
