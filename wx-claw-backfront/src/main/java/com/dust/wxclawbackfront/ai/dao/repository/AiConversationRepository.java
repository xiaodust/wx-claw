package com.dust.wxclawbackfront.ai.dao.repository;

import com.dust.wxclawbackfront.ai.dao.entity.AiConversation;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiConversationRepository extends JpaRepository<AiConversation, String> {

    Optional<AiConversation> findBySessionId(String sessionId);

    List<AiConversation> findAllByUsername(String username, Sort sort);
}
