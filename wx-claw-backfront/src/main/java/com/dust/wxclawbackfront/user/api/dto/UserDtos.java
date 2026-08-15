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

    public record AiConfigEntry(boolean configured, String apiKeyMasked, String provider, String model) {
    }

    public record AiConfigs(AiConfigEntry chat, AiConfigEntry image, AiConfigEntry video,
                            AiConfigEntry videoDashscope, AiConfigEntry tts, AiConfigEntry search) {
    }

    public record UpdateAiConfigRequest(String apiKey) {
    }

    public record UpdateModelRequest(String model, String provider, String baseUrl) {
    }

    public record ChangePasswordRequest(String oldPassword, String newPassword) {
    }

    public record AccountInfo(String username, String contactEmail, boolean hasAccount) {
    }

    public record SetupAccountRequest(String username, String contactEmail, String emailCode, String password) {
    }

    public record SetupAccountResult(String username, String sessionToken, LocalDateTime sessionExpiresAt) {
    }

    public record MailConfig(String smtpHost, int smtpPort, String username,
                             String fromAddress, boolean enabled, boolean configured) {
    }

    public record SaveMailConfigRequest(String smtpHost, int smtpPort, String username,
                                        String password, String fromAddress) {
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
