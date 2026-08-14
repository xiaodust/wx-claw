package com.dust.wxclawbackfront.ilink.runtime.status;

import com.dust.wxclawbackfront.ilink.runtime.BotRuntimeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class BotRuntimeStatusRegistry {
    private final ConcurrentHashMap<BotRuntimeKey, MutableState> states = new ConcurrentHashMap<>();

    @Value("${wxclaw.ilink.status-cleanup.retention-days:7}")
    private int stoppedRetentionDays;

    public void starting(BotRuntimeKey key, boolean resumeContextAvailable) {
        state(key).updateStatus(BotRuntimeStatus.STARTING, 0, null, resumeContextAvailable);
    }

    public void waitingForQr(BotRuntimeKey key) {
        waitingForQr(key, null);
    }

    public void waitingForQr(BotRuntimeKey key, String qrContent) {
        MutableState state = state(key);
        state.qrContent = qrContent;
        state.updateStatus(BotRuntimeStatus.WAITING_QR, 0, null, false);
    }

    public void loginSucceeded(BotRuntimeKey key, boolean resumeContextAvailable) {
        MutableState state = state(key);
        state.connectedAt = Instant.now();
        state.qrContent = null;
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
        state(key).qrContent = null;
        state(key).updateStatus(BotRuntimeStatus.RECONNECTING, attempts, message(error), true);
    }

    public void error(BotRuntimeKey key, int attempts, Throwable error) {
        state(key).qrContent = null;
        state(key).updateStatus(BotRuntimeStatus.ERROR, attempts, message(error),
                state(key).resumeContextAvailable);
    }

    public void offline(BotRuntimeKey key) {
        state(key).qrContent = null;
        state(key).updateStatus(BotRuntimeStatus.OFFLINE, 0, null, state(key).resumeContextAvailable);
    }

    public void stopped(BotRuntimeKey key) {
        state(key).qrContent = null;
        state(key).updateStatus(BotRuntimeStatus.STOPPED, 0, null, state(key).resumeContextAvailable);
    }

    public Optional<BotRuntimeSnapshot> get(BotRuntimeKey key) {
        MutableState state = states.get(key);
        return state == null ? Optional.empty() : Optional.of(state.snapshot());
    }

    public List<BotRuntimeSnapshot> snapshots() {
        return states.values().stream().map(MutableState::snapshot).toList();
    }

    /**
     * 删除 Bot 时立即移除其运行时状态，避免已删除的 Bot 残留状态。
     */
    public void remove(BotRuntimeKey key) {
        states.remove(key);
    }

    /**
     * 清理停止时间超过保留期的已停止机器人状态，防止配置中已删除的 Bot 永久残留。
     */
    @Scheduled(cron = "${wxclaw.ilink.status-cleanup.cron:0 30 3 * * ?}")
    public void cleanupStoppedStates() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(Math.max(1, stoppedRetentionDays)));
        int before = states.size();
        states.entrySet().removeIf(entry ->
                entry.getValue().status == BotRuntimeStatus.STOPPED
                        && entry.getValue().statusChangedAt.isBefore(cutoff));
        int removed = before - states.size();
        if (removed > 0) {
            log.info("已清理 {} 个超过保留期的已停止 Bot 状态", removed);
        }
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
        private volatile String qrContent;

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
                    lastMessageAt, lastErrorAt, lastError, reconnectAttempts, resumeContextAvailable,
                    qrContent);
        }
    }
}
