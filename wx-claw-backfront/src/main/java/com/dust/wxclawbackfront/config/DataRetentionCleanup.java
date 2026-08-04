package com.dust.wxclawbackfront.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 数据库保留期清理。
 *
 * <p>与业务调度（{@code DynamicTaskSchedulerService}）分开，负责对无业务终态的表做
 * 基于时间的保留策略：LLM 调用观测、消息回执、过期 API 凭证，以及可选的旧会话清理。
 * 所有任务均在无租户上下文的调度线程上执行，仅操作时间维度的全局数据。</p>
 */
@Slf4j
@Component
public class DataRetentionCleanup {

    private static final int DELETE_BATCH_SIZE = 1000;

    private final JdbcTemplate jdbcTemplate;

    @Value("${wxclaw.cleanup.llm-invocations.enabled:true}")
    private boolean llmInvocationsEnabled;

    @Value("${wxclaw.cleanup.llm-invocations.retention-days:90}")
    private int llmInvocationsRetentionDays;

    @Value("${wxclaw.cleanup.credentials.enabled:true}")
    private boolean credentialsEnabled;

    @Value("${wxclaw.cleanup.conversations.enabled:false}")
    private boolean conversationsEnabled;

    @Value("${wxclaw.cleanup.conversations.retention-days:180}")
    private int conversationsRetentionDays;

    public DataRetentionCleanup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "${wxclaw.cleanup.llm-invocations.cron:0 15 3 * * ?}")
    public void cleanupLlmInvocations() {
        if (!llmInvocationsEnabled) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, llmInvocationsRetentionDays));
        long total = deleteInBatches("DELETE FROM llm_invocation WHERE started_at < ?", cutoff);
        if (total > 0) {
            log.info("已清理 {} 条 LLM 调用记录（started_at 早于 {}）", total, cutoff);
        }
    }

    @Scheduled(cron = "${wxclaw.cleanup.credentials.cron:0 15 3 * * ?}")
    public void cleanupExpiredCredentials() {
        if (!credentialsEnabled) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        long total = deleteInBatches(
                "DELETE FROM tenant_api_credential WHERE expires_at IS NOT NULL AND expires_at < ?", now);
        if (total > 0) {
            log.info("已清理 {} 条过期 API 凭证（expires_at 早于 {}）", total, now);
        }
    }

    /**
     * 非活跃会话清理（默认关闭，需显式开启）。
     *
     * <p>会删除 {@code updated_time} 早于保留期的会话及其全部消息，属于不可恢复的数据删除，
     * 因此默认不启用。</p>
     */
    @Scheduled(cron = "${wxclaw.cleanup.conversations.cron:0 15 3 * * ?}")
    public void cleanupStaleConversations() {
        if (!conversationsEnabled) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minusDays(Math.max(1, conversationsRetentionDays));
        long deletedMessages = jdbcTemplate.update(
                "DELETE m FROM ai_message m INNER JOIN ai_conversation c ON c.id = m.conversation_id "
                        + "WHERE c.updated_time < ?", cutoff);
        long deletedConversations = deleteInBatches(
                "DELETE FROM ai_conversation WHERE updated_time < ?", cutoff);
        if (deletedMessages > 0 || deletedConversations > 0) {
            log.info("已清理非活跃会话：删除消息 {} 条、会话 {} 个（updated_time 早于 {}）",
                    deletedMessages, deletedConversations, cutoff);
        }
    }

    private long deleteInBatches(String sql, Object... args) {
        int total = 0;
        int batch;
        do {
            batch = jdbcTemplate.update(sql + " LIMIT " + DELETE_BATCH_SIZE, args);
            total += batch;
        } while (batch >= DELETE_BATCH_SIZE);
        return total;
    }
}
