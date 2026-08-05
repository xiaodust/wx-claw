package com.dust.wxclawbackfront.bot.service;

import com.dust.wxclawbackfront.bot.agent.llm.chat.PlainTextLlmService;
import com.dust.wxclawbackfront.bot.agent.tools.memory.UserMemoryService;
import com.dust.wxclawbackfront.bot.dao.entity.AiConversation;
import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import com.dust.wxclawbackfront.bot.dao.repository.AiConversationRepository;
import com.dust.wxclawbackfront.bot.dao.repository.AiMessageRepository;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 长期记忆自动抽取：会话关闭/空闲时，把窗口外的对话抽取为结构化用户画像。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryExtractionService {

    private static final String EXTRACTION_PROMPT = """
            你是记忆抽取器。从对话中抽取值得长期记住的用户事实、偏好和决定。
            要求：
            1. 只抽取明确表达的信息，不要猜测或推断；
            2. 每个条目输出 type（profile/preference/habit/decision）、category、key、value、confidence（0.0-1.0）；
            3. 没有可抽取内容时输出 {"items": []}；
            4. 直接输出 JSON，不要任何前缀、解释或代码块标记。

            【对话】
            %s
            """;

    private final AiMessageRepository messageRepository;
    private final AiConversationRepository conversationRepository;
    private final UserMemoryService userMemoryService;
    private final PlainTextLlmService plainTextLlmService;
    private final ObjectMapper objectMapper;

    @Value("${wxclaw.memory.extraction.enabled:true}")
    private boolean enabled;

    @Value("${wxclaw.memory.extraction.min-confidence:0.6}")
    private double minConfidence;

    @Value("${wxclaw.memory.extraction.max-messages-per-run:100}")
    private int maxMessagesPerRun;

    @Value("${wxclaw.memory.extraction.ttl-days:90}")
    private long ttlDays;

    /**
     * 抽取水位线之后的消息并沉淀为用户画像。必须运行在有效租户上下文内。
     */
    public void extractIfDue(AiConversation conversation) {
        if (!enabled || conversation == null) {
            return;
        }
        TenantContext context = TenantContextHolder.require();
        int watermark = conversation.getLastMemoryExtractSeq() == null
                ? 0 : conversation.getLastMemoryExtractSeq();
        List<AiMessage> messages = messageRepository
                .findByTenantIdAndConversationIdAndMessageSeqGreaterThanOrderByMessageSeqAsc(
                        context.tenantId(), conversation.getId(), watermark,
                        PageRequest.of(0, maxMessagesPerRun));
        if (messages.isEmpty()) {
            return;
        }

        String response;
        try {
            response = plainTextLlmService.chat(
                    EXTRACTION_PROMPT.formatted(formatMessages(messages)), "MEMORY_EXTRACT");
        } catch (Exception e) {
            log.warn("长期记忆抽取调用失败: {}", e.getMessage());
            return;
        }

        int persisted = persistItems(context, response);
        conversation.setLastMemoryExtractSeq(messages.get(messages.size() - 1).getMessageSeq());
        conversationRepository.save(conversation);
        if (persisted > 0) {
            log.info("长期记忆抽取完成: conversationId={}, 新增/更新 {} 条", conversation.getId(), persisted);
        }
    }

    private int persistItems(TenantContext context, String response) {
        if (response == null || response.isBlank()) {
            return 0;
        }
        try {
            JsonNode root = objectMapper.readTree(extractJson(response));
            JsonNode items = root.get("items");
            if (items == null || !items.isArray()) {
                return 0;
            }
            int count = 0;
            for (JsonNode item : items) {
                String key = text(item, "key");
                String value = text(item, "value");
                if (key == null || value == null) {
                    continue;
                }
                double confidence = item.has("confidence")
                        ? item.get("confidence").asDouble(0.6) : 0.6;
                if (confidence < minConfidence) {
                    continue;
                }
                String category = switch (text(item, "type")) {
                    case "preference", "habit" -> "preference";
                    case "decision" -> "decision";
                    default -> "basic_info";
                };
                userMemoryService.saveProfileWithConfidence(
                        context.internalUserId(), category, key, value, "ai_detected",
                        BigDecimal.valueOf(confidence), LocalDateTime.now().plusDays(ttlDays));
                log.info("记忆审计: tenant={}, user={}, key={}, value={}, confidence={}, source=ai_detected",
                        context.tenantId(), context.internalUserId(), key, value, confidence);
                count++;
            }
            return count;
        } catch (Exception e) {
            log.warn("记忆抽取结果解析失败: {}", e.getMessage());
            return 0;
        }
    }

    private String formatMessages(List<AiMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (AiMessage message : messages) {
            if (message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            String role = Integer.valueOf(0).equals(message.getMessageType()) ? "用户" : "助手";
            sb.append(role).append(": ").append(message.getContent().trim()).append("\n");
        }
        return sb.toString();
    }

    private String extractJson(String response) {
        int first = response.indexOf("```");
        if (first < 0) {
            return response.trim();
        }
        int start = response.indexOf('\n', first);
        int last = response.lastIndexOf("```");
        if (start < 0 || last <= start) {
            return response.trim();
        }
        return response.substring(start + 1, last).trim();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
