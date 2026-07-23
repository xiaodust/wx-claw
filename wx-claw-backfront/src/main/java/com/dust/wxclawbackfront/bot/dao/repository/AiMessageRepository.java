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

    List<AiMessage> findAllBySessionIdOrderByMessageSeqAsc(String sessionId);

    /**
     * 查询最近的N条消息（按messageSeq降序，然后在内存中反转）
     */
    List<AiMessage> findTop20BySessionIdOrderByMessageSeqDesc(String sessionId);

    /**
     * 使用 Pageable 查询最近的消息
     */
    @Query("SELECT m FROM AiMessage m WHERE m.sessionId = :sessionId ORDER BY m.messageSeq DESC")
    List<AiMessage> findRecentBySessionId(@Param("sessionId") String sessionId, Pageable pageable);

    Optional<AiMessage> findTopBySessionIdOrderByMessageSeqDesc(String sessionId);

    long countBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);

    @Query("SELECT m FROM AiMessage m WHERE m.sessionId IN " +
           "(SELECT c.sessionId FROM AiConversation c WHERE c.username = :username) " +
           "AND m.createTime >= :startTime AND m.createTime < :endTime " +
           "ORDER BY m.createTime ASC")
    List<AiMessage> findByUsernameAndTimeRange(@Param("username") String username,
                                                 @Param("startTime") LocalDateTime startTime,
                                                 @Param("endTime") LocalDateTime endTime);
}
