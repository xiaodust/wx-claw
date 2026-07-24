package com.dust.wxclawbackfront.bot.dao.entity;

import com.dust.wxclawbackfront.tenancy.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "ai_conversation",
        indexes = {
                @Index(name = "idx_ai_conversation_tenant_session", columnList = "tenant_id,session_id"),
                @Index(name = "idx_ai_conversation_tenant_user_active", columnList = "tenant_id,internal_user_id,is_active"),
                @Index(name = "idx_ai_conversation_tenant_bot", columnList = "tenant_id,bot_id,created_time")
        }
)
public class AiConversation extends TenantOwnedEntity {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "username")
    private String username;

    @Column(name = "internal_user_id", nullable = false, length = 128)
    private String internalUserId;

    @Column(nullable = false, length = 20)
    private String channel;

    @Column(name = "bot_id", length = 128)
    private String botId;

    @Column(name = "is_active", nullable = false)
    private Boolean active = Boolean.TRUE;

    @Column(name = "message_count", nullable = false)
    private Integer messageCount;

    @Column(name = "last_message_time")
    private LocalDateTime lastMessageTime;

    @Column(name = "created_time", updatable = false)
    private LocalDateTime createdTime;

    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdTime == null) {
            createdTime = now;
        }
        if (updatedTime == null) {
            updatedTime = now;
        }
        if (messageCount == null) {
            messageCount = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedTime = LocalDateTime.now();
    }

}
