package com.dust.wxclawbackfront.bot.dao.repository;

import com.dust.wxclawbackfront.bot.dao.entity.AiConversationSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AiConversationSummaryRepository extends JpaRepository<AiConversationSummary, String> {

    Optional<AiConversationSummary> findByTenantIdAndConversationId(String tenantId, String conversationId);
}
