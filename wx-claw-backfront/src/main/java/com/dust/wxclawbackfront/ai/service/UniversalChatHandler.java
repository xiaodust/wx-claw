package com.dust.wxclawbackfront.ai.service;

import com.dust.wxclawbackfront.ai.dao.entity.AiMessage;
import com.dust.wxclawbackfront.ai.tools.AIContentAccumulator;
import com.dust.wxclawbackfront.ai.tools.TextSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class UniversalChatHandler implements ChatHandler {

    private final ChatClient chatClient;
    private final String model;
    private final String thinkingType;
    private final int maxTokens;
    private final Duration timeout;
    private final int maxContextChars;
    private final int maxMessageChars;
    private final ObjectMapper objectMapper;

    public UniversalChatHandler(ChatClient.Builder chatClientBuilder,
                                ObjectMapper objectMapper,
                                @Value("${spring.ai.openai.chat.model:}") String model,
                                @Value("${wxclaw.ai.thinking.type:disabled}") String thinkingType,
                                @Value("${wxclaw.ai.chat.max-tokens:1024}") int maxTokens,
                                @Value("${wxclaw.ai.chat.timeout:PT35S}") Duration timeout,
                                @Value("${wxclaw.ai.context.max-chars:12000}") int maxContextChars,
                                @Value("${wxclaw.ai.context.max-message-chars:1200}") int maxMessageChars) {
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
        this.model = model;
        this.thinkingType = thinkingType;
        this.maxTokens = maxTokens;
        this.timeout = timeout;
        this.maxContextChars = maxContextChars;
        this.maxMessageChars = maxMessageChars;
    }
    @Override
    public String chat(String userMessage, List<AiMessage> historyMessages, AIContentAccumulator accumulator) {
        String requestText = buildRequestText(userMessage, historyMessages, maxContextChars, maxMessageChars);
        String llmRequestJson = buildTextOnlyRequestJson(model, requestText, thinkingType);
        if (accumulator != null) {
            accumulator.setRequestText(requestText);
            accumulator.setModel(model);
            accumulator.setLlmRequestJson(llmRequestJson);
        }
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
        String content = spec.user(requestText)
                .call()
                .content();

        if (accumulator != null) {
            accumulator.setFinalContent(content);
        }

        return content;
}

    private String buildTextOnlyRequestJson(String model, String text, String thinkingType) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", model);
            if (thinkingType != null && !thinkingType.isBlank()) {
                payload.put("thinking", Map.of("type", thinkingType.trim()));
            }
            Map<String, Object> contentItem = new LinkedHashMap<>();
            contentItem.put("type", "input_text");
            contentItem.put("text", text);
            Map<String, Object> inputItem = new LinkedHashMap<>();
            inputItem.put("role", "user");
            inputItem.put("content", List.of(contentItem));
            payload.put("input", List.of(inputItem));
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String buildRequestText(String userMessage,
                                           List<AiMessage> historyMessages,
                                           int maxContextChars,
                                           int maxMessageChars) {
        String sanitizedUserMessage = TextSanitizer.sanitizeForPrompt(userMessage);
        if (sanitizedUserMessage != null && !sanitizedUserMessage.isBlank()) {
            sanitizedUserMessage = sanitizedUserMessage.trim();
        }

        List<AiMessage> sortedHistory = new ArrayList<>();
        if (historyMessages != null && !historyMessages.isEmpty()) {
            historyMessages.stream()
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(AiMessage::getMessageSeq, Comparator.nullsLast(Integer::compareTo)))
                    .forEach(sortedHistory::add);
        }

        List<String> picked = new ArrayList<>();
        int totalChars = 0;
        int dropped = 0;

        if (!sortedHistory.isEmpty()) {
            for (int i = sortedHistory.size() - 1; i >= 0; i--) {
                String content = TextSanitizer.sanitizeForPrompt(sortedHistory.get(i).getContent());
                if (content == null || content.isBlank()) {
                    continue;
                }
                String trimmed = content.trim();
                if (maxMessageChars > 0 && trimmed.length() > maxMessageChars) {
                    trimmed = trimmed.substring(0, maxMessageChars) + "...";
                }
                int addLen = trimmed.length() + 1;
                if (maxContextChars > 0 && totalChars + addLen > maxContextChars) {
                    dropped = i + 1;
                    break;
                }
                picked.add(trimmed);
                totalChars += addLen;
            }
        }

        String truncationNote = dropped > 0 ? "(历史较长，已省略前 " + dropped + " 条消息)\n" : "";
        int noteLen = truncationNote.isEmpty() ? 0 : truncationNote.length();

        StringBuilder sb = new StringBuilder();
        if (!truncationNote.isEmpty()) {
            if (maxContextChars > 0 && noteLen > maxContextChars) {
                truncationNote = truncationNote.substring(0, maxContextChars);
            }
            sb.append(truncationNote);
        }

        for (int i = picked.size() - 1; i >= 0; i--) {
            String line = picked.get(i);
            if (maxContextChars > 0 && sb.length() + line.length() + 1 > maxContextChars) {
                break;
            }
            sb.append(line).append("\n");
        }

        if (sanitizedUserMessage != null && !sanitizedUserMessage.isBlank()) {
            String trimmed = sanitizedUserMessage;
            if (maxMessageChars > 0 && trimmed.length() > maxMessageChars) {
                trimmed = trimmed.substring(0, maxMessageChars) + "...";
            }
            if (maxContextChars > 0 && sb.length() + trimmed.length() > maxContextChars) {
                int allowed = Math.max(0, maxContextChars - sb.length());
                if (allowed > 0) {
                    sb.append(trimmed, 0, Math.min(allowed, trimmed.length()));
                }
                return sb.toString();
            }
            sb.append(trimmed);
        }

        return sb.toString();
    }
}
