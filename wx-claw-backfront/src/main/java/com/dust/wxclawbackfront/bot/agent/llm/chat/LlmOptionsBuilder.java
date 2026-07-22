package com.dust.wxclawbackfront.bot.agent.llm.chat;

import org.springframework.ai.openai.OpenAiChatOptions;

import java.time.Duration;
import java.util.Map;

/**
 * LLM Options 构建器
 * 封装 OpenAiChatOptions 的构建逻辑，避免重复代码
 */
public class LlmOptionsBuilder {

    private final OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder();

    private LlmOptionsBuilder() {
    }

    /**
     * 创建构建器实例
     */
    public static LlmOptionsBuilder builder() {
        return new LlmOptionsBuilder();
    }

    /**
     * 设置模型名称
     */
    public LlmOptionsBuilder model(String model) {
        if (model != null && !model.isBlank()) {
            optionsBuilder.model(model);
        }
        return this;
    }

    /**
     * 设置思考类型
     */
    public LlmOptionsBuilder thinkingType(String thinkingType) {
        if (thinkingType != null && !thinkingType.isBlank()) {
            optionsBuilder.extraBody(Map.of("thinking", Map.of("type", thinkingType.trim())));
        }
        return this;
    }

    /**
     * 设置最大 token 数
     */
    public LlmOptionsBuilder maxTokens(int maxTokens) {
        if (maxTokens > 0) {
            optionsBuilder.maxTokens(maxTokens);
        }
        return this;
    }

    /**
     * 设置超时时间
     */
    public LlmOptionsBuilder timeout(Duration timeout) {
        if (timeout != null) {
            optionsBuilder.timeout(timeout);
        }
        return this;
    }

    /**
     * 构建 OpenAiChatOptions
     */
    public OpenAiChatOptions build() {
        return optionsBuilder.build();
    }

    /**
     * 获取内部的 Builder（用于需要传给 ChatClient.options() 的场景）
     */
    public OpenAiChatOptions.Builder buildBuilder() {
        return optionsBuilder;
    }
}
