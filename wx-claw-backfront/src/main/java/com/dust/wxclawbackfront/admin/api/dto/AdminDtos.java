package com.dust.wxclawbackfront.admin.api.dto;

import java.time.Instant;
import java.time.LocalDateTime;

public final class AdminDtos {
    private AdminDtos() {
    }

    public record Overview(long botCount, long onlineBotCount, long waitingQrBotCount,
                           long errorBotCount, long todayConversationCount, long todayMessageCount,
                           long todayInvocationCount, long todayFailedInvocationCount,
                           Instant generatedAt) {
    }

    public record BotStatus(String tenantId, String botId, String displayName, String channel,
                            String configuredStatus, String runtimeStatus, Instant connectedAt,
                            Instant statusChangedAt, Instant lastPollAt, Instant lastMessageAt,
                            Instant lastErrorAt, String lastError, int reconnectAttempts,
                            boolean resumeContextAvailable) {
    }

    public record Conversation(String id, String tenantId, String botId, String sessionId,
                               String username, String internalUserId, String channel, boolean active,
                               int messageCount, LocalDateTime lastMessageTime,
                               LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public record Message(String id, String tenantId, String conversationId, String sessionId,
                          int messageType, String content, String reasoningContent, int messageSeq,
                          Integer responseTime, String errorMsg, LocalDateTime createTime,
                          LocalDateTime updateTime) {
    }

    public record InvocationSummary(String id, String tenantId, String botId, String conversationId,
                                    String sessionId, String traceId, int sequenceNo,
                                    String invocationType, String provider, String model, String status,
                                    Integer inputTokens, Integer outputTokens, Integer durationMs,
                                    String errorMessage, LocalDateTime startedAt, LocalDateTime completedAt) {
    }

    public record InvocationDetail(String id, String tenantId, String botId, String conversationId,
                                   String sessionId, String traceId, String parentInvocationId,
                                   int sequenceNo, String invocationType, String provider, String model,
                                   String status, String requestPayload, String responsePayload,
                                   String toolCallsJson, boolean requestTruncated,
                                   boolean responseTruncated, Integer requestOriginalLength,
                                   Integer responseOriginalLength, String requestSha256,
                                   String responseSha256, Integer inputTokens, Integer outputTokens,
                                   Integer durationMs, String errorType, String errorMessage,
                                   LocalDateTime startedAt, LocalDateTime completedAt) {
    }
}
