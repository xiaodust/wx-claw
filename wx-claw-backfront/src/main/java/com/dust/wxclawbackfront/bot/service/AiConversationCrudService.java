package com.dust.wxclawbackfront.bot.service;

import com.dust.wxclawbackfront.bot.dao.entity.AiConversation;
import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import com.dust.wxclawbackfront.bot.dao.repository.AiConversationRepository;
import com.dust.wxclawbackfront.bot.dao.repository.AiMessageRepository;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
public class AiConversationCrudService {

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final ConversationSummaryService conversationSummaryService;
    private final MemoryExtractionService memoryExtractionService;
    private final ExecutorService asyncSaveExecutor;

    public AiConversationCrudService(AiConversationRepository conversationRepository,
                                     AiMessageRepository messageRepository,
                                     ConversationSummaryService conversationSummaryService,
                                     MemoryExtractionService memoryExtractionService,
                                     @Qualifier("asyncSaveExecutor") ExecutorService asyncSaveExecutor) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.conversationSummaryService = conversationSummaryService;
        this.memoryExtractionService = memoryExtractionService;
        this.asyncSaveExecutor = asyncSaveExecutor;
    }

    @Transactional
    public AiConversation createOrGetConversation(String sessionId, String username) {
        TenantContext context = TenantContextHolder.require();
        String internalUserId = resolveSubject(context, username);
        Optional<AiConversation> existing = conversationRepository.findByTenantIdAndSessionId(context.tenantId(), sessionId);
        if (existing.isPresent()) {
            AiConversation conversation = existing.get();
            ensureOwner(context, conversation);
            boolean changed = false;
            if (conversation.getUsername() == null || conversation.getUsername().isBlank()) {
                conversation.setUsername(internalUserId);
                changed = true;
            }
            if (!Boolean.TRUE.equals(conversation.getActive())) {
                conversation.setActive(Boolean.TRUE);
                changed = true;
            }
            return changed ? conversationRepository.save(conversation) : conversation;
        }

        AiConversation conversation = new AiConversation();
        conversation.setSessionId(sessionId);
        conversation.setUsername(internalUserId);
        conversation.setInternalUserId(internalUserId);
        conversation.setChannel(context.channel());
        conversation.setBotId(context.botId());
        conversation.setActive(Boolean.TRUE);
        conversation.setMessageCount(0);
        return conversationRepository.save(conversation);
    }

    @Transactional
    public AiConversation createNewConversation(String username) {
        TenantContext context = TenantContextHolder.require();
        String internalUserId = resolveSubject(context, username);
        if (internalUserId != null && !internalUserId.isBlank()) {
            List<AiConversation> conversations = conversationRepository.findAllByTenantIdAndInternalUserId(
                    context.tenantId(), internalUserId,
                    Sort.by(Sort.Direction.DESC, "updatedTime")
            );
            for (AiConversation conversation : conversations) {
                if (Boolean.TRUE.equals(conversation.getActive())) {
                    conversation.setActive(Boolean.FALSE);
                    conversationRepository.save(conversation);
                    // 会话关闭：异步生成摘要并抽取长期记忆
                    submitAfterCommit(() -> summarizeAndExtract(conversation));
                }
            }
        }

        String sessionId = buildSessionId();
        AiConversation conversation = new AiConversation();
        conversation.setSessionId(sessionId);
        conversation.setUsername(internalUserId);
        conversation.setInternalUserId(internalUserId);
        conversation.setChannel(context.channel());
        conversation.setBotId(context.botId());
        conversation.setActive(Boolean.TRUE);
        conversation.setMessageCount(0);
        return conversationRepository.save(conversation);
    }

    @Transactional(readOnly = true)
    public AiConversation getActiveConversation(String username) {
        TenantContext context = TenantContextHolder.require();
        String internalUserId = resolveSubject(context, username);
        return conversationRepository.findFirstByTenantIdAndInternalUserIdAndChannelAndBotIdAndActiveTrueOrderByUpdatedTimeDesc(
                context.tenantId(), internalUserId, context.channel(), context.botId()).orElse(null);
    }

    @Transactional
    public AiConversation getOrCreateActiveConversation(String username) {
        AiConversation activeConversation = getActiveConversation(username);
        if (activeConversation != null) {
            return activeConversation;
        }
        return createNewConversation(username);
    }

    @Transactional
    public AiMessage appendMessage(String sessionId,
                                   Integer messageType,
                                   String content,
                                   String reasoningContent,
                                   Integer responseTime,
                                   String errorMsg) {
        TenantContext context = TenantContextHolder.require();
        AiConversation conversation = createOrGetConversation(sessionId, null);

        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                int nextSeq = messageRepository.findTopByTenantIdAndConversationIdOrderByMessageSeqDesc(
                                context.tenantId(), conversation.getId())
                        .map(AiMessage::getMessageSeq)
                        .filter(Objects::nonNull)
                        .map(seq -> seq + 1)
                        .orElse(1);

                AiMessage message = new AiMessage();
                message.setSessionId(sessionId);
                message.setConversationId(conversation.getId());
                message.setMessageType(messageType == null ? 0 : messageType);
                message.setContent(content);
                message.setReasoningContent(reasoningContent);
                message.setMessageSeq(nextSeq);
                message.setResponseTime(responseTime);
                message.setErrorMsg(errorMsg);

                AiMessage saved = messageRepository.save(message);

                Integer messageCount = conversation.getMessageCount();
                conversation.setMessageCount((messageCount == null ? 0 : messageCount) + 1);
                conversation.setLastMessageTime(LocalDateTime.now());
                if (!Boolean.TRUE.equals(conversation.getActive())) {
                    conversation.setActive(Boolean.TRUE);
                }
                conversationRepository.save(conversation);

                // 会话消息数达到阈值时异步触发增量摘要（窗口外早期对话不丢）
                submitAfterCommit(() -> safeRun(() -> conversationSummaryService.summarizeIfDue(conversation)));

                return saved;
            } catch (DataIntegrityViolationException e) {
                if (attempt == maxRetries - 1) {
                    throw e;
                }
            }
        }

        throw new IllegalStateException("Failed to append message after " + maxRetries + " attempts");
    }

    private void summarizeAndExtract(AiConversation conversation) {
        safeRun(() -> conversationSummaryService.summarizeIfDue(conversation));
        safeRun(() -> memoryExtractionService.extractIfDue(conversation));
    }

    private void safeRun(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.warn("后台记忆任务执行失败: {}", e.getMessage());
        }
    }

    /**
     * 事务提交后再执行异步任务，避免读到未提交数据；无事务时直接提交。
     */
    private void submitAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncSaveExecutor.execute(task);
                }
            });
        } else {
            asyncSaveExecutor.execute(task);
        }
    }

    @Transactional(readOnly = true)
    public AiConversation getConversationBySessionId(String sessionId) {
        TenantContext context = TenantContextHolder.require();
        return conversationRepository.findByTenantIdAndSessionId(context.tenantId(), sessionId)
                .filter(conversation -> canAccess(context, conversation))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<AiConversation> listConversations(String username) {
        TenantContext context = TenantContextHolder.require();
        String internalUserId = resolveSubject(context, username);
        Sort sort = Sort.by(Sort.Direction.DESC, "updatedTime");
        return conversationRepository.findAllByTenantIdAndInternalUserId(context.tenantId(), internalUserId, sort);
    }

    @Transactional(readOnly = true)
    public List<AiMessage> listMessages(String sessionId) {
        TenantContext context = TenantContextHolder.require();
        AiConversation conversation = requireConversation(context, sessionId);
        return messageRepository.findAllByTenantIdAndConversationIdOrderByMessageSeqAsc(
                context.tenantId(), conversation.getId());
    }

    /**
     * 查询最近的N条消息（分页，避免内存溢出）
     */
    @Transactional(readOnly = true)
    public List<AiMessage> listRecentMessages(String sessionId, int limit) {
        if (sessionId == null || sessionId.isBlank() || limit <= 0) {
            return Collections.emptyList();
        }
        TenantContext context = TenantContextHolder.require();
        AiConversation conversation = requireConversation(context, sessionId);
        List<AiMessage> messages = messageRepository.findRecent(
                context.tenantId(), conversation.getId(),
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "messageSeq")));
        // 反转为正序
        Collections.reverse(messages);
        return messages;
    }

    @Transactional
    public void deleteConversationBySessionId(String sessionId) {
        TenantContext context = TenantContextHolder.require();
        AiConversation conversation = requireConversation(context, sessionId);
        messageRepository.deleteByTenantIdAndConversationId(context.tenantId(), conversation.getId());
        conversationRepository.delete(conversation);
    }

    private String buildSessionId() {
        return UUID.randomUUID().toString();
    }

    private AiConversation requireConversation(TenantContext context, String sessionId) {
        AiConversation conversation = conversationRepository.findByTenantIdAndSessionId(context.tenantId(), sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        ensureOwner(context, conversation);
        return conversation;
    }

    private String resolveSubject(TenantContext context, String requestedUserId) {
        String currentUserId = context.internalUserId();
        if (requestedUserId == null || requestedUserId.isBlank() || requestedUserId.equals(currentUserId)) {
            return currentUserId;
        }
        if (context.scopes().contains("tenant:admin") || context.scopes().contains("*")) {
            return requestedUserId.trim();
        }
        throw new SecurityException("Cannot access another user's conversations");
    }

    private void ensureOwner(TenantContext context, AiConversation conversation) {
        if (!canAccess(context, conversation)) {
            throw new SecurityException("Conversation does not belong to the current principal");
        }
    }

    private boolean canAccess(TenantContext context, AiConversation conversation) {
        return context.tenantId().equals(conversation.getTenantId())
                && (context.internalUserId().equals(conversation.getInternalUserId())
                || context.scopes().contains("tenant:admin") || context.scopes().contains("*"));
    }

}
