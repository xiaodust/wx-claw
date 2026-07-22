package com.dust.wxclawbackfront.bot.dao.repository;

import com.dust.wxclawbackfront.bot.dao.entity.AiConversation;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiConversationRepository extends JpaRepository<AiConversation, String> {

    Optional<AiConversation> findBySessionId(String sessionId);

    Optional<AiConversation> findFirstByUsernameAndActiveTrueOrderByUpdatedTimeDesc(String username);

    List<AiConversation> findAllByUsername(String username, Sort sort);
}
