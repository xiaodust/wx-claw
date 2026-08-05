package com.dust.wxclawbackfront.bot.service;

import com.dust.wxclawbackfront.bot.agent.llm.chat.PlainTextLlmService;
import com.dust.wxclawbackfront.bot.agent.tools.memory.UserMemoryService;
import com.dust.wxclawbackfront.bot.dao.entity.AiConversation;
import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import com.dust.wxclawbackfront.bot.dao.repository.AiConversationRepository;
import com.dust.wxclawbackfront.bot.dao.repository.AiMessageRepository;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryExtractionServiceTest {

    private AiMessageRepository messageRepository;
    private AiConversationRepository conversationRepository;
    private UserMemoryService userMemoryService;
    private PlainTextLlmService llmService;
    private MemoryExtractionService service;

    @BeforeEach
    void setUp() {
        messageRepository = mock(AiMessageRepository.class);
        conversationRepository = mock(AiConversationRepository.class);
        userMemoryService = mock(UserMemoryService.class);
        llmService = mock(PlainTextLlmService.class);
        service = new MemoryExtractionService(messageRepository, conversationRepository,
                userMemoryService, llmService, new ObjectMapper());
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "minConfidence", 0.6);
        ReflectionTestUtils.setField(service, "maxMessagesPerRun", 100);
        ReflectionTestUtils.setField(service, "ttlDays", 90L);
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void extractsAndPersistsProfilesAboveConfidence() {
        AiConversation conversation = conversation();
        AiMessage message = message(3, "我喜欢杭州，目标岗位是 Java 后端");
        when(messageRepository.findByTenantIdAndConversationIdAndMessageSeqGreaterThanOrderByMessageSeqAsc(
                eq("tenant-a"), eq("c1"), eq(0), any(Pageable.class)))
                .thenReturn(List.of(message));
        when(llmService.chat(anyString(), eq("MEMORY_EXTRACT"))).thenReturn("""
                {"items":[
                  {"type":"preference","key":"居住城市","value":"杭州","confidence":0.9},
                  {"type":"profile","key":"目标岗位","value":"Java 后端","confidence":0.8}
                ]}
                """);

        service.extractIfDue(conversation);

        verify(userMemoryService).saveProfileWithConfidence(
                eq("user-a"), eq("preference"), eq("居住城市"), eq("杭州"), eq("ai_detected"),
                argThat((BigDecimal confidence) -> confidence.doubleValue() == 0.9), any(LocalDateTime.class));
        verify(userMemoryService).saveProfileWithConfidence(
                eq("user-a"), eq("basic_info"), eq("目标岗位"), eq("Java 后端"), eq("ai_detected"),
                argThat((BigDecimal confidence) -> confidence.doubleValue() == 0.8), any(LocalDateTime.class));
        verify(conversationRepository).save(argThat(saved -> saved.getLastMemoryExtractSeq() == 3));
        assertThat(conversation.getLastMemoryExtractSeq()).isEqualTo(3);
    }

    @Test
    void ignoresItemsBelowMinConfidence() {
        AiConversation conversation = conversation();
        AiMessage message = message(2, "某条低置信度信息");
        when(messageRepository.findByTenantIdAndConversationIdAndMessageSeqGreaterThanOrderByMessageSeqAsc(
                eq("tenant-a"), eq("c1"), eq(0), any(Pageable.class)))
                .thenReturn(List.of(message));
        when(llmService.chat(anyString(), eq("MEMORY_EXTRACT")))
                .thenReturn("{\"items\":[{\"type\":\"profile\",\"key\":\"猜测\",\"value\":\"也许\",\"confidence\":0.4}]}");

        service.extractIfDue(conversation);

        verify(userMemoryService, never()).saveProfileWithConfidence(
                anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
        verify(conversationRepository).save(argThat(saved -> saved.getLastMemoryExtractSeq() == 2));
    }

    @Test
    void handlesInvalidExtractionResponseGracefully() {
        AiConversation conversation = conversation();
        AiMessage message = message(4, "正常对话");
        when(messageRepository.findByTenantIdAndConversationIdAndMessageSeqGreaterThanOrderByMessageSeqAsc(
                eq("tenant-a"), eq("c1"), eq(0), any(Pageable.class)))
                .thenReturn(List.of(message));
        when(llmService.chat(anyString(), eq("MEMORY_EXTRACT"))).thenReturn("不是 JSON");

        service.extractIfDue(conversation);

        verify(userMemoryService, never()).saveProfileWithConfidence(
                anyString(), anyString(), anyString(), anyString(), anyString(), any(), any());
        verify(conversationRepository).save(argThat(saved -> saved.getLastMemoryExtractSeq() == 4));
    }

    private AiConversation conversation() {
        AiConversation conversation = new AiConversation();
        conversation.setId("c1");
        conversation.setTenantId("tenant-a");
        conversation.setLastMemoryExtractSeq(0);
        return conversation;
    }

    private AiMessage message(int seq, String content) {
        AiMessage message = new AiMessage();
        message.setMessageSeq(seq);
        message.setContent(content);
        message.setMessageType(0);
        return message;
    }
}
