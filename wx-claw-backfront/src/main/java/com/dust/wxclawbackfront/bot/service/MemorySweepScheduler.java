package com.dust.wxclawbackfront.bot.service;

import com.dust.wxclawbackfront.bot.dao.entity.AiConversation;
import com.dust.wxclawbackfront.bot.dao.repository.AiConversationRepository;
import com.dust.wxclawbackfront.tenancy.TenantContext;
import com.dust.wxclawbackfront.tenancy.TenantContextHolder;
import com.dust.wxclawbackfront.tenancy.entity.Tenant;
import com.dust.wxclawbackfront.tenancy.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 记忆每日兜底扫描：
 * <ul>
 *   <li>活跃超长会话 → 增量摘要（防止漏触发）；</li>
 *   <li>已关闭会话 → 长期记忆抽取（防止漏抽取）。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemorySweepScheduler {

    private final TenantRepository tenantRepository;
    private final AiConversationRepository conversationRepository;
    private final ConversationSummaryService summaryService;
    private final MemoryExtractionService extractionService;

    @Value("${wxclaw.memory.summary.trigger-message-threshold:30}")
    private int summaryThreshold;

    @Value("${wxclaw.memory.sweep-enabled:true}")
    private boolean sweepEnabled;

    @Scheduled(cron = "${wxclaw.memory.sweep-cron:0 0 5 * * ?}")
    public void sweep() {
        if (!sweepEnabled) {
            return;
        }
        for (Tenant tenant : tenantRepository.findAll()) {
            conversationRepository
                    .findByTenantIdAndActiveTrueAndMessageCountGreaterThanEqual(
                            tenant.getTenantId(), summaryThreshold)
                    .forEach(conversation -> withContext(conversation,
                            () -> safeRun(() -> summaryService.summarizeIfDue(conversation))));
            conversationRepository.findByTenantIdAndActiveFalse(tenant.getTenantId())
                    .forEach(conversation -> withContext(conversation,
                            () -> safeRun(() -> extractionService.extractIfDue(conversation))));
        }
    }

    private void withContext(AiConversation conversation, Runnable task) {
        TenantContext context = new TenantContext(
                conversation.getTenantId(), conversation.getChannel(), conversation.getBotId(),
                conversation.getInternalUserId(), conversation.getInternalUserId(),
                Set.of(), Set.of(), "memory-sweep");
        TenantContextHolder.set(context);
        try {
            task.run();
        } finally {
            TenantContextHolder.clear();
        }
    }

    private void safeRun(Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.warn("记忆兜底任务执行失败: {}", e.getMessage());
        }
    }
}
