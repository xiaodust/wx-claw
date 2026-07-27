package com.dust.wxclawbackfront.bot.agent.llm.chat;

import com.dust.wxclawbackfront.bot.agent.llm.LlmToolRegistry;
import com.dust.wxclawbackfront.bot.agent.llm.SkillLoader;
import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import com.dust.wxclawbackfront.bot.agent.tools.memory.UserMemoryService;
import com.dust.wxclawbackfront.bot.agent.tools.shared.AiToolInvocationStore;
import com.dust.wxclawbackfront.bot.agent.tools.shared.UserContextHolder;
import com.dust.wxclawbackfront.observability.llm.service.LlmInvocationRecorder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * 通用聊天处理器
 * 使用 Spring AI 原生 function calling，模型自主决定调用哪些工具
 */
@Slf4j
@Service
public class UniversalChatHandler implements ChatHandler {
    private final ChatClient chatClient;
    private final AiToolInvocationStore toolInvocationStore;
    private final ChatRequestBuilder requestBuilder;
    private final LlmToolRegistry toolRegistry;
    private final SkillLoader skillLoader;
    private final UserMemoryService userMemoryService;
    private final ExecutorService promptExecutor;
    private final LlmInvocationRecorder invocationRecorder;
    private final ObjectMapper objectMapper;

    @Value("${spring.ai.openai.chat.model:}")
    private String model;

    @Value("${wxclaw.ai.thinking.type:disabled}")
    private String thinkingType;

    @Value("${wxclaw.ai.chat.max-tokens:768}")
    private int maxTokens;

    @Value("${wxclaw.ai.chat.timeout:PT25S}")
    private Duration timeout;

    @Value("${wxclaw.ai.context.max-chars:7000}")
    private int maxContextChars;

    @Value("${wxclaw.ai.context.max-message-chars:800}")
    private int maxMessageChars;

    public UniversalChatHandler(ChatClient.Builder chatClientBuilder,
                                AiToolInvocationStore toolInvocationStore,
                                ChatRequestBuilder requestBuilder,
                                LlmToolRegistry toolRegistry,
                                SkillLoader skillLoader,
                                UserMemoryService userMemoryService,
                                @Qualifier("promptExecutor") ExecutorService promptExecutor,
                                LlmInvocationRecorder invocationRecorder,
                                ObjectMapper objectMapper,
                                @Value("${spring.ai.openai.chat.model:}") String model,
                                @Value("${wxclaw.ai.thinking.type:disabled}") String thinkingType,
                                @Value("${wxclaw.ai.chat.max-tokens:768}") int maxTokens,
                                @Value("${wxclaw.ai.chat.timeout:PT25S}") Duration timeout,
                                @Value("${wxclaw.ai.context.max-chars:7000}") int maxContextChars,
                                @Value("${wxclaw.ai.context.max-message-chars:800}") int maxMessageChars) {
        this.chatClient = chatClientBuilder.build();
        this.toolInvocationStore = toolInvocationStore;
        this.requestBuilder = requestBuilder;
        this.toolRegistry = toolRegistry;
        this.skillLoader = skillLoader;
        this.userMemoryService = userMemoryService;
        this.promptExecutor = promptExecutor;
        this.invocationRecorder = invocationRecorder;
        this.objectMapper = objectMapper;
        this.model = model;
        this.thinkingType = thinkingType;
        this.maxTokens = maxTokens;
        this.timeout = timeout;
        this.maxContextChars = maxContextChars;
        this.maxMessageChars = maxMessageChars;
    }

