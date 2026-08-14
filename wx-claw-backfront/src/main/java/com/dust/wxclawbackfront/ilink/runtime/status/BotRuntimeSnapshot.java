package com.dust.wxclawbackfront.ilink.runtime.status;

import com.dust.wxclawbackfront.ilink.runtime.BotRuntimeKey;

import java.time.Instant;

public record BotRuntimeSnapshot(
        BotRuntimeKey key,
        BotRuntimeStatus status,
        Instant connectedAt,
        Instant statusChangedAt,
        Instant lastPollAt,
        Instant lastMessageAt,
        Instant lastErrorAt,
        String lastError,
        int reconnectAttempts,
        boolean resumeContextAvailable,
        String qrContent
) {
}
