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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationSummaryServiceTest {

    private AiConversationSummaryRepository summaryRepository;
    private AiMessageRepository messageRepository;
    private AiConversationRepository conversationRepository;
    private PlainTextLlmService llmService;
    private MemoryChunkService memoryChunkService;
    private ConversationSummaryService service;

    @BeforeEach
    void setUp() {
        summaryRepository = mock(AiConversationSummaryRepository.class);
        messageRepository = mock(AiMessageRepository.class);
        conversationRepository = mock(AiConversationRepository.class);
        llmService = mock(PlainTextLlmService.class);
        memoryChunkService = mock(MemoryChunkService.class);
        service = new ConversationSummaryService(summaryRepository, messageRepository,
                conversationRepository, llmService, memoryChunkService);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "triggerMessageThreshold", 30);
        ReflectionTestUtils.setField(service, "minIntervalMinutes", 30L);
        ReflectionTestUtils.setField(service, "maxSummaryChars", 1500);
        ReflectionTestUtils.setField(service, "maxMessagesPerRun", 100);
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void generatesIncrementalSummaryWhenThresholdReached() {
        AiConversation conversation = conversation(31);
        AiMessage message = message(5, "用户: 我喜欢杭州");
        when(summaryRepository.findByTenantIdAndConversationId("tenant-a", "c1"))
                .thenReturn(Optional.empty());
        when(messageRepository.findByTenantIdAndConversationIdAndMessageSeqGreaterThanOrderByMessageSeqAsc(
                eq("tenant-a"), eq("c1"), eq(0), any(Pageable.class)))
                .thenReturn(List.of(message));
        when(llmService.chat(anyString(), eq("CONVERSATION_SUMMARY"))).thenReturn("合并后的摘要");

        service.summarizeIfDue(conversation);

        verify(summaryRepository).save(argThat(saved ->
                saved.getConversationId().equals("c1")
                        && saved.getSummaryText().equals("合并后的摘要")
                        && saved.getLastSummarizedSeq() == 5
                        && saved.getSummaryVersion() == 1));
        verify(memoryChunkService).indexMessages(any(), any(), eq("合并后的摘要"));
        assertThat(conversation.getMessageCount()).isEqualTo(31);
    }

    @Test
    void skipsWhenBelowThreshold() {
        AiConversation conversation = conversation(10);

        service.summarizeIfDue(conversation);

        verify(llmService, never()).chat(anyString(), anyString());
        verify(summaryRepository, never()).save(any());
    }

    @Test
    void skipsWhenWithinMinInterval() {
        AiConversation conversation = conversation(40);
        AiConversationSummary existing = new AiConversationSummary();
        existing.setConversationId("c1");
        existing.setSummaryText("旧摘要");
        existing.setLastSummarizedSeq(10);
        existing.setUpdatedAt(LocalDateTime.now());
        when(summaryRepository.findByTenantIdAndConversationId("tenant-a", "c1"))
                .thenReturn(Optional.of(existing));

        service.summarizeIfDue(conversation);

        verify(llmService, never()).chat(anyString(), anyString());
    }

    @Test
    void mergesWithExistingSummaryAndIncrementsVersion() {
        AiConversation conversation = conversation(40);
        AiConversationSummary existing = new AiConversationSummary();
        existing.setConversationId("c1");
        existing.setSummaryText("旧摘要");
        existing.setLastSummarizedSeq(10);
        existing.setSummaryVersion(2);
        existing.setUpdatedAt(LocalDateTime.now().minusHours(2));
        AiMessage message = message(15, "用户: 新消息");
        when(summaryRepository.findByTenantIdAndConversationId("tenant-a", "c1"))
                .thenReturn(Optional.of(existing));
        when(messageRepository.findByTenantIdAndConversationIdAndMessageSeqGreaterThanOrderByMessageSeqAsc(
                eq("tenant-a"), eq("c1"), eq(10), any(Pageable.class)))
                .thenReturn(List.of(message));
        when(llmService.chat(argThat(prompt -> prompt.contains("旧摘要") && prompt.contains("新消息")),
                eq("CONVERSATION_SUMMARY"))).thenReturn("新摘要");

        service.summarizeIfDue(conversation);

        verify(summaryRepository).save(argThat(saved ->
                saved.getSummaryText().equals("新摘要")
                        && saved.getLastSummarizedSeq() == 15
                        && saved.getSummaryVersion() == 3));
    }

    private AiConversation conversation(int messageCount) {
        AiConversation conversation = new AiConversation();
        conversation.setId("c1");
        conversation.setTenantId("tenant-a");
        conversation.setMessageCount(messageCount);
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
