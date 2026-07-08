package com.dust.wxclawbackfront.ai.dao.repository;

import com.dust.wxclawbackfront.ai.dao.entity.AiMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiMessageRepository extends JpaRepository<AiMessage, String> {

    List<AiMessage> findAllBySessionIdOrderByMessageSeqAsc(String sessionId);

    Optional<AiMessage> findTopBySessionIdOrderByMessageSeqDesc(String sessionId);

    long countBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);
}
