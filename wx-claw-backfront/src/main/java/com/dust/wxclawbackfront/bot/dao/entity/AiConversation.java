package com.dust.wxclawbackfront.bot.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "ai_conversation",
        indexes = {
                @Index(name = "idx_ai_conversation_session_id", columnList = "session_id"),
                @Index(name = "idx_ai_conversation_username", columnList = "username"),
                @Index(name = "idx_ai_conversation_username_active", columnList = "username,is_active")
        }
)
public class AiConversation {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "username")
    private String username;

    @Column(name = "is_active", nullable = false, columnDefinition = "boolean default true")
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
