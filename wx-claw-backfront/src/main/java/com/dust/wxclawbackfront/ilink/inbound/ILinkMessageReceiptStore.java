package com.dust.wxclawbackfront.ilink.inbound;

import com.dust.wxclawbackfront.ilink.runtime.BotRuntimeKey;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ILinkMessageReceiptStore {

    private static final int DELETE_BATCH_SIZE = 1000;

    private final JdbcTemplate jdbcTemplate;

    /**
     * 进程内正在处理的消息（tenant+bot+messageId -> 开始处理时间），
     * 用于区分"同进程仍在处理中的重复投递"与"崩溃重启后的重新投递"。
     */
    private final ConcurrentMap<String, Instant> activeSince = new ConcurrentHashMap<>();

    @Value("${wxclaw.cleanup.receipts.enabled:true}")
    private boolean cleanupEnabled;

    @Value("${wxclaw.cleanup.receipts.retention-days:30}")
    private int retentionDays;

    /**
     * 进程内活跃处理租约：超过该时长仍停留在活跃集合中的条目视为"worker 已挂起"，
     * 允许重新认领。崩溃重启后活跃集合为空，非 DONE 回执会立即被重新认领，不丢消息。
     */
    @Value("${wxclaw.ilink.receipt.reclaim-lease-seconds:240}")
    private long reclaimLeaseSeconds;

    public boolean claim(BotRuntimeKey runtimeKey, WeixinMessage message) {
        if (message.getMessage_id() == null) {
            return true;
        }
        int inserted = jdbcTemplate.update("""
                        INSERT IGNORE INTO ilink_message_receipt
                            (tenant_id, bot_id, message_id, from_user_id, create_time_ms, received_at, status)
                        VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, 'RECEIVED')
                        """,
                runtimeKey.tenantId(), runtimeKey.botId(), message.getMessage_id(),
                message.getFrom_user_id(), message.getCreate_time_ms());
        if (inserted == 1) {
            activeSince.put(activeKey(runtimeKey, message), Instant.now());
            return true;
        }
        return reclaimIfStale(runtimeKey, message);
    }

    /**
     * 已存在回执时：进程内仍在处理（活跃集合内且未超租约）或 DONE 的直接跳过；
     * 其余 RECEIVED/PROCESSING 视为崩溃残留或挂起任务，重新置为 RECEIVED 并允许再次处理。
     */
    private boolean reclaimIfStale(BotRuntimeKey runtimeKey, WeixinMessage message) {
        Instant now = Instant.now();
        String activeKey = activeKey(runtimeKey, message);
        Instant started = activeSince.get(activeKey);
        if (started != null && started.isAfter(now.minusSeconds(Math.max(1, reclaimLeaseSeconds)))) {
            return false;
        }
        String status;
        try {
            status = jdbcTemplate.queryForObject("""
                            SELECT status FROM ilink_message_receipt
                            WHERE tenant_id = ? AND bot_id = ? AND message_id = ?
                            """, String.class,
                    runtimeKey.tenantId(), runtimeKey.botId(), message.getMessage_id());
        } catch (EmptyResultDataAccessException ex) {
            // 回执记录已不存在（如恰被清理），按新消息处理
            return true;
        }
        if ("DONE".equals(status)) {
            return false;
        }
        int updated = jdbcTemplate.update("""
                        UPDATE ilink_message_receipt
                        SET status = 'RECEIVED', received_at = CURRENT_TIMESTAMP
                        WHERE tenant_id = ? AND bot_id = ? AND message_id = ?
                          AND status <> 'DONE'
                        """,
                runtimeKey.tenantId(), runtimeKey.botId(), message.getMessage_id());
        if (updated == 1) {
            activeSince.put(activeKey, now);
            return true;
        }
        return false;
    }

    /**
     * 开始处理前置为 PROCESSING，防止同一进程内的并发重复消费。
     */
    public void markProcessing(BotRuntimeKey runtimeKey, WeixinMessage message) {
        if (message.getMessage_id() == null) {
            return;
        }
        activeSince.put(activeKey(runtimeKey, message), Instant.now());
        jdbcTemplate.update("""
                        UPDATE ilink_message_receipt SET status = 'PROCESSING'
                        WHERE tenant_id = ? AND bot_id = ? AND message_id = ? AND status <> 'DONE'
                        """,
                runtimeKey.tenantId(), runtimeKey.botId(), message.getMessage_id());
    }

    /**
     * 处理完成（成功或已向用户反馈失败的终态）后置为 DONE，这才是真正的 ack。
     */
    public void markDone(BotRuntimeKey runtimeKey, WeixinMessage message) {
        if (message.getMessage_id() == null) {
            return;
        }
        activeSince.remove(activeKey(runtimeKey, message));
        jdbcTemplate.update("""
                        UPDATE ilink_message_receipt
                        SET status = 'DONE', processed_at = CURRENT_TIMESTAMP
                        WHERE tenant_id = ? AND bot_id = ? AND message_id = ? AND status <> 'DONE'
                        """,
                runtimeKey.tenantId(), runtimeKey.botId(), message.getMessage_id());
    }

    private String activeKey(BotRuntimeKey runtimeKey, WeixinMessage message) {
        return runtimeKey.tenantId() + "::" + runtimeKey.botId() + "::" + message.getMessage_id();
    }

    /**
     * 分批删除超过保留期且已完成的回执（DONE），防止去重表无限增长。
     * 未完成的 RECEIVED/PROCESSING 记录保留，作为崩溃恢复依据。
     */
    @Scheduled(cron = "${wxclaw.cleanup.receipts.cron:0 15 3 * * ?}")
    public void cleanupExpired() {
        if (!cleanupEnabled) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, retentionDays));
        long deleted = deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("已清理 {} 条已完成的 iLink 消息回执（received_at 早于 {}）", deleted, cutoff);
        }
    }

    /**
     * 分批删除 {@code status = 'DONE'} 且 {@code received_at} 早于 {@code cutoff} 的回执记录。
     */
    public long deleteOlderThan(LocalDateTime cutoff) {
        int total = 0;
        int batch;
        do {
            batch = jdbcTemplate.update(
                    "DELETE FROM ilink_message_receipt WHERE status = 'DONE' AND received_at < ? LIMIT "
                            + DELETE_BATCH_SIZE,
                    ps -> ps.setObject(1, cutoff));
            total += batch;
        } while (batch >= DELETE_BATCH_SIZE);
        return total;
    }
}
