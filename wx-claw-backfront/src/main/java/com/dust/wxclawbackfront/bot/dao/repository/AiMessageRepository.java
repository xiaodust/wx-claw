package com.dust.wxclawbackfront.bot.dao.repository;

import com.dust.wxclawbackfront.bot.dao.entity.AiMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Date;
import java.util.List;
import java.util.Optional;

public interface AiMessageRepository extends JpaRepository<AiMessage, String> {

    List<AiMessage> findAllBySessionIdOrderByMessageSeqAsc(String sessionId);

    Optional<AiMessage> findTopBySessionIdOrderByMessageSeqDesc(String sessionId);

    long countBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);

    @Query("SELECT m FROM AiMessage m WHERE m.sessionId IN " +
           "(SELECT c.sessionId FROM AiConversation c WHERE c.username = :username) " +
           "AND m.createTime >= :startTime AND m.createTime < :endTime " +
           "ORDER BY m.createTime ASC")
    List<AiMessage> findByUsernameAndTimeRange(@Param("username") String username,
                                                 @Param("startTime") Date startTime,
                                                 @Param("endTime") Date endTime);
}
