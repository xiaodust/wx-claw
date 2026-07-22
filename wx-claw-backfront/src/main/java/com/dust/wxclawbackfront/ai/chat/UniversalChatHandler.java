package com.dust.wxclawbackfront.ai.chat;

import com.dust.wxclawbackfront.ai.dao.entity.AiMessage;
import com.dust.wxclawbackfront.ai.tools.memory.UserMemoryService;
import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;
import com.dust.wxclawbackfront.ai.tools.shared.UserContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
                () -> skillLoader.getSkillSystemPrompt(), PROMPT_EXECUTOR);
        CompletableFuture<String> memoryFuture = CompletableFuture.supplyAsync(
                () -> userMemoryService.buildMemoryPrompt(userId), PROMPT_EXECUTOR);

        String skillPrompt = skillFuture.join();
        String memoryPrompt = memoryFuture.join();

        // 4. 构建 system prompt
        StringBuilder systemPromptBuilder = new StringBuilder();
        // 微信聊天场景：要求纯文本回复
        systemPromptBuilder.append("【回复格式要求】你是在微信聊天中回复用户，请使用纯文本格式，不要使用任何 Markdown 语法（不要用 # 标题、** 粗体、* 列表、` 代码标记、> 引用、--- 分隔线、表格等）。直接用自然语言回复，需要分点说明时用数字编号（1. 2. 3.），需要强调时用【】标注。\n\n");
        if (skillPrompt != null && !skillPrompt.isBlank()) {
            systemPromptBuilder.append(skillPrompt);
        }
        if (memoryPrompt != null && !memoryPrompt.isBlank()) {
            systemPromptBuilder.append(memoryPrompt);
        }
        String systemPrompt = systemPromptBuilder.toString();

        // 5. 使用 Spring AI 原生 function calling（模型自主决定调用工具）
        String content = callWithTools(requestText, systemPrompt);

        // 6. 打印工具调用日志
        logToolInvocations();

        return content;
    }

    /**
     * 使用 Spring AI 原生 function calling 调用 LLM
     */
    private String callWithTools(String requestText, String systemPrompt) {
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

        spec = spec.options(optionsBuilder);
        spec = spec.tools(toolRegistry.getAllTools());

        ChatClient.ChatClientRequestSpec finalSpec;
        if (!systemPrompt.isBlank()) {
            finalSpec = spec.system(systemPrompt).user(requestText);
        } else {
            finalSpec = spec.user(requestText);
        }

        long start = System.currentTimeMillis();
        String content = finalSpec.call().content();
        long elapsed = System.currentTimeMillis() - start;
        log.info("LLM响应完成, 耗时={}ms, model={}", elapsed, model);
        return content;
    }

    /**
     * 打印本轮工具调用日志
     */
    private void logToolInvocations() {
        if (toolInvocationStore == null) {
            return;
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
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "null";
        return text.length() <= maxLen ? text : text.substring(0, maxLen) + "...";
    }

    private static final ExecutorService PROMPT_EXECUTOR = Executors.newFixedThreadPool(2);
}
