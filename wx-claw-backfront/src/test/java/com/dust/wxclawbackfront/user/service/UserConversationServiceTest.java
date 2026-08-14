package com.dust.wxclawbackfront.user.service;

import com.dust.wxclawbackfront.bot.dao.entity.AiConversation;
import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import com.dust.wxclawbackfront.bot.dao.repository.AiConversationRepository;
import com.dust.wxclawbackfront.bot.dao.repository.AiMessageRepository;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.entity.TenantBot;
import com.dust.wxclawbackfront.tenancy.repository.TenantBotRepository;
import com.dust.wxclawbackfront.user.api.dto.UserDtos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserConversationServiceTest {

    private TenantBotRepository botRepository;
    private AiConversationRepository conversationRepository;
    private AiMessageRepository messageRepository;
    private UserConversationService service;

    @BeforeEach
    void setUp() {
        botRepository = mock(TenantBotRepository.class);
        conversationRepository = mock(AiConversationRepository.class);
        messageRepository = mock(AiMessageRepository.class);
        service = new UserConversationService(botRepository, conversationRepository, messageRepository);
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void listsConversationsOfOwnBot() {
        when(botRepository.findByTenantIdAndBotId("tenant-a", "bot-1"))
                .thenReturn(Optional.of(bot("bot-1")));
        AiConversation conversation = new AiConversation();
        conversation.setId("c1");
        conversation.setTenantId("tenant-a");
        conversation.setBotId("bot-1");
        conversation.setSessionId("s1");
        conversation.setActive(true);
        conversation.setMessageCount(3);
        when(conversationRepository.findByTenantIdAndChannelAndBotId(
                eq("tenant-a"), eq("ILINK"), eq("bot-1"), any(Pageable.class)))
                .thenReturn(List.of(conversation));

        List<UserDtos.Conversation> conversations = service.conversations("bot-1", 10);

        assertThat(conversations).hasSize(1);
        assertThat(conversations.getFirst().sessionId()).isEqualTo("s1");
        assertThat(conversations.getFirst().messageCount()).isEqualTo(3);
    }

    @Test
    void rejectsConversationFromAnotherTenantOrBot() {
        when(botRepository.findByTenantIdAndBotId("tenant-a", "bot-1"))
                .thenReturn(Optional.of(bot("bot-1")));
        AiConversation foreign = new AiConversation();
        foreign.setId("c-foreign");
        foreign.setTenantId("tenant-b");
        foreign.setBotId("bot-1");
        when(conversationRepository.findById("c-foreign")).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.messages("bot-1", "c-foreign"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Conversation not found");
        verify(messageRepository, never()).findAllByTenantIdAndConversationIdOrderByMessageSeqAsc(any(), any());
    }

    @Test
    void listsMessagesInSequenceOrder() {
        when(botRepository.findByTenantIdAndBotId("tenant-a", "bot-1"))
                .thenReturn(Optional.of(bot("bot-1")));
        AiConversation conversation = new AiConversation();
        conversation.setId("c1");
        conversation.setTenantId("tenant-a");
        conversation.setBotId("bot-1");
        when(conversationRepository.findById("c1")).thenReturn(Optional.of(conversation));
        AiMessage m1 = message("m1", "c1", 0, "你好", 1);
        AiMessage m2 = message("m2", "c1", 1, "你好，有什么可以帮你", 2);
        when(messageRepository.findAllByTenantIdAndConversationIdOrderByMessageSeqAsc("tenant-a", "c1"))
                .thenReturn(List.of(m1, m2));

        List<UserDtos.Message> messages = service.messages("bot-1", "c1");

        assertThat(messages).hasSize(2);
        assertThat(messages.getFirst().content()).isEqualTo("你好");
        assertThat(messages.get(1).messageType()).isEqualTo(1);
    }

    private TenantBot bot(String botId) {
        TenantBot bot = new TenantBot();
        bot.setTenantId("tenant-a");
        bot.setChannel("ILINK");
        bot.setBotId(botId);
        bot.setStatus("ACTIVE");
        return bot;
    }

    private AiMessage message(String id, String conversationId, int type, String content, int seq) {
        AiMessage message = new AiMessage();
        message.setId(id);
        message.setTenantId("tenant-a");
        message.setConversationId(conversationId);
        message.setMessageType(type);
        message.setContent(content);
        message.setMessageSeq(seq);
        return message;
    }
}
