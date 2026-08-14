package com.dust.wxclawbackfront.user.api.dto;

import java.time.Instant;
import java.time.LocalDateTime;

public final class UserDtos {
    private UserDtos() {
    }

    public record Bot(String tenantId, String botId, String displayName, String configuredStatus,
                      String runtimeStatus, Instant connectedAt, Instant statusChangedAt,
                      Instant lastPollAt, Instant lastMessageAt, String lastError,
                      int reconnectAttempts, boolean qrAvailable) {
    }

    public record CreateBotRequest(String displayName) {
    }

    public record Qr(String botId, String qrImage, String status, Instant statusChangedAt) {
    }

    public record AiConfig(boolean configured, String apiKeyMasked, String baseUrl,
                           LocalDateTime updatedAt) {
    }

    public record UpdateAiConfigRequest(String apiKey) {
    }

    public record Conversation(String id, String sessionId, String botId, boolean active,
                               int messageCount, LocalDateTime lastMessageTime,
                               LocalDateTime createdTime, LocalDateTime updatedTime) {
    }

    public record Message(String id, int messageType, String content, String reasoningContent,
                          int messageSeq, Integer responseTime, String errorMsg,
                          LocalDateTime createTime) {
    }
}
