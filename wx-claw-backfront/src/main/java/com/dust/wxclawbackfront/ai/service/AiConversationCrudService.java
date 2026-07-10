package com.dust.wxclawbackfront.ai.service;

import com.dust.wxclawbackfront.ai.dao.entity.AiConversation;
import com.dust.wxclawbackfront.ai.dao.entity.AiMessage;
import com.dust.wxclawbackfront.ai.dao.repository.AiConversationRepository;
import com.dust.wxclawbackfront.ai.dao.repository.AiMessageRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
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
        conversation.setLastMessageTime(new Date());
        if (!Boolean.TRUE.equals(conversation.getActive())) {
            conversation.setActive(Boolean.TRUE);
        }
        conversationRepository.save(conversation);

        return saved;
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

    @Transactional
    public void deleteConversationBySessionId(String sessionId) {
        messageRepository.deleteBySessionId(sessionId);
        conversationRepository.findBySessionId(sessionId).ifPresent(conversationRepository::delete);
    }

    private String buildSessionId(String username) {
        String prefix = (username == null || username.isBlank()) ? "anonymous" : username.trim();
        return prefix + "::" + UUID.randomUUID();
    }
}
