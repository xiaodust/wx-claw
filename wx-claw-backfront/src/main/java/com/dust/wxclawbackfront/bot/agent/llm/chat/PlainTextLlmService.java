package com.dust.wxclawbackfront.bot.agent.llm.chat;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 纯文本 LLM 调用服务
 * 不挂载任何 tools，用于定时任务、摘要压缩等后台场景，避免引入工具链循环依赖。
 */
@Slf4j
@Service
public class PlainTextLlmService {

    private final ChatClient chatClient;
    private OpenAiChatOptions.Builder cachedOptionsBuilder;

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
        long start = System.currentTimeMillis();
        String content = chatClient.prompt()
                .options(cachedOptionsBuilder)
                .user(prompt)
                .call()
                .content();
        long elapsed = System.currentTimeMillis() - start;
        log.info("LLM响应完成, 耗时={}ms, model={}", elapsed, model);
        return content;
    }
}
