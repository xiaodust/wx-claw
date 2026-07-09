package com.dust.wxclawbackfront.ai.tools.reminder;

import com.dust.wxclawbackfront.ilnk.ILinkBotService;
import com.github.wechat.ilink.sdk.ILinkClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 提醒任务调度器
 * 定期扫描到期的提醒任务并发送消息
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wxclaw.reminder", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private final ReminderTaskRepository repository;
    private final ILinkBotService iLinkBotService;

    @Value("${wxclaw.ai.time.zone:Asia/Shanghai}")
    private String timeZone;

    @Value("${wxclaw.reminder.retry-failed:true}")
    private boolean retryFailed;

    @Value("${wxclaw.reminder.max-retry-count:3}")
    private int maxRetryCount;

    /**
     * 每30秒扫描一次到期的提醒任务
     */
    @Scheduled(fixedDelayString = "${wxclaw.reminder.scan-interval-ms:30000}")
    @Transactional
    public void scanAndExecute() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of(timeZone));
        
        // 查询到期的待执行任务
        List<ReminderTask> tasks = repository.findByStatusAndTriggerTimeBefore("PENDING", now);
        
        if (tasks.isEmpty()) {
            return;
        }

        log.info("扫描到 {} 个到期提醒任务", tasks.size());

        for (ReminderTask task : tasks) {
            try {
                sendReminder(task);
                
                // 标记为已执行
                task.setStatus("EXECUTED");
                task.setExecutedAt(now);
                repository.save(task);
                
                log.info("提醒任务执行成功: reminderId={}, userId={}, text={}", 
                        task.getId(), task.getUserId(), task.getReminderText());
                
            } catch (Exception e) {
                log.error("提醒任务执行失败: reminderId={}, userId={}, error={}", 
                        task.getId(), task.getUserId(), e.getMessage(), e);
                
                // 处理失败重试逻辑
                handleFailure(task, e.getMessage());
            }
        }
    }

    /**
     * 发送提醒消息
     */
    private void sendReminder(ReminderTask task) {
        String reminderMessage = "⏰ 提醒：" + task.getReminderText();
        
        ILinkClient client = iLinkBotService.getActiveClient();
        if (client == null) {
            log.warn("ILinkClient 未初始化，无法发送提醒消息: userId={}", task.getUserId());
            throw new RuntimeException("ILinkClient 未初始化");
        }
        
        try {
            // 尝试主动发送消息（context_token 传 null）
            client.sendText(task.getUserId(), reminderMessage);
            log.info("发送提醒消息成功: userId={}, message={}", task.getUserId(), reminderMessage);
            
        } catch (Exception e) {
            log.error("发送提醒消息失败: userId={}, error={}", task.getUserId(), e.getMessage());
            throw new RuntimeException("发送消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理任务执行失败
     */
    private void handleFailure(ReminderTask task, String errorMessage) {
        task.setRetryCount(task.getRetryCount() + 1);
        task.setFailureReason(errorMessage);
        
        if (retryFailed && task.getRetryCount() < maxRetryCount) {
            // 保持 PENDING 状态，下次扫描时重试
            log.info("提醒任务将重试: reminderId={}, retryCount={}", task.getId(), task.getRetryCount());
        } else {
            // 超过重试次数或不重试，标记为失败
            task.setStatus("FAILED");
            task.setExecutedAt(LocalDateTime.now(ZoneId.of(timeZone)));
            log.warn("提醒任务标记为失败: reminderId={}, retryCount={}", task.getId(), task.getRetryCount());
        }
        
        repository.save(task);
    }
}
