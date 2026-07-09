package com.dust.wxclawbackfront.scheduler;

import com.dust.wxclawbackfront.ai.tools.reminder.ReminderNotifier;
import com.dust.wxclawbackfront.ai.tools.reminder.ReminderTask;
import com.dust.wxclawbackfront.ai.tools.reminder.ReminderTaskRepository;
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
 * 只负责定时扫描和状态流转，具体发送委托给 ReminderNotifier
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wxclaw.reminder", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private final ReminderTaskRepository repository;
    private final ReminderNotifier reminderNotifier;

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
        
        List<ReminderTask> tasks = repository.findByStatusAndTriggerTimeBefore("PENDING", now);
        
        if (tasks.isEmpty()) {
            return;
        }

        log.info("扫描到 {} 个到期提醒任务", tasks.size());

        for (ReminderTask task : tasks) {
            try {
                boolean success = reminderNotifier.sendReminder(task);
                
                if (success) {
                    task.setStatus("EXECUTED");
                    task.setExecutedAt(now);
                    repository.save(task);
                    log.info("提醒任务执行成功: reminderId={}, userId={}, text={}", 
                            task.getId(), task.getUserId(), task.getReminderText());
                } else {
                    handleFailure(task, "发送失败");
                }
                
            } catch (Exception e) {
                log.error("提醒任务执行异常: reminderId={}, userId={}, error={}", 
                        task.getId(), task.getUserId(), e.getMessage(), e);
                handleFailure(task, e.getMessage());
            }
        }
    }

    /**
     * 处理任务执行失败
     */
    private void handleFailure(ReminderTask task, String errorMessage) {
        task.setRetryCount(task.getRetryCount() + 1);
        task.setFailureReason(errorMessage);
        
        if (retryFailed && task.getRetryCount() < maxRetryCount) {
            log.info("提醒任务将重试: reminderId={}, retryCount={}", task.getId(), task.getRetryCount());
        } else {
            task.setStatus("FAILED");
            task.setExecutedAt(LocalDateTime.now(ZoneId.of(timeZone)));
            log.warn("提醒任务标记为失败: reminderId={}, retryCount={}", task.getId(), task.getRetryCount());
        }
        
        repository.save(task);
    }
}
