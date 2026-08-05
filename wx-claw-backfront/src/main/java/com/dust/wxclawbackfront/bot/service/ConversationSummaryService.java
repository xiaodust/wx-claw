package com.dust.wxclawbackfront.bot.service;

import com.dust.wxclawbackfront.bot.agent.llm.chat.PlainTextLlmService;
import com.dust.wxclawbackfront.bot.dao.entity.AiConversation;
import com.dust.wxclawbackfront.bot.dao.entity.AiConversationSummary;
import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import com.dust.wxclawbackfront.bot.dao.repository.AiConversationRepository;
import com.dust.wxclawbackfront.bot.dao.repository.AiConversationSummaryRepository;
import com.dust.wxclawbackfront.bot.dao.repository.AiMessageRepository;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 会话摘要服务：主链路滑动窗口外的早期对话以增量摘要形式保留。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSummaryService {

    private final AiConversationSummaryRepository summaryRepository;
    private final AiMessageRepository messageRepository;
    private final AiConversationRepository conversationRepository;
    private final PlainTextLlmService plainTextLlmService;
    private final MemoryChunkService memoryChunkService;

    @Value("${wxclaw.memory.summary.enabled:true}")
    private boolean enabled;

    @Value("${wxclaw.memory.summary.trigger-message-threshold:30}")
    private int triggerMessageThreshold;

    @Value("${wxclaw.memory.summary.min-interval-minutes:30}")
    private long minIntervalMinutes;

    @Value("${wxclaw.memory.summary.max-summary-chars:1500}")
    private int maxSummaryChars;

    @Value("${wxclaw.memory.summary.max-messages-per-run:100}")
    private int maxMessagesPerRun;

    /**
     * 查询会话摘要（用于注入主链路 prompt）。
     */
    public String findSummaryBySessionId(String sessionId) {
        TenantContext context = TenantContextHolder.require();
        return conversationRepository.findByTenantIdAndSessionId(context.tenantId(), sessionId)
                .flatMap(conversation -> summaryRepository.findByTenantIdAndConversationId(
                        context.tenantId(), conversation.getId()))
                .map(AiConversationSummary::getSummaryText)
                .orElse(null);
    }

    /**
     * 按阈值与间隔判断是否需要生成/更新摘要，需要则增量合并。
     * 必须运行在有效租户上下文内（请求线程装饰器或定时任务显式设置）。
     */
    public void summarizeIfDue(AiConversation conversation) {
        if (!enabled || conversation == null) {
            return;
        }
        TenantContext context = TenantContextHolder.require();
        Integer count = conversation.getMessageCount();
        if (count == null || count < triggerMessageThreshold) {
            return;
        }

        Optional<AiConversationSummary> existing = summaryRepository.findByTenantIdAndConversationId(
                context.tenantId(), conversation.getId());
        if (existing.isPresent() && Duration.between(existing.get().getUpdatedAt(), LocalDateTime.now())
                .toMinutes() < minIntervalMinutes) {
            return;
        }

        int fromSeq = existing.map(AiConversationSummary::getLastSummarizedSeq).orElse(0);
        List<AiMessage> newMessages = messageRepository
                .findByTenantIdAndConversationIdAndMessageSeqGreaterThanOrderByMessageSeqAsc(
                        context.tenantId(), conversation.getId(), fromSeq,
                        PageRequest.of(0, maxMessagesPerRun));
        if (newMessages.isEmpty()) {
            return;
        }

        String merged = mergeSummary(existing.map(AiConversationSummary::getSummaryText).orElse(null),
                newMessages);
        if (merged == null || merged.isBlank()) {
            return;
        }
        if (merged.length() > maxSummaryChars) {
            merged = merged.substring(0, maxSummaryChars);
        }

        int version = existing.map(s -> s.getSummaryVersion() + 1).orElse(1);
        AiConversationSummary summary = existing.orElseGet(AiConversationSummary::new);
        summary.setConversationId(conversation.getId());
        summary.setSummaryText(merged);
        summary.setLastSummarizedSeq(newMessages.get(newMessages.size() - 1).getMessageSeq());
        summary.setSummaryVersion(version);
        summaryRepository.save(summary);
        // M3：窗口外消息与摘要切块入库向量记忆（未启用时为空操作）
        memoryChunkService.indexMessages(conversation, newMessages, merged);
        log.info("会话摘要已更新: conversationId={}, version={}, 至 seq={}",
                conversation.getId(), version, summary.getLastSummarizedSeq());
    }

    private String mergeSummary(String existing, List<AiMessage> newMessages) {
        StringBuilder newText = new StringBuilder();
        for (AiMessage message : newMessages) {
            if (message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            String role = Integer.valueOf(0).equals(message.getMessageType()) ? "用户" : "助手";
            newText.append(role).append(": ").append(message.getContent().trim()).append("\n");
        }
        String prompt = """
                你负责维护一段对话的长期摘要。
                要求：
                1. 合并【已有摘要】与【新增对话】，输出更新后的完整摘要；
                2. 保留关键事实、用户偏好、待办事项和重要结论，不要遗漏已有摘要中的信息；
                3. 直接输出摘要正文，不要任何前缀或解释。

                【已有摘要】
                %s

                【新增对话】
                %s
                """.formatted(existing == null || existing.isBlank() ? "（无）" : existing, newText);
        try {
            return plainTextLlmService.chat(prompt, "CONVERSATION_SUMMARY");
        } catch (Exception e) {
            log.warn("会话摘要生成失败: {}", e.getMessage());
            return null;
        }
    }
}
