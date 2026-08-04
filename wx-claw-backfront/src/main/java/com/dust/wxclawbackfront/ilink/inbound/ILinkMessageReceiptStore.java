package com.dust.wxclawbackfront.ilink.inbound;

import com.dust.wxclawbackfront.ilink.runtime.BotRuntimeKey;
import com.github.wechat.ilink.sdk.core.model.WeixinMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ILinkMessageReceiptStore {

    private static final int DELETE_BATCH_SIZE = 1000;

    private final JdbcTemplate jdbcTemplate;

    @Value("${wxclaw.cleanup.receipts.enabled:true}")
    private boolean cleanupEnabled;

    @Value("${wxclaw.cleanup.receipts.retention-days:30}")
    private int retentionDays;

    public boolean claim(BotRuntimeKey runtimeKey, WeixinMessage message) {
        if (message.getMessage_id() == null) {
            return true;
        }
        return jdbcTemplate.update("""
                        INSERT IGNORE INTO ilink_message_receipt
                            (tenant_id, bot_id, message_id, from_user_id, create_time_ms, received_at)
                        VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                runtimeKey.tenantId(), runtimeKey.botId(), message.getMessage_id(),
                message.getFrom_user_id(), message.getCreate_time_ms()) == 1;
    }

    /**
     * 分批删除超过保留期的消息回执，防止去重表无限增长。
     */
    @Scheduled(cron = "${wxclaw.cleanup.receipts.cron:0 15 3 * * ?}")
    public void cleanupExpired() {
        if (!cleanupEnabled) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, retentionDays));
        long deleted = deleteOlderThan(cutoff);
        if (deleted > 0) {
            log.info("已清理 {} 条 iLink 消息回执（received_at 早于 {}）", deleted, cutoff);
        }
    }

    /**
     * 分批删除 {@code received_at} 早于 {@code cutoff} 的回执记录。
     */
    public long deleteOlderThan(LocalDateTime cutoff) {
        int total = 0;
        int batch;
        do {
            batch = jdbcTemplate.update(
                    "DELETE FROM ilink_message_receipt WHERE received_at < ? LIMIT " + DELETE_BATCH_SIZE,
                    ps -> ps.setObject(1, cutoff));
            total += batch;
        } while (batch >= DELETE_BATCH_SIZE);
        return total;
    }
}
