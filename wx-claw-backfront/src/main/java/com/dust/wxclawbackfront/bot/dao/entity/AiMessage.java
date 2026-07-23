package com.dust.wxclawbackfront.bot.dao.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;

import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "ai_message",
        indexes = {
                @Index(name = "idx_ai_message_session_id", columnList = "session_id"),
                @Index(name = "idx_ai_message_session_seq", columnList = "session_id,message_seq")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_session_message_seq",
                columnNames = {"session_id", "message_seq"}
        )
)
public class AiMessage {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

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
