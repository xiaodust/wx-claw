package com.dust.wxclawbackfront.ai.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.util.Date;

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
    private Date lastMessageTime;

    @CreationTimestamp
    @Column(name = "created_time", updatable = false)
    private Date createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private Date updatedTime;

}
