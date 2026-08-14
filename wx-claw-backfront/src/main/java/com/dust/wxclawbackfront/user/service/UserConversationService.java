package com.dust.wxclawbackfront.user.service;

import com.dust.wxclawbackfront.bot.dao.entity.AiConversation;
import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import com.dust.wxclawbackfront.bot.dao.repository.AiConversationRepository;
import com.dust.wxclawbackfront.bot.dao.repository.AiMessageRepository;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.entity.TenantBot;
import com.dust.wxclawbackfront.tenancy.repository.TenantBotRepository;
import com.dust.wxclawbackfront.user.api.dto.UserDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户自助会话/聊天记录查询：仅返回当前租户下指定 bot 的数据。
 */
@Service
@RequiredArgsConstructor
public class UserConversationService {

    private static final String CHANNEL_ILINK = "ILINK";

    private final TenantBotRepository botRepository;
    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;

    public List<UserDtos.Conversation> conversations(String botId, int limit) {
        requireBot(botId);
        String tenantId = TenantContextHolder.require().tenantId();
        int size = Math.min(50, Math.max(1, limit));
        return conversationRepository
                .findByTenantIdAndChannelAndBotId(tenantId, CHANNEL_ILINK, botId,
                        PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "updatedTime")))
                .stream()
                .map(this::toConversation)
                .toList();
    }

    public List<UserDtos.Message> messages(String botId, String conversationId) {
        requireBot(botId);
        String tenantId = TenantContextHolder.require().tenantId();
        AiConversation conversation = conversationRepository.findById(conversationId)
                .filter(c -> tenantId.equals(c.getTenantId()) && botId.equals(c.getBotId()))
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));
        return messageRepository.findAllByTenantIdAndConversationIdOrderByMessageSeqAsc(
                        tenantId, conversation.getId())
                .stream()
                .map(this::toMessage)
                .toList();
    }

    private void requireBot(String botId) {
        String tenantId = TenantContextHolder.require().tenantId();
        if (botRepository.findByTenantIdAndBotId(tenantId, botId).isEmpty()) {
            throw new IllegalArgumentException("Bot not found: " + botId);
        }
    }

    private UserDtos.Conversation toConversation(AiConversation c) {
        return new UserDtos.Conversation(
                c.getId(), c.getSessionId(), c.getBotId(),
                Boolean.TRUE.equals(c.getActive()),
                c.getMessageCount() == null ? 0 : c.getMessageCount(),
                c.getLastMessageTime(), c.getCreatedTime(), c.getUpdatedTime());
    }

    private UserDtos.Message toMessage(AiMessage m) {
        return new UserDtos.Message(
                m.getId(),
                m.getMessageType() == null ? 0 : m.getMessageType(),
                m.getContent(),
                m.getReasoningContent(),
                m.getMessageSeq() == null ? 0 : m.getMessageSeq(),
                m.getResponseTime(),
                m.getErrorMsg(),
                m.getCreateTime());
    }
}
