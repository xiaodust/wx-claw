package com.dust.wxclawbackfront.bot.service;

import com.dust.wxclawbackfront.bot.dao.entity.AiConversation;
import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryChunkServiceTest {

    private JdbcTemplate jdbcTemplate;
    private ObjectProvider<EmbeddingModel> provider;
    private EmbeddingModel embeddingModel;
    private MemoryChunkService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        provider = mock(ObjectProvider.class);
        embeddingModel = mock(EmbeddingModel.class);
        service = new MemoryChunkService(jdbcTemplate, new ObjectMapper(), provider);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "chunkChars", 200);
        ReflectionTestUtils.setField(service, "topK", 5);
        ReflectionTestUtils.setField(service, "ttlDays", 90L);
        when(provider.getIfAvailable()).thenReturn(embeddingModel);
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f, 0.2f});
        TenantContextHolder.set(TenantContext.ilink("tenant-a", "bot-a", "user-a", "req"));
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    @Test
    void indexesMessagesIntoChunks() {
        AiConversation conversation = new AiConversation();
        conversation.setId("c1");
        conversation.setTenantId("tenant-a");
        AiMessage message = new AiMessage();
        message.setMessageSeq(1);
        message.setMessageType(0);
        message.setContent("这是一段需要被向量化的用户消息内容，用于后续语义召回。");
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        service.indexMessages(conversation, List.of(message), "早期对话摘要");

        verify(jdbcTemplate).update(
                argThat(sql -> sql.startsWith("DELETE FROM conversation_memory_chunk")), any(Object[].class));
        verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce()).update(
                argThat(sql -> sql.contains("INSERT INTO conversation_memory_chunk")), any(Object[].class));
    }

    @Test
    void recallsTopKByCosineSimilarity() {
        when(embeddingModel.embed("还记得我们聊过的城市吗")).thenReturn(new float[]{1f, 0f});
        MemoryChunkService.StoredChunk hz = new MemoryChunkService.StoredChunk("杭州", new float[]{1f, 0f});
        MemoryChunkService.StoredChunk bj = new MemoryChunkService.StoredChunk("北京", new float[]{0f, 1f});
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(hz, bj));

        String result = service.recall("user-a", "还记得我们聊过的城市吗", 5);

        assertThat(result).contains("杭州").doesNotContain("北京");
    }

    @Test
    void returnsNullWhenDisabled() {
        ReflectionTestUtils.setField(service, "enabled", false);

        assertThat(service.recall("user-a", "之前聊了什么", 5)).isNull();
        verify(embeddingModel, never()).embed(anyString());
    }

    @Test
    void returnsNullWhenNoEmbeddingModel() {
        when(provider.getIfAvailable()).thenReturn(null);

        assertThat(service.recall("user-a", "之前聊了什么", 5)).isNull();
    }
}
