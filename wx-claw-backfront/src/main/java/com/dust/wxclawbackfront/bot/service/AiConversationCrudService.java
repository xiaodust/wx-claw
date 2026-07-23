package com.dust.wxclawbackfront.bot.service;

import com.dust.wxclawbackfront.bot.dao.entity.AiConversation;
import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import com.dust.wxclawbackfront.bot.dao.repository.AiConversationRepository;
import com.dust.wxclawbackfront.bot.dao.repository.AiMessageRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class AiConversationCrudService {

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;

    public AiConversationCrudService(AiConversationRepository conversationRepository, AiMessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }

    @Transactional
    public AiConversation createOrGetConversation(String sessionId, String username) {
        Optional<AiConversation> existing = conversationRepository.findBySessionId(sessionId);
        if (existing.isPresent()) {
            AiConversation conversation = existing.get();
            boolean changed = false;
            if (username != null && !username.isBlank() && (conversation.getUsername() == null || conversation.getUsername().isBlank())) {
                conversation.setUsername(username.trim());
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
        conversation.setUsername(username == null ? null : username.trim());
        conversation.setActive(Boolean.TRUE);
        conversation.setMessageCount(0);
        return conversationRepository.save(conversation);
    }

    @Transactional
    public AiConversation createNewConversation(String username) {
        String normalizedUsername = username == null ? null : username.trim();
        if (normalizedUsername != null && !normalizedUsername.isBlank()) {
            List<AiConversation> conversations = conversationRepository.findAllByUsername(
                    normalizedUsername,
                    Sort.by(Sort.Direction.DESC, "updatedTime")
            );
            for (AiConversation conversation : conversations) {
                if (Boolean.TRUE.equals(conversation.getActive())) {
                    conversation.setActive(Boolean.FALSE);
                    conversationRepository.save(conversation);
                }
            }
        }

        String sessionId = buildSessionId(normalizedUsername);
        AiConversation conversation = new AiConversation();
        conversation.setSessionId(sessionId);
        conversation.setUsername(normalizedUsername);
        conversation.setActive(Boolean.TRUE);
        conversation.setMessageCount(0);
        return conversationRepository.save(conversation);
    }

    @Transactional(readOnly = true)
    public AiConversation getActiveConversation(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return conversationRepository.findFirstByUsernameAndActiveTrueOrderByUpdatedTimeDesc(username.trim()).orElse(null);
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
        AiConversation conversation = createOrGetConversation(sessionId, null);

        int maxRetries = 3;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                int nextSeq = messageRepository.findTopBySessionIdOrderByMessageSeqDesc(sessionId)
                        .map(AiMessage::getMessageSeq)
                        .filter(Objects::nonNull)
                        .map(seq -> seq + 1)
                        .orElse(1);

                AiMessage message = new AiMessage();
                message.setSessionId(sessionId);
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

                return saved;
            } catch (DataIntegrityViolationException e) {
                if (attempt == maxRetries - 1) {
                    throw e;
                }
                // 唯一约束冲突，重试
            } catch (Exception e) {
                if (attempt == maxRetries - 1 || !isDatabaseLockError(e)) {
                    throw e;
                }
                // SQLITE_BUSY: 数据库锁定，指数退避后重试
                try {
                    Thread.sleep(50L * (1L << attempt)); // 50ms, 100ms, 200ms
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }

        throw new IllegalStateException("Failed to append message after " + maxRetries + " attempts");
    }

    @Transactional(readOnly = true)
    public AiConversation getConversationBySessionId(String sessionId) {
        return conversationRepository.findBySessionId(sessionId).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<AiConversation> listConversations(String username) {
        Sort sort = Sort.by(Sort.Direction.DESC, "updatedTime");
        if (username == null || username.isBlank()) {
            return conversationRepository.findAll(sort);
        }
        return conversationRepository.findAllByUsername(username.trim(), sort);
    }

    @Transactional(readOnly = true)
    public List<AiMessage> listMessages(String sessionId) {
        return messageRepository.findAllBySessionIdOrderByMessageSeqAsc(sessionId);
    }

    /**
     * 查询最近的N条消息（分页，避免内存溢出）
     */
    @Transactional(readOnly = true)
    public List<AiMessage> listRecentMessages(String sessionId, int limit) {
        if (sessionId == null || sessionId.isBlank() || limit <= 0) {
            return Collections.emptyList();
        }
        List<AiMessage> messages = messageRepository.findRecentBySessionId(
                sessionId, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "messageSeq")));
        // 反转为正序
        Collections.reverse(messages);
        return messages;
    }

    @Transactional
    public void deleteConversationBySessionId(String sessionId) {
        messageRepository.deleteBySessionId(sessionId);
        conversationRepository.findBySessionId(sessionId).ifPresent(conversationRepository::delete);
    }

    private String buildSessionId(String username) {
        String prefix = (username == null || username.isBlank()) ? "anonymous" : username.trim();
        return prefix + "::" + UUID.randomUUID();
    }

    private boolean isDatabaseLockError(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            if (cause.getClass().getSimpleName().contains("SQLiteException")
                    || (cause.getMessage() != null && cause.getMessage().contains("database is locked"))) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
