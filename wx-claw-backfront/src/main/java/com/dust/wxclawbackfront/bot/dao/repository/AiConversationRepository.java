package com.dust.wxclawbackfront.bot.dao.repository;

import com.dust.wxclawbackfront.bot.dao.entity.AiConversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface AiConversationRepository extends JpaRepository<AiConversation, String>, JpaSpecificationExecutor<AiConversation> {

    Optional<AiConversation> findByTenantIdAndSessionId(String tenantId, String sessionId);

    Optional<AiConversation> findFirstByTenantIdAndInternalUserIdAndChannelAndBotIdAndActiveTrueOrderByUpdatedTimeDesc(
            String tenantId, String internalUserId, String channel, String botId);

    List<AiConversation> findAllByTenantIdAndInternalUserId(String tenantId, String internalUserId, Sort sort);

    List<AiConversation> findByTenantIdAndActiveTrueAndMessageCountGreaterThanEqual(
            String tenantId, int messageCount);

    List<AiConversation> findByTenantIdAndActiveFalse(String tenantId);

    long countByTenantIdAndCreatedTimeGreaterThanEqual(String tenantId, java.time.LocalDateTime createdTime);

    long countByCreatedTimeGreaterThanEqual(java.time.LocalDateTime createdTime);

    /** 用户自助 API：按租户+渠道+bot 列出会话（配合 Pageable 排序）。 */
    List<AiConversation> findByTenantIdAndChannelAndBotId(
            String tenantId, String channel, String botId, Pageable pageable);
}
