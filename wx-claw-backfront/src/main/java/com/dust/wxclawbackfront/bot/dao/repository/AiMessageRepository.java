package com.dust.wxclawbackfront.bot.dao.repository;

import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AiMessageRepository extends JpaRepository<AiMessage, String> {

    List<AiMessage> findAllByTenantIdAndConversationIdOrderByMessageSeqAsc(String tenantId, String conversationId);

    /**
     * 查询最近的N条消息（按messageSeq降序，然后在内存中反转）
     */
    List<AiMessage> findTop20ByTenantIdAndConversationIdOrderByMessageSeqDesc(String tenantId, String conversationId);

    /**
     * 使用 Pageable 查询最近的消息
     */
    @Query("SELECT m FROM AiMessage m WHERE m.tenantId = :tenantId AND m.conversationId = :conversationId ORDER BY m.messageSeq DESC")
    List<AiMessage> findRecent(@Param("tenantId") String tenantId,
                               @Param("conversationId") String conversationId,
                               Pageable pageable);

    /**
     * 查询指定会话中序号大于水位线的消息（用于增量摘要与长期记忆抽取）。
     */
    List<AiMessage> findByTenantIdAndConversationIdAndMessageSeqGreaterThanOrderByMessageSeqAsc(
            String tenantId, String conversationId, Integer messageSeq, Pageable pageable);

    Optional<AiMessage> findTopByTenantIdAndConversationIdOrderByMessageSeqDesc(String tenantId, String conversationId);

    long countByTenantIdAndConversationId(String tenantId, String conversationId);

    void deleteByTenantIdAndConversationId(String tenantId, String conversationId);

    long countByTenantIdAndCreateTimeGreaterThanEqual(String tenantId, LocalDateTime createTime);

    long countByCreateTimeGreaterThanEqual(LocalDateTime createTime);

    @Query("SELECT m FROM AiMessage m WHERE m.tenantId = :tenantId AND m.conversationId IN " +
           "(SELECT c.id FROM AiConversation c WHERE c.tenantId = :tenantId AND c.internalUserId = :internalUserId) " +
           "AND m.createTime >= :startTime AND m.createTime < :endTime " +
           "ORDER BY m.createTime ASC")
    List<AiMessage> findByUserAndTimeRange(@Param("tenantId") String tenantId,
                                            @Param("internalUserId") String internalUserId,
                                            @Param("startTime") LocalDateTime startTime,
                                            @Param("endTime") LocalDateTime endTime);
}
