package com.dust.wxclawbackfront.bot.dao.entity;

import com.dust.wxclawbackfront.tenancy.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.EqualsAndHashCode;

import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
        name = "ai_message",
        indexes = {
                @Index(name = "idx_ai_message_tenant_session", columnList = "tenant_id,session_id"),
                @Index(name = "idx_ai_message_tenant_conversation", columnList = "tenant_id,conversation_id,message_seq")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_session_message_seq",
                columnNames = {"tenant_id", "conversation_id", "message_seq"}
        )
)
public class AiMessage extends TenantOwnedEntity {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "conversation_id", nullable = false, length = 36)
    private String conversationId;

    @Column(name = "message_type", nullable = false)
    private Integer messageType;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "reasoning_content", columnDefinition = "TEXT")
    private String reasoningContent;

    @Column(name = "message_seq", nullable = false)
    private Integer messageSeq;

    @Column(name = "response_time")
    private Integer responseTime;

    @Column(name = "error_msg", length = 1024)
    private String errorMsg;

    @Column(name = "create_time", updatable = false)
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        if (updateTime == null) {
            updateTime = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

}
