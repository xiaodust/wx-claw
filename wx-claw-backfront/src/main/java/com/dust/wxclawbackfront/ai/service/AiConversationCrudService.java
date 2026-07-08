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
            if (username != null && !username.isBlank() && (conversation.getUsername() == null || conversation.getUsername().isBlank())) {
                conversation.setUsername(username.trim());
                return conversationRepository.save(conversation);
            }
            return conversation;
        }

        AiConversation conversation = new AiConversation();
        conversation.setSessionId(sessionId);
        conversation.setUsername(username);
        conversation.setMessageCount(0);
        return conversationRepository.save(conversation);
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
}
