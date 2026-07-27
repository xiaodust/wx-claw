package com.dust.wxclawbackfront.observability.llm.entity;

import com.dust.wxclawbackfront.tenancy.TenantOwnedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "llm_invocation", indexes = {
        @Index(name = "idx_llm_invocation_tenant_conversation", columnList = "tenant_id,conversation_id,started_at"),
        @Index(name = "idx_llm_invocation_tenant_trace", columnList = "tenant_id,trace_id,sequence_no"),
        @Index(name = "idx_llm_invocation_tenant_bot", columnList = "tenant_id,bot_id,started_at"),
        @Index(name = "idx_llm_invocation_tenant_status", columnList = "tenant_id,status,started_at")
})
public class LlmInvocation extends TenantOwnedEntity {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "bot_id", length = 128)
    private String botId;

    @Column(name = "conversation_id", length = 36)
    private String conversationId;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "parent_invocation_id", length = 36)
    private String parentInvocationId;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "invocation_type", nullable = false, length = 40)
    private String invocationType;

    @Column(length = 64)
    private String provider;

    @Column(length = 128)
    private String model;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "request_payload", columnDefinition = "TEXT")
    private String requestPayload;

    @Column(name = "response_payload", columnDefinition = "TEXT")
    private String responsePayload;

    @Column(name = "tool_calls_json", columnDefinition = "TEXT")
    private String toolCallsJson;

    @Column(name = "request_truncated", nullable = false)
    private Boolean requestTruncated = false;

    @Column(name = "response_truncated", nullable = false)
    private Boolean responseTruncated = false;

    @Column(name = "request_original_length")
    private Integer requestOriginalLength;

    @Column(name = "response_original_length")
    private Integer responseOriginalLength;

    @Column(name = "request_sha256", length = 64)
    private String requestSha256;

    @Column(name = "response_sha256", length = 64)
    private String responseSha256;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "error_type", length = 255)
    private String errorType;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
