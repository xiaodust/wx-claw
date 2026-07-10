package com.dust.wxclawbackfront.scheduler;

import com.dust.wxclawbackfront.ai.tools.reminder.ReminderHandler;
import com.dust.wxclawbackfront.ai.tools.reminder.ReminderTask;
import com.dust.wxclawbackfront.ai.tools.reminder.ReminderTaskRepository;
import com.dust.wxclawbackfront.ai.tools.reminder.executor.TaskActionExecutor;
import com.dust.wxclawbackfront.ai.tools.reminder.executor.TaskActionExecutorRegistry;
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
 * 定时任务调度器
 * 负责扫描并执行到期任务，根据 actionType 分发到不同执行器
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "wxclaw.reminder", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ReminderScheduler {

    private final ReminderTaskRepository repository;
    private final ReminderHandler reminderHandler;
    private final TaskActionExecutorRegistry executorRegistry;

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
                // 获取动作类型（如果为空，默认为 REMINDER）
                String actionType = task.getActionType();
                if (actionType == null || actionType.isBlank()) {
                    actionType = "REMINDER";
                }
                
                // 获取对应的执行器
                TaskActionExecutor executor = executorRegistry.getExecutor(actionType);
                if (executor == null) {
                    log.error("未找到执行器: taskId={}, actionType={}", task.getId(), actionType);
                    handleFailure(task, "未找到执行器：" + actionType);
                    continue;
                }
                
                // 执行任务
                boolean success = executor.execute(task);
                
                if (success) {
                    // 判断任务类型
                    String taskType = task.getTaskType();
                    if ("ONE_TIME".equals(taskType)) {
                        // 一次性任务，标记为已完成
                        task.setStatus("EXECUTED");
                        task.setExecutedAt(now);
                        repository.save(task);
                        log.info("一次性提醒任务执行成功: reminderId={}, userId={}, text={}", 
                                task.getId(), task.getUserId(), task.getReminderText());
                    } else {
                        // 周期性任务，计算下次触发时间
                        LocalDateTime nextTrigger = calculateNextTriggerTime(task);
                        if (nextTrigger != null) {
                            task.setTriggerTime(nextTrigger);
                            task.setExecutedAt(now);
                            repository.save(task);
                            log.info("周期性提醒任务已重置: reminderId={}, nextTrigger={}, type={}", 
                                    task.getId(), nextTrigger, taskType);
                        } else {
                            // 无法计算下次触发时间，标记为已完成
                            task.setStatus("EXECUTED");
                            task.setExecutedAt(now);
                            repository.save(task);
                            log.warn("无法计算下次触发时间，任务标记为完成: reminderId={}", task.getId());
                        }
                    }
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
     * 计算下次触发时间（委托给 ReminderHandler）
     */
    private LocalDateTime calculateNextTriggerTime(ReminderTask task) {
        return reminderHandler.calculateNextTriggerTime(task);
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
