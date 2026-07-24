package com.dust.wxclawbackfront.bot.service;

import com.dust.wxclawbackfront.bot.dao.entity.AiConversation;
import com.dust.wxclawbackfront.bot.dao.repository.AiConversationRepository;
import com.dust.wxclawbackfront.bot.dao.repository.AiMessageRepository;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiConversationCrudServiceIsolationTest {
    private AiConversationRepository conversationRepository;
    private AiConversationCrudService service;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(AiConversationRepository.class);
        service = new AiConversationCrudService(conversationRepository, mock(AiMessageRepository.class));
        TenantContextHolder.set(new TenantContext("tenant-a", "REST", null, "user-a", null,
                Set.of(), Set.of("conversation:read"), "request-a"));
    }

    @AfterEach
    void clearContext() {
        TenantContextHolder.clear();
    }

    @Test
    void alwaysQueriesConversationWithCurrentTenant() {
        when(conversationRepository.findByTenantIdAndSessionId("tenant-a", "session-b"))
                .thenReturn(Optional.empty());

        assertNull(service.getConversationBySessionId("session-b"));
        verify(conversationRepository).findByTenantIdAndSessionId("tenant-a", "session-b");
    }

    @Test
    void rejectsDifferentUserEvenIfRepositoryReturnsObject() {
        AiConversation conversation = new AiConversation();
        conversation.setTenantId("tenant-a");
        conversation.setInternalUserId("user-b");
        conversation.setId("conversation-b");
        when(conversationRepository.findByTenantIdAndSessionId("tenant-a", "session-b"))
                .thenReturn(Optional.of(conversation));

        assertThrows(SecurityException.class, () -> service.listMessages("session-b"));
    }
}
