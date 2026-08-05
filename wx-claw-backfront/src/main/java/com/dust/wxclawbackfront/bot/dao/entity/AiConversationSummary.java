package com.dust.wxclawbackfront.bot.dao.entity;

import com.dust.wxclawbackfront.tenancy.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

/**
 * 会话摘要：主链路滑动窗口外的早期对话以摘要形式保留，避免超长对话直接丢失。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "ai_conversation_summary")
public class AiConversationSummary extends TenantOwnedEntity {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "conversation_id", nullable = false, unique = true, length = 36)
    private String conversationId;

    @Column(name = "summary_text", nullable = false, columnDefinition = "TEXT")
    private String summaryText;

    @Column(name = "last_summarized_seq", nullable = false)
    private Integer lastSummarizedSeq = 0;

    @Column(name = "summary_version", nullable = false)
    private Integer summaryVersion = 1;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
