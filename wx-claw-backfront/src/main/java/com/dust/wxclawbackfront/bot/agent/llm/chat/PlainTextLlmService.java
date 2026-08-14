package com.dust.wxclawbackfront.bot.agent.llm.chat;

import com.dust.wxclawbackfront.observability.llm.service.LlmInvocationRecorder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 纯文本 LLM 调用服务
 * 不挂载任何 tools，用于定时任务、摘要压缩等后台场景，避免引入工具链循环依赖。
 */
@Slf4j
@Service
public class PlainTextLlmService {

    private final TenantChatClientFactory chatClientFactory;
    private final LlmInvocationRecorder invocationRecorder;
    private final ObjectMapper objectMapper;
    private OpenAiChatOptions.Builder cachedOptionsBuilder;

    @Value("${spring.ai.openai.chat.model:}")
    private String model;

    @Value("${wxclaw.ai.thinking.type:disabled}")
    private String thinkingType;

    @Value("${wxclaw.ai.chat.max-tokens:1024}")
    private int maxTokens;

    @Value("${wxclaw.ai.chat.timeout:PT35S}")
    private Duration timeout;

    public PlainTextLlmService(TenantChatClientFactory chatClientFactory,
                               LlmInvocationRecorder invocationRecorder,
                               ObjectMapper objectMapper) {
        this.chatClientFactory = chatClientFactory;
        this.invocationRecorder = invocationRecorder;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        this.cachedOptionsBuilder = LlmOptionsBuilder.builder()
                .model(model)
                .thinkingType(thinkingType)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .buildBuilder();
    }

    public String chat(String prompt) {
        return chat(prompt, "PLAIN_TEXT");
    }

    public String chat(String prompt, String invocationType) {
        long start = System.currentTimeMillis();
        LlmInvocationRecorder.InvocationHandle handle = invocationRecorder.start(
                invocationType, "OPENAI_COMPATIBLE", model, requestPayload(prompt));
        try {
            ChatResponse response = chatClientFactory.currentClient().prompt()
                    .options(cachedOptionsBuilder)
                    .user(prompt)
                    .call()
                    .chatResponse();
            String content = response == null || response.getResult() == null
                    ? null : response.getResult().getOutput().getText();
            Usage usage = response == null || response.getMetadata() == null
                    ? null : response.getMetadata().getUsage();
            invocationRecorder.success(handle, responsePayload(response, content), null,
                    usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens());
            long elapsed = System.currentTimeMillis() - start;
            log.info("LLM响应完成, 耗时={}ms, model={}", elapsed, model);
            return content;
        } catch (RuntimeException ex) {
            invocationRecorder.failure(handle, ex);
            throw ex;
        }
    }

    private String requestPayload(String prompt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("thinkingType", thinkingType);
        payload.put("maxTokens", maxTokens);
        payload.put("timeout", timeout.toString());
        payload.put("user", prompt);
        return toJson(payload);
    }

    private String responsePayload(ChatResponse response, String content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", content);
        payload.put("metadata", response == null ? null : String.valueOf(response.getMetadata()));
        payload.put("results", response == null ? null : String.valueOf(response.getResults()));
        return toJson(payload);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }
}
