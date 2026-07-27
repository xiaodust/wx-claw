package com.dust.wxclawbackfront.ilink.runtime.status;

import com.dust.wxclawbackfront.ilink.runtime.BotRuntimeKey;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class BotRuntimeStatusRegistry {
    private final ConcurrentHashMap<BotRuntimeKey, MutableState> states = new ConcurrentHashMap<>();

    public void starting(BotRuntimeKey key, boolean resumeContextAvailable) {
        state(key).updateStatus(BotRuntimeStatus.STARTING, 0, null, resumeContextAvailable);
    }

    public void waitingForQr(BotRuntimeKey key) {
        state(key).updateStatus(BotRuntimeStatus.WAITING_QR, 0, null, false);
    }

    public void loginSucceeded(BotRuntimeKey key, boolean resumeContextAvailable) {
        MutableState state = state(key);
        state.connectedAt = Instant.now();
        state.updateStatus(BotRuntimeStatus.STARTING, state.reconnectAttempts, null, resumeContextAvailable);
    }

    public void pollSucceeded(BotRuntimeKey key, boolean receivedMessage) {
        MutableState state = state(key);
        Instant now = Instant.now();
        state.lastPollAt = now;
        if (receivedMessage) {
            state.lastMessageAt = now;
        }
        state.updateStatus(BotRuntimeStatus.ONLINE, 0, null, state.resumeContextAvailable);
    }

    public void reconnecting(BotRuntimeKey key, int attempts, Throwable error) {
        state(key).updateStatus(BotRuntimeStatus.RECONNECTING, attempts, message(error), true);
    }

    public void error(BotRuntimeKey key, int attempts, Throwable error) {
        state(key).updateStatus(BotRuntimeStatus.ERROR, attempts, message(error),
                state(key).resumeContextAvailable);
    }

    public void offline(BotRuntimeKey key) {
        state(key).updateStatus(BotRuntimeStatus.OFFLINE, 0, null, state(key).resumeContextAvailable);
    }

    public void stopped(BotRuntimeKey key) {
        state(key).updateStatus(BotRuntimeStatus.STOPPED, 0, null, state(key).resumeContextAvailable);
    }

    public Optional<BotRuntimeSnapshot> get(BotRuntimeKey key) {
        MutableState state = states.get(key);
        return state == null ? Optional.empty() : Optional.of(state.snapshot());
    }

    public List<BotRuntimeSnapshot> snapshots() {
        return states.values().stream().map(MutableState::snapshot).toList();
    }

    private MutableState state(BotRuntimeKey key) {
        return states.computeIfAbsent(key, MutableState::new);
    }

    private String message(Throwable error) {
        return error == null ? null : error.getMessage();
    }

    private static final class MutableState {
        private final BotRuntimeKey key;
        private volatile BotRuntimeStatus status = BotRuntimeStatus.OFFLINE;
        private volatile Instant connectedAt;
        private volatile Instant statusChangedAt = Instant.now();
        private volatile Instant lastPollAt;
        private volatile Instant lastMessageAt;
        private volatile Instant lastErrorAt;
        private volatile String lastError;
        private volatile int reconnectAttempts;
        private volatile boolean resumeContextAvailable;

        private MutableState(BotRuntimeKey key) {
            this.key = key;
        }

        private synchronized void updateStatus(BotRuntimeStatus nextStatus, int attempts, String error,
                                               boolean hasResumeContext) {
            if (status != nextStatus) {
                statusChangedAt = Instant.now();
            }
            status = nextStatus;
            reconnectAttempts = attempts;
            resumeContextAvailable = hasResumeContext;
            if (error != null && !error.isBlank()) {
                lastError = error;
                lastErrorAt = Instant.now();
            }
        }

        private synchronized BotRuntimeSnapshot snapshot() {
            return new BotRuntimeSnapshot(key, status, connectedAt, statusChangedAt, lastPollAt,
                    lastMessageAt, lastErrorAt, lastError, reconnectAttempts, resumeContextAvailable);
        }
    }
}
