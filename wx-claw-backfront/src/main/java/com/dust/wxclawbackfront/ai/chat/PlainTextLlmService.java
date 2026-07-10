package com.dust.wxclawbackfront.ai.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;

/**
 * 纯文本 LLM 调用服务
 * 不挂载任何 tools，用于定时任务、摘要压缩等后台场景，避免引入工具链循环依赖。
 */
@Service
public class PlainTextLlmService {

    private final ChatClient chatClient;

    @Value("${spring.ai.openai.chat.model:}")
    private String model;

    @Value("${wxclaw.ai.thinking.type:disabled}")
    private String thinkingType;

    @Value("${wxclaw.ai.chat.max-tokens:1024}")
    private int maxTokens;

    @Value("${wxclaw.ai.chat.timeout:PT35S}")
    private Duration timeout;

    public PlainTextLlmService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public String chat(String prompt) {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder();

        if (model != null && !model.isBlank()) {
            optionsBuilder = optionsBuilder.model(model);
        }
        if (thinkingType != null && !thinkingType.isBlank()) {
            optionsBuilder = optionsBuilder.extraBody(Map.of("thinking", Map.of("type", thinkingType.trim())));
        }
        if (maxTokens > 0) {
            optionsBuilder = optionsBuilder.maxTokens(maxTokens);
        }
        if (timeout != null) {
            optionsBuilder = optionsBuilder.timeout(timeout);
        }

        return spec
                .options(optionsBuilder)
                .user(prompt)
                .call()
                .content();
    }
}
