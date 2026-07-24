package com.dust.wxclawbackfront.ilink.runtime;

public record BotRuntimeKey(String tenantId, String botId) {
    public BotRuntimeKey {
        if (tenantId == null || tenantId.isBlank() || botId == null || botId.isBlank()) {
            throw new IllegalArgumentException("tenantId and botId are required");
        }
    }
}
