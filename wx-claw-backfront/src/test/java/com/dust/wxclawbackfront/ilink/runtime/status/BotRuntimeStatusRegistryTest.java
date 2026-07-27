package com.dust.wxclawbackfront.ilink.runtime.status;

import com.dust.wxclawbackfront.ilink.runtime.BotRuntimeKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BotRuntimeStatusRegistryTest {
    @Test
    void tracksBotsIndependentlyAcrossLifecycleEvents() {
        BotRuntimeStatusRegistry registry = new BotRuntimeStatusRegistry();
        BotRuntimeKey first = new BotRuntimeKey("tenant-a", "bot-1");
        BotRuntimeKey second = new BotRuntimeKey("tenant-a", "bot-2");

        registry.waitingForQr(first);
        registry.starting(second, true);
        registry.loginSucceeded(second, true);
        registry.pollSucceeded(second, true);

        assertEquals(BotRuntimeStatus.WAITING_QR, registry.get(first).orElseThrow().status());
        BotRuntimeSnapshot online = registry.get(second).orElseThrow();
        assertEquals(BotRuntimeStatus.ONLINE, online.status());
        assertNotNull(online.lastPollAt());
        assertNotNull(online.lastMessageAt());
    }

    @Test
    void exposesReconnectAttemptsAndError() {
        BotRuntimeStatusRegistry registry = new BotRuntimeStatusRegistry();
        BotRuntimeKey key = new BotRuntimeKey("tenant-a", "bot-1");

        registry.reconnecting(key, 3, new IllegalStateException("session expired"));

        BotRuntimeSnapshot snapshot = registry.get(key).orElseThrow();
        assertEquals(BotRuntimeStatus.RECONNECTING, snapshot.status());
        assertEquals(3, snapshot.reconnectAttempts());
        assertEquals("session expired", snapshot.lastError());
    }
}