    @Override
    public String chat(String userMessage, List<AiMessage> historyMessages) {
        // 1. 构建请求文本
        String requestText = requestBuilder.buildRequestText(userMessage, historyMessages, maxContextChars, maxMessageChars);

        // 2. 重置工具调用记录
        if (toolInvocationStore != null) {
            toolInvocationStore.reset();
        }

        // 3. 并行获取 skill prompt 和 memory prompt
        String userId = UserContextHolder.getUserId();
        CompletableFuture<String> skillFuture = CompletableFuture.supplyAsync(
                () -> skillLoader.getSkillSystemPrompt(), promptExecutor);
        CompletableFuture<String> memoryFuture = CompletableFuture.supplyAsync(
                () -> userMemoryService.buildMemoryPrompt(userId), promptExecutor);

        String skillPrompt = skillFuture.join();
        String memoryPrompt = memoryFuture.join();

        // 4. 构建 system prompt
        StringBuilder systemPromptBuilder = new StringBuilder();

        // 微信聊天场景：要求纯文本回复
        if (skillPrompt != null && !skillPrompt.isBlank()) {
            systemPromptBuilder.append(skillPrompt);
        }
        if (memoryPrompt != null && !memoryPrompt.isBlank()) {
            systemPromptBuilder.append(memoryPrompt);
        }
        String systemPrompt = systemPromptBuilder.toString();

        // 5. 使用 Spring AI 原生 function calling（模型自主决定调用工具）
        String content = callWithTools(requestText, systemPrompt);

        return content;
    }

    /**
     * 使用 Spring AI 原生 function calling 调用 LLM
     */
    private String callWithTools(String requestText, String systemPrompt) {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt();

        var optionsBuilder = LlmOptionsBuilder.builder()
                .model(model)
                .thinkingType(thinkingType)
                .maxTokens(maxTokens)
                .timeout(timeout)
                .buildBuilder();

        spec = spec.options(optionsBuilder);
        spec = spec.tools(toolRegistry.getAllTools());

        ChatClient.ChatClientRequestSpec finalSpec;
        if (!systemPrompt.isBlank()) {
            finalSpec = spec.system(systemPrompt).user(requestText);
        } else {
            finalSpec = spec.user(requestText);
        }

        long start = System.currentTimeMillis();
        LlmInvocationRecorder.InvocationHandle handle = invocationRecorder.start(
                "CHAT", "OPENAI_COMPATIBLE", model, requestPayload(requestText, systemPrompt));
        try {
            ChatResponse response = finalSpec.call().chatResponse();
            String content = response == null || response.getResult() == null
                    ? null : response.getResult().getOutput().getText();
            List<AiToolInvocationStore.Invocation> toolInvocations = drainToolInvocations();
            Usage usage = response == null || response.getMetadata() == null
                    ? null : response.getMetadata().getUsage();
            invocationRecorder.success(handle, responsePayload(response, content), toJson(toolInvocations),
                    usage == null ? null : usage.getPromptTokens(),
                    usage == null ? null : usage.getCompletionTokens());
            long elapsed = System.currentTimeMillis() - start;
            log.info("LLM响应完成, 耗时={}ms, model={}", elapsed, model);
            return content;
        } catch (RuntimeException ex) {
            drainToolInvocations();
            invocationRecorder.failure(handle, ex);
            throw ex;
        }
    }

    /**
     * 打印本轮工具调用日志
     */
    private List<AiToolInvocationStore.Invocation> drainToolInvocations() {
        if (toolInvocationStore == null) {
            return List.of();
        }
        List<AiToolInvocationStore.Invocation> invocations = toolInvocationStore.drain();
        if (!invocations.isEmpty()) {
            for (AiToolInvocationStore.Invocation inv : invocations) {
                log.info("工具调用: name={}, request={}, response={}",
                        inv.toolName(),
                        truncate(inv.toolRequest(), 200),
                        truncate(inv.toolResponse(), 200));
            }
        }
        return invocations;
    }

    private String requestPayload(String requestText, String systemPrompt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("thinkingType", thinkingType);
        payload.put("maxTokens", maxTokens);
        payload.put("timeout", timeout.toString());
        payload.put("system", systemPrompt);
        payload.put("user", requestText);
        payload.put("tools", Arrays.stream(toolRegistry.getAllTools())
                .map(tool -> tool.getClass().getName()).toList());
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

    private String truncate(String text, int maxLen) {
        if (text == null) return "null";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }
}
