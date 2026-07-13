package com.dust.wxclawbackfront.ai.chat;

import com.dust.wxclawbackfront.ai.agent.AgentChatResult;
import com.dust.wxclawbackfront.ai.agent.AgentLlmCaller;
import com.dust.wxclawbackfront.ai.agent.ToolPollingAgent;
import com.dust.wxclawbackfront.ai.dao.entity.AiMessage;
import com.dust.wxclawbackfront.ai.tools.memory.UserMemoryService;
import com.dust.wxclawbackfront.ai.tools.shared.AiToolInvocationStore;
import com.dust.wxclawbackfront.ai.tools.shared.UserContextHolder;
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
 * 只负责流程编排，具体职责委托给各组件
 */
@Service
public class UniversalChatHandler implements ChatHandler {
    private final ChatClient chatClient;
    private final AiToolInvocationStore toolInvocationStore;
    private final ToolPollingAgent toolPollingAgent;
    private final ChatRequestBuilder requestBuilder;
    private final LlmRequestJsonBuilder jsonBuilder;
    private final LlmToolRegistry toolRegistry;
    private final ChatTraceAssembler traceAssembler;
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
                                ToolPollingAgent toolPollingAgent,
                                ChatRequestBuilder requestBuilder,
                                LlmRequestJsonBuilder jsonBuilder,
                                LlmToolRegistry toolRegistry,
                                ChatTraceAssembler traceAssembler,
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
        this.toolPollingAgent = toolPollingAgent;
        this.requestBuilder = requestBuilder;
        this.jsonBuilder = jsonBuilder;
        this.toolRegistry = toolRegistry;
        this.traceAssembler = traceAssembler;
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
    public String chat(String userMessage, List<AiMessage> historyMessages, AIContentAccumulator accumulator) {
        // 1. 构建请求文本
        String requestText = requestBuilder.buildRequestText(userMessage, historyMessages, maxContextChars, maxMessageChars);
        String llmRequestJson = jsonBuilder.buildTextOnlyRequestJson(model, requestText, thinkingType);

        // 2. 设置基础 trace 信息
        traceAssembler.setBasicInfo(accumulator, requestText, model, llmRequestJson);

        // 3. 执行 agent 调用
        AgentChatResult result = toolPollingAgent.run(userMessage, requestText, this::chatOnce);

        // 4. 回填 trace
        traceAssembler.assembleTrace(accumulator, result);

        return result.content();
    }

    private static final ExecutorService PROMPT_EXECUTOR = Executors.newFixedThreadPool(2);

    private AgentLlmCaller.LlmCallResult chatOnce(String requestText) {
        if (toolInvocationStore != null) {
            toolInvocationStore.reset();
        }

        // 并行获取 skill prompt 和 memory prompt
        String userId = UserContextHolder.getUserId();
        CompletableFuture<String> skillFuture = CompletableFuture.supplyAsync(
                () -> skillLoader.getSkillSystemPrompt(), PROMPT_EXECUTOR);
        CompletableFuture<String> memoryFuture = CompletableFuture.supplyAsync(
                () -> userMemoryService.buildMemoryPrompt(userId), PROMPT_EXECUTOR);

        // 等待两者完成
        String skillPrompt = skillFuture.join();
        String memoryPrompt = memoryFuture.join();

        // 构建 system prompt
        StringBuilder systemPromptBuilder = new StringBuilder();
        if (skillPrompt != null && !skillPrompt.isBlank()) {
            systemPromptBuilder.append(skillPrompt);
        }
        if (memoryPrompt != null && !memoryPrompt.isBlank()) {
            systemPromptBuilder.append(memoryPrompt);
        }
        String systemPrompt = systemPromptBuilder.toString();

        // 构建请求
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

        String content = finalSpec.call().content();

        List<AiToolInvocationStore.Invocation> invocations = toolInvocationStore == null ? List.of() : toolInvocationStore.drain();
        return new AgentLlmCaller.LlmCallResult(content, invocations);
    }
}
