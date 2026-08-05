package com.dust.wxclawbackfront.bot.service;

import com.dust.wxclawbackfront.bot.dao.entity.AiConversation;
import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 向量记忆：把会话摘要与关键消息切块 embedding 入库，按语义召回历史片段。
 *
 * <p>默认关闭（需配置 embedding 模型）；embedding 失败时降级为跳过，不影响主链路。
 * 当前存储为 MySQL + 应用内余弦相似度，数据量大时可迁移 Redis/ES。</p>
 */
@Slf4j
@Service
public class MemoryChunkService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<VolcArkEmbeddingClient> embeddingClientProvider;

    @Value("${wxclaw.memory.vector.enabled:false}")
    private boolean enabled;

    @Value("${wxclaw.memory.vector.chunk-chars:200}")
    private int chunkChars;

    @Value("${wxclaw.memory.vector.top-k:5}")
    private int topK;

    @Value("${wxclaw.memory.vector.ttl-days:90}")
    private long ttlDays;

    public MemoryChunkService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper,
                              ObjectProvider<VolcArkEmbeddingClient> embeddingClientProvider) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.embeddingClientProvider = embeddingClientProvider;
    }

    /**
     * 索引会话窗口外消息与摘要：先清空该会话旧分块，再切块 embedding 入库。
     */
    public void indexMessages(AiConversation conversation, List<AiMessage> messages, String summary) {
        if (!enabled || conversation == null) {
            return;
        }
        VolcArkEmbeddingClient embeddingClient = embeddingClientProvider.getIfAvailable();
        if (embeddingClient == null) {
            log.warn("未配置向量模型，向量记忆跳过（wxclaw.memory.vector.enabled 已开启但无模型）");
            return;
        }
        TenantContext context = TenantContextHolder.require();
        try {
            List<String> chunks = buildChunks(messages, summary);
            if (chunks.isEmpty()) {
                return;
            }
            jdbcTemplate.update("DELETE FROM conversation_memory_chunk WHERE tenant_id = ? AND conversation_id = ?",
                    context.tenantId(), conversation.getId());
            LocalDateTime expiresAt = LocalDateTime.now().plusDays(Math.max(1, ttlDays));
            for (String chunk : chunks) {
                float[] embedding = embeddingClient.embed(chunk);
                jdbcTemplate.update("""
                                INSERT INTO conversation_memory_chunk
                                    (id, tenant_id, user_id, conversation_id, chunk_text, embedding, created_at, expires_at)
                                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?)
                                """,
                        UUID.randomUUID().toString(), context.tenantId(), context.internalUserId(),
                        conversation.getId(), chunk, objectMapper.writeValueAsString(embedding), expiresAt);
            }
            log.info("向量记忆已索引: conversationId={}, chunks={}", conversation.getId(), chunks.size());
        } catch (Exception e) {
            log.warn("向量记忆索引失败: {}", e.getMessage());
        }
    }

    /**
     * 按用户召回与查询最相似的 top-k 历史片段；未启用/无模型/无结果时返回 null。
     */
    public String recall(String userId, String query, int k) {
        if (!enabled || userId == null || query == null || query.isBlank()) {
            return null;
        }
        VolcArkEmbeddingClient embeddingClient = embeddingClientProvider.getIfAvailable();
        if (embeddingClient == null) {
            return null;
        }
        TenantContext context = TenantContextHolder.require();
        try {
            float[] queryVector = embeddingClient.embed(query);
            List<StoredChunk> rows = jdbcTemplate.query("""
                            SELECT chunk_text, embedding FROM conversation_memory_chunk
                            WHERE tenant_id = ? AND user_id = ? AND expires_at > CURRENT_TIMESTAMP
                            """,
                    (rs, rowNum) -> new StoredChunk(rs.getString("chunk_text"),
                            parseEmbedding(rs.getString("embedding"))),
                    context.tenantId(), userId);
            if (rows.isEmpty()) {
                return null;
            }
            return rows.stream()
                    .map(row -> new ScoredChunk(row, cosine(queryVector, row.embedding())))
                    .filter(scored -> scored.score() > 0)
                    .sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
                    .limit(Math.max(1, k))
                    .map(scored -> scored.chunk().text())
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("向量记忆召回失败: {}", e.getMessage());
            return null;
        }
    }

    private List<String> buildChunks(List<AiMessage> messages, String summary) {
        List<String> chunks = new ArrayList<>();
        if (summary != null && !summary.isBlank()) {
            chunks.addAll(splitChunks(summary));
        }
        if (messages != null) {
            for (AiMessage message : messages) {
                if (message.getContent() == null || message.getContent().isBlank()) {
                    continue;
                }
                String role = Integer.valueOf(0).equals(message.getMessageType()) ? "用户" : "助手";
                chunks.addAll(splitChunks(role + ": " + message.getContent().trim()));
            }
        }
        return chunks;
    }

    private List<String> splitChunks(String text) {
        int size = Math.max(64, chunkChars);
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < text.length(); i += size) {
            chunks.add(text.substring(i, Math.min(text.length(), i + size)));
        }
        return chunks;
    }

    private float[] parseEmbedding(String json) {
        if (json == null || json.isBlank()) {
            return new float[0];
        }
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (Exception e) {
            log.warn("向量解析失败，按空向量处理: {}", e.getMessage());
            return new float[0];
        }
    }

    private float cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0;
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return (float) (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    record StoredChunk(String text, float[] embedding) {
    }

    private record ScoredChunk(StoredChunk chunk, float score) {
    }
}
